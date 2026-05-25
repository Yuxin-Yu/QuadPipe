// SPDX-License-Identifier: MIT

package softmax.fp

import spinal.core._

case class FltToFixConvConfig(
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_RESULT_WIDTH: Int = 34,
  C_RESULT_FRACTION_WIDTH: Int = 26,
  C_HAS_ROUNDING: Int = 1,
  REGISTERS: String = "0000_0000_0011_0110"
) {
  val A_W = C_A_WIDTH
  val A_EW = C_A_WIDTH - C_A_FRACTION_WIDTH
  val A_FW = C_A_FRACTION_WIDTH
  val R_W = C_RESULT_WIDTH
  val R_FW = C_RESULT_FRACTION_WIDTH
}

class FltToFixConv(config: FltToFixConvConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(A_W bits)

    val RESULT = out UInt(R_W bits)
    val INVALID_OP = out Bool()
    val OVERFLOW = out Bool()
    val UNDERFLOW = out Bool()
  }

  private val bias = (1 << (A_EW - 1)) - 1
  private val mantWidth = A_FW - 1
  private val shiftWidth = 8

  val sign = io.A(A_W - 1)
  val exp = io.A(A_W - 2 downto A_FW - 1)
  val mant = io.A(A_FW - 2 downto 0)

  val expAllOnes = exp === U((1 << A_EW) - 1, A_EW bits)
  val expAllZero = exp === 0
  val mantAllZero = mant === 0
  val isNaN = expAllOnes && !mantAllZero
  val isInf = expAllOnes && mantAllZero
  val isZero = expAllZero && mantAllZero

  val mantInt = UInt(A_FW bits)
  mantInt := Mux(expAllZero, (U(0, 1 bits) ## mant).asUInt, (U(1, 1 bits) ## mant).asUInt)

  val shiftSigned = SInt((shiftWidth + 1) bits)
  shiftSigned := exp.resize(shiftWidth + 1).asSInt - S(bias, shiftWidth + 1 bits) -
    S(mantWidth, shiftWidth + 1 bits) + S(R_FW, shiftWidth + 1 bits)

  val magExt = mantInt.resize(96)
  val shiftedMag = UInt(96 bits)
  shiftedMag := 0

  val rightShift = UInt(shiftWidth bits)
  rightShift := 0
  val roundBit = Bool()
  roundBit := False
  val stickyBit = Bool()
  stickyBit := False

  when(shiftSigned >= 0) {
    shiftedMag := (magExt |<< shiftSigned.asUInt.resize(shiftWidth)).resized
  } otherwise {
    rightShift := (-shiftSigned).asUInt.resize(shiftWidth)
    shiftedMag := (magExt |>> rightShift).resized
    when(rightShift =/= 0) {
      roundBit := (magExt |>> (rightShift - 1))(0)
    }
    when(rightShift > 1) {
      val stickyMask = (U(1, 96 bits) |<< (rightShift - 1)) - 1
      stickyBit := (magExt & stickyMask) =/= 0
    }
  }

  val roundedMag = UInt(97 bits)
  val roundUp = Bool()
  roundUp := False
  if (C_HAS_ROUNDING == 1) {
    roundUp := roundBit && (stickyBit || shiftedMag(0))
  }
  roundedMag := shiftedMag.resize(97) + roundUp.asUInt.resize(97)

  val overflowPos = roundedMag(96 downto R_W) =/= 0
  val resultMag = roundedMag(R_W - 1 downto 0)

  val resultComb = UInt(R_W bits)
  resultComb := resultMag
  when(sign) {
    resultComb := (~resultMag + 1).resized
  }

  val underflowComb = Bool()
  underflowComb := isZero || (shiftSigned < -mantWidth)
  val overflowComb = isInf || (!underflowComb && overflowPos)
  val invalidComb = isNaN

  when(isZero || underflowComb || invalidComb) {
    resultComb := 0
  }

  private val convClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(convClockDomain) {
    val resultReg = Reg(UInt(R_W bits)) init (0)
    val invalidReg = Reg(Bool()) init (False)
    val overflowReg = Reg(Bool()) init (False)
    val underflowReg = Reg(Bool()) init (False)

    when(io.ce) {
      resultReg := resultComb
      invalidReg := invalidComb
      overflowReg := overflowComb
      underflowReg := underflowComb
    }

    io.RESULT := resultReg
    io.INVALID_OP := invalidReg
    io.OVERFLOW := overflowReg
    io.UNDERFLOW := underflowReg
  }
}

object FltToFixConv {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltToFixConvConfig
  ): (UInt, Bool, Bool, Bool) = {
    val module = new FltToFixConv(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    (module.io.RESULT, module.io.INVALID_OP, module.io.OVERFLOW, module.io.UNDERFLOW)
  }
}
