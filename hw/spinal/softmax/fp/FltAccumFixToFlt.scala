package softmax.fp

import spinal.core._

case class FltAccumFixToFltConfig(
  C_A_WIDTH: Int = 96,
  C_A_FRACTION_WIDTH: Int = 46,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,
  C_FIXED_DATA_UNSIGNED: Int = 0
) {
  val A_W = C_A_WIDTH
  val A_FW = C_A_FRACTION_WIDTH
  val R_W = C_RESULT_WIDTH
  val R_FW = C_RESULT_FRACTION_WIDTH
  val R_EW = C_RESULT_WIDTH - C_RESULT_FRACTION_WIDTH
}

class FltAccumFixToFlt(config: FltAccumFixToFltConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(A_W bits)
    val RESULT = out UInt(R_W bits)
  }

  private val convClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val EXP_BIAS_I = (1 << (R_EW - 1)) - 1
  private val ADJ_BIAS_I = EXP_BIAS_I + (A_W - A_FW) - 1

  private val logic = new ClockingArea(convClockDomain) {
    val aSign = if (C_FIXED_DATA_UNSIGNED == 0) io.A.asSInt < 0 else False
    val aAbs = UInt(A_W bits)
    if (C_FIXED_DATA_UNSIGNED == 0) {
      aAbs := Mux(aSign, (-io.A.asSInt).asUInt.resize(A_W), io.A)
    } else {
      aAbs := io.A
    }

    val stage0Sign = RegNextWhen(aSign, io.ce) init(False)
    val stage0Abs = RegNextWhen(aAbs, io.ce) init(0)
    val stage0AllZero = RegNextWhen(io.A.asSInt === 0, io.ce) init(True)

    val lzCount = UInt(log2Up(A_W + 1) bits)
    lzCount := U(A_W, lzCount.getWidth bits)
    for (bit <- 0 until A_W) {
      when(stage0Abs(bit)) {
        lzCount := U(A_W - 1 - bit, lzCount.getWidth bits)
      }
    }

    val hasNonZero = stage0Abs.orR

    val stage1Sign = RegNextWhen(stage0Sign, io.ce) init(False)
    val stage1Abs = RegNextWhen(stage0Abs, io.ce) init(0)
    val stage1LzCount = RegNextWhen(lzCount, io.ce) init(0)
    val stage1AllZero = RegNextWhen(stage0AllZero, io.ce) init(True)
    val stage1HasNonZero = RegNextWhen(hasNonZero, io.ce) init(False)

    val shiftedMant = UInt((R_FW + 2) bits)
    when(stage1AllZero || !stage1HasNonZero) {
      shiftedMant := 0
    } otherwise {
      shiftedMant := (stage1Abs |<< stage1LzCount).resize(A_W)(A_W - 1 downto A_W - R_FW - 2)
    }

    val stage2Sign = RegNextWhen(stage1Sign, io.ce) init(False)
    val stage2Mant = RegNextWhen(shiftedMant, io.ce) init(0)
    val stage2LzCount = RegNextWhen(stage1LzCount, io.ce) init(0)
    val stage2AllZero = RegNextWhen(stage1AllZero, io.ce) init(True)

    val mantWithImplicit = stage2Mant(R_FW + 1 downto 2)
    val mantRounded = mantWithImplicit
    val roundOverflow = False

    val stage3Sign = RegNextWhen(stage2Sign, io.ce) init(False)
    val stage3Mant = RegNextWhen(mantRounded, io.ce) init(0)
    val stage3LzCount = RegNextWhen(stage2LzCount, io.ce) init(0)
    val stage3RoundOverflow = RegNextWhen(roundOverflow, io.ce) init(False)
    val stage3AllZero = RegNextWhen(stage2AllZero, io.ce) init(True)

    val expCalc = SInt(10 bits)
    expCalc := S(ADJ_BIAS_I, 10 bits) - (stage3LzCount.resize(10).asSInt + S(1, 10 bits)) +
      stage3RoundOverflow.asUInt.resize(10).asSInt

    val expValue = UInt(R_EW bits)
    expValue := 0
    when(!stage3AllZero) {
      when(expCalc <= 0) {
        expValue := 0
      } elsewhen(expCalc >= S((1 << R_EW) - 1, 10 bits)) {
        expValue := U((1 << R_EW) - 1, R_EW bits)
      } otherwise {
        expValue := expCalc.asUInt.resize(R_EW)
      }
    }

    val finalMant = UInt((R_FW - 1) bits)
    finalMant := 0
    when(!stage3AllZero && expCalc > 0 && expCalc < S((1 << R_EW) - 1, 10 bits)) {
      finalMant := stage3Mant(R_FW - 2 downto 0)
    }

    val stage4Sign = RegNextWhen(stage3Sign, io.ce) init(False)
    val stage4Exp = RegNextWhen(expValue, io.ce) init(0)
    val stage4Mant = RegNextWhen(finalMant, io.ce) init(0)

    val result = Cat(stage4Sign, stage4Exp, stage4Mant).asUInt
    io.RESULT := RegNextWhen(result, io.ce) init(0)
  }
}

object FltAccumFixToFlt {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltAccumFixToFltConfig
  ): UInt = {
    val module = new FltAccumFixToFlt(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.RESULT
  }
}
