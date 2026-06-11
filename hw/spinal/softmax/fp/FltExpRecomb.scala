


















package softmax.fp

import spinal.core._

case class FltExpRecombConfig(
  EXPONENT_WIDTH: Int = 8,
  MANTISSA_WIDTH: Int = 23,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,

  C_WF: Int = 24
) {
  val FLT_STATE_NORMAL = 0
  val FLT_STATE_NAN = 1
  val FLT_STATE_ZERO = 2
  val FLT_STATE_INF = 3
}

class FltExpRecomb(config: FltExpRecombConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val special_case = in Bits(2 bits)
    val input_is_overflow = in Bool()
    val input_is_underflow = in Bool()
    val input_sign = in Bool()
    val output_is_overflow = in Bool()
    val output_is_underflow = in Bool()
    val res_sign = in Bool()
    val res_exponent = in UInt(EXPONENT_WIDTH bits)
    val res_mantissa = in UInt(MANTISSA_WIDTH bits)

    val result = out UInt(C_RESULT_WIDTH bits)
    val underflow = out Bool()
    val overflow = out Bool()
  }

  private val cd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(cd) {


    def expOf(v: BigInt): UInt = U((v >> MANTISSA_WIDTH) & ((BigInt(1) << EXPONENT_WIDTH) - 1), EXPONENT_WIDTH bits)
    def mantOf(v: BigInt): UInt = U(v & ((BigInt(1) << MANTISSA_WIDTH) - 1), MANTISSA_WIDTH bits)
    val ZERO = BigInt("00000000", 16)
    val INF = BigInt("7f800000", 16)
    val QUIET_NAN = BigInt("7fc00000", 16)
    val ONE = BigInt("3f800000", 16)

    val sign_i = Bool()
    val exponent_i = UInt(EXPONENT_WIDTH bits)
    val mantissa_i = UInt(MANTISSA_WIDTH bits)
    sign_i := io.res_sign
    exponent_i := io.res_exponent
    mantissa_i := io.res_mantissa

    val sc = io.special_case
    when(sc === B(FLT_STATE_NAN, 2 bits)) {
      sign_i := False; exponent_i := expOf(QUIET_NAN); mantissa_i := mantOf(QUIET_NAN)
    } elsewhen (sc === B(FLT_STATE_ZERO, 2 bits)) {
      sign_i := False; exponent_i := expOf(ONE); mantissa_i := mantOf(ONE)
    } elsewhen (sc === B(FLT_STATE_INF, 2 bits) && io.input_sign) {
      sign_i := io.res_sign; exponent_i := expOf(ZERO); mantissa_i := mantOf(ZERO)
    } elsewhen (sc === B(FLT_STATE_INF, 2 bits)) {
      sign_i := False; exponent_i := expOf(INF); mantissa_i := mantOf(INF)
    } elsewhen (io.input_is_overflow && io.input_sign) {
      sign_i := False; exponent_i := expOf(ZERO); mantissa_i := mantOf(ZERO)
    } elsewhen (io.input_is_overflow) {
      sign_i := False; exponent_i := expOf(INF); mantissa_i := mantOf(INF)
    } elsewhen (io.input_is_underflow) {
      sign_i := False; exponent_i := expOf(ONE); mantissa_i := mantOf(ZERO)
    } elsewhen (io.output_is_overflow) {
      sign_i := False; exponent_i := expOf(INF); mantissa_i := mantOf(INF)
    } elsewhen (io.output_is_underflow) {
      sign_i := False; exponent_i := expOf(ZERO); mantissa_i := mantOf(ZERO)
    }

    val result_i = RegNext((sign_i ## exponent_i ## mantissa_i).asUInt) init(0)
    val isNormal = sc === B(FLT_STATE_NORMAL, 2 bits)
    val overflow_i = RegNext(isNormal & (io.output_is_overflow | (io.input_is_overflow & !io.input_sign))) init(False)
    val underflow_i = RegNext(isNormal & (io.output_is_overflow | (io.input_is_overflow & io.input_sign))) init(False)

    io.result := result_i
    io.overflow := overflow_i
    io.underflow := underflow_i
  }
}

object FltExpRecomb {
  def apply(
    clk: Bool,
    ce: Bool,
    special_case: Bits,
    input_is_overflow: Bool,
    input_is_underflow: Bool,
    input_sign: Bool,
    output_is_overflow: Bool,
    output_is_underflow: Bool,
    res_sign: Bool,
    res_exponent: UInt,
    res_mantissa: UInt,
    config: FltExpRecombConfig
  ): (UInt, Bool, Bool) = {
    val module = new FltExpRecomb(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.special_case := special_case
    module.io.input_is_overflow := input_is_overflow
    module.io.input_is_underflow := input_is_underflow
    module.io.input_sign := input_sign
    module.io.output_is_overflow := output_is_overflow
    module.io.output_is_underflow := output_is_underflow
    module.io.res_sign := res_sign
    module.io.res_exponent := res_exponent
    module.io.res_mantissa := res_mantissa
    (module.io.result, module.io.underflow, module.io.overflow)
  }
}
