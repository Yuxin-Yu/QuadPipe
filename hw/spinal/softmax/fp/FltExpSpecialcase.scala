











package softmax.fp

import spinal.core._
import softmax.util.{FltDelay, FltSpecialDetect}

case class FltExpSpecialcaseConfig(
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_WE: Int = 8,
  C_WF: Int = 23,
  C_G: Int = 3,

  C_RESULT_WIDTH: Int = 32
) {
  val EXPONENT_WIDTH = C_A_WIDTH - C_A_FRACTION_WIDTH
  val IEEE_BIAS = (1 << (C_A_WIDTH - C_A_FRACTION_WIDTH - 1)) - 1
  val RANGE_OVERFLOW_VALUE = C_WE - 2 + IEEE_BIAS
  val RANGE_UNDERFLOW_VALUE = IEEE_BIAS - C_WF - C_G

  val FLT_STATE_NORMAL = 0
  val FLT_STATE_NAN = 1
  val FLT_STATE_ZERO = 2
  val FLT_STATE_INF = 3
}

class FltExpSpecialcase(config: FltExpSpecialcaseConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val x = in UInt(C_A_WIDTH bits)

    val special_case = out Bits(2 bits)
    val input_is_overflow = out Bool()
    val input_is_underflow = out Bool()
    val input_sign = out Bool()
  }

  private val cd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(cd) {
    val input_sign_i = io.x(C_A_WIDTH - 1)


    val det = new FltSpecialDetect(aW = C_A_WIDTH, aFw = C_A_FRACTION_WIDTH, opDelay = 1)
    det.io.clk := io.clk
    det.io.ce := io.ce
    det.io.A := io.x.asBits
    val mant_all_zero = det.io.MANT_ALL_ZERO
    val exp_all_one = det.io.EXP_ALL_ONE
    val exp_all_zero = det.io.EXP_ALL_ZERO


    val expField = io.x(C_A_WIDTH - 2 downto C_A_FRACTION_WIDTH - 1)

    val input_is_overflow_i = RegNext(expField > U(RANGE_OVERFLOW_VALUE, EXPONENT_WIDTH bits)) init(False)
    val input_is_underflow_i = RegNext(expField < U(RANGE_UNDERFLOW_VALUE, EXPONENT_WIDTH bits)) init(False)

    val flag_async = Reg(Bits(2 bits)) init(0)
    when(exp_all_zero) {
      flag_async := B(FLT_STATE_ZERO, 2 bits)
    } elsewhen (exp_all_one && mant_all_zero) {
      flag_async := B(FLT_STATE_INF, 2 bits)
    } elsewhen (exp_all_one && !mant_all_zero) {
      flag_async := B(FLT_STATE_NAN, 2 bits)
    } otherwise {
      flag_async := B(FLT_STATE_NORMAL, 2 bits)
    }


    io.input_is_overflow := FltDelay(io.clk, io.ce, input_is_overflow_i.asBits, 1, 12)(0)
    io.input_is_underflow := FltDelay(io.clk, io.ce, input_is_underflow_i.asBits, 1, 12)(0)
    io.special_case := FltDelay(io.clk, io.ce, flag_async, 2, 11)
    io.input_sign := FltDelay(io.clk, io.ce, input_sign_i.asBits, 1, 13)(0)
  }
}

object FltExpSpecialcase {
  def apply(
    clk: Bool,
    ce: Bool,
    x: UInt,
    config: FltExpSpecialcaseConfig
  ): (Bits, Bool, Bool, Bool) = {
    val module = new FltExpSpecialcase(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.x := x
    (module.io.special_case, module.io.input_is_overflow, module.io.input_is_underflow, module.io.input_sign)
  }
}
