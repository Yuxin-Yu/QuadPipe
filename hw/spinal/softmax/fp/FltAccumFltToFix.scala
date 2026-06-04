package softmax.fp

import spinal.core._

case class FltAccumFltToFixConfig(
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_RESULT_WIDTH: Int = 96,
  C_RESULT_FRACTION_WIDTH: Int = 46,
  C_HAS_ROUNDING: Int = 1,
  REGISTERS: String = "000_0000"
) {
  val A_W = C_A_WIDTH
  val A_EW = C_A_WIDTH - C_A_FRACTION_WIDTH
  val A_FW = C_A_FRACTION_WIDTH
  val R_W = C_RESULT_WIDTH
  val R_FW = C_RESULT_FRACTION_WIDTH
}

class FltAccumFltToFix(config: FltAccumFltToFixConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(C_A_WIDTH bits)
    val RESULT = out UInt(C_RESULT_WIDTH bits)
    val INVALID_OP = out Bool()
    val OVERFLOW = out Bool()
    val UNDERFLOW = out Bool()
  }

  private val convClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val EXP_BIAS_I = (1 << (A_EW - 1)) - 1
  private val MOD_BIAS = R_W - (R_FW + 1) + EXP_BIAS_I - 1

  private val logic = new ClockingArea(convClockDomain) {
    val aSign = io.A(A_W - 1)
    val aExp = io.A(A_W - 2 downto A_FW - 1)
    val aMant = io.A(A_FW - 2 downto 0)

    val aExpAllZero = aExp === 0
    val aExpAllOne = aExp === U((1 << A_EW) - 1, A_EW bits)
    val aMantAllZero = aMant === 0

    val aMantWithImplicit = UInt(A_FW bits)
    aMantWithImplicit := Mux(aExpAllZero, (False ## aMant).asUInt, (True ## aMant).asUInt)

    val alignDist = SInt(10 bits)
    alignDist := S(MOD_BIAS, 10 bits) - aExp.resize(10).asSInt

    val alignDistAdj = SInt(10 bits)
    alignDistAdj := Mux(aExpAllZero, S(MOD_BIAS - 1, 10 bits), alignDist)

    val alignOverflow = alignDistAdj < 0
    val alignUnderflow = alignDistAdj >= S(R_W, 10 bits)

    val mantWide = UInt(R_W bits)
    mantWide := aMantWithImplicit.resize(R_W)

    val shiftedFromHigh = UInt((R_W + 72) bits)
    shiftedFromHigh := (mantWide.resize(R_W + 72) |<< 72).resize(R_W + 72)

    val shiftAmount = UInt(7 bits)
    shiftAmount := Mux(alignDistAdj > S(R_W, 10 bits), U(R_W, 7 bits), alignDistAdj.asUInt.resize(7))

    val alignedMant = UInt(R_W bits)
    when(alignUnderflow) {
      alignedMant := 0
    } elsewhen(alignOverflow) {
      alignedMant := U((BigInt(1) << R_W) - 1, R_W bits)
    } otherwise {
      alignedMant := (shiftedFromHigh |>> shiftAmount).resize(R_W)
    }

    val signedResult = UInt(R_W bits)
    signedResult := Mux(aSign, (~alignedMant + 1).resized, alignedMant)

    val finalResult = UInt(R_W bits)
    finalResult := signedResult
    when(aExpAllOne && !aMantAllZero) {
      finalResult := 0
    } elsewhen(aExpAllOne && aMantAllZero) {
      finalResult := Mux(aSign, U(BigInt(1) << (R_W - 1), R_W bits), U((BigInt(1) << (R_W - 1)) - 1, R_W bits))
    } elsewhen(aExpAllZero && aMantAllZero) {
      finalResult := 0
    }

    val resultReg = Reg(UInt(R_W bits)) init(0)
    val invalidOpReg = Reg(Bool()) init(False)
    val overflowReg = Reg(Bool()) init(False)

    when(io.ce) {
      resultReg := finalResult
      invalidOpReg := aExpAllOne && !aMantAllZero
      overflowReg := alignOverflow && !aExpAllOne
    }

    io.RESULT := resultReg
    io.INVALID_OP := invalidOpReg
    io.OVERFLOW := overflowReg
    io.UNDERFLOW := False
  }
}

object FltAccumFltToFix {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltAccumFltToFixConfig
  ): (UInt, Bool, Bool, Bool) = {
    val module = new FltAccumFltToFix(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    (module.io.RESULT, module.io.INVALID_OP, module.io.OVERFLOW, module.io.UNDERFLOW)
  }
}
