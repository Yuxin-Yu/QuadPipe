// SPDX-License-Identifier: MIT

package softmax.fp

import spinal.core._

case class FltFixToFltConvConfig(
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 16,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,
  C_FIXED_DATA_UNSIGNED: Int = 0,
  REGISTERS: String = "0000_0000_0011_1011"
) {
  val A_W = C_A_WIDTH
  val A_FW = C_A_FRACTION_WIDTH
  val R_W = C_RESULT_WIDTH
  val R_FW = C_RESULT_FRACTION_WIDTH
  val R_EW = C_RESULT_WIDTH - C_RESULT_FRACTION_WIDTH
}

class FltFixToFltConv(config: FltFixToFltConvConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(A_W bits)

    val RESULT = out UInt(R_W bits)
  }

  val signComb = if (C_FIXED_DATA_UNSIGNED == 0) io.A.msb else False
  val absComb = UInt(A_W bits)
  if (C_FIXED_DATA_UNSIGNED == 0) {
    absComb := Mux(io.A.msb, (~io.A + 1).resized, io.A)
  } else {
    absComb := io.A
  }

  val isZero = absComb === 0

  val highestOne = UInt(log2Up(A_W) bits)
  highestOne := 0
  for (bit <- 0 until A_W) {
    when(absComb(bit)) {
      highestOne := U(bit, highestOne.getWidth bits)
    }
  }

  val normalized = UInt(A_W bits)
  normalized := (absComb |<< (U(A_W - 1, highestOne.getWidth bits) - highestOne)).resized

  val mantissaBase = normalized(A_W - 2 downto A_W - R_FW)
  val guardBit = normalized(A_W - R_FW - 1)
  val stickyBit = normalized(A_W - R_FW - 2 downto 0).orR
  val roundUp = guardBit && (stickyBit || mantissaBase(0))

  val mantissaRounded = UInt(R_FW bits)
  mantissaRounded := mantissaBase.resize(R_FW) + roundUp.asUInt.resize(R_FW)

  val exponentBase = UInt((R_EW + 1) bits)
  exponentBase := (highestOne.resize(R_EW + 1) + U(((1 << (R_EW - 1)) - 1) - A_FW, R_EW + 1 bits)).resized

  val expComb = UInt(R_EW bits)
  val mantComb = UInt((R_FW - 1) bits)
  expComb := 0
  mantComb := 0

  when(!isZero) {
    when(mantissaRounded(R_FW - 1)) {
      expComb := (exponentBase + 1).resize(R_EW)
      mantComb := 0
    } otherwise {
      expComb := exponentBase.resize(R_EW)
      mantComb := mantissaRounded(R_FW - 2 downto 0)
    }
  }

  val resultComb = Cat(signComb, expComb, mantComb).asUInt

  private val convClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(convClockDomain) {
    val resultReg = Reg(UInt(R_W bits)) init (0)
    when(io.ce) {
      resultReg := resultComb
    }
    io.RESULT := resultReg
  }
}

object FltFixToFltConv {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltFixToFltConvConfig
  ): UInt = {
    val module = new FltFixToFltConv(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.RESULT
  }
}
