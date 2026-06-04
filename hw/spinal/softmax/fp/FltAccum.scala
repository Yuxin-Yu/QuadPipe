package softmax.fp

import spinal.core._
import softmax.util.FltDelay

case class FltAccumConfig(
  C_MULT_USAGE: Int = 1,
  C_OPTIMIZATION: Int = 1,
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_ACCUM_INPUT_MSB: Int = 48,
  C_ACCUM_MSB: Int = 48,
  C_ACCUM_LSB: Int = -47,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,
  C_HAS_ADD: Int = 1,
  C_HAS_SUB: Int = 0,
  SFM_DSP48_VER: String = "DSP48E2",
  REGISTERS: String = "10_0010_0100_1001_0010_0100_1000"
)

class FltAccum(config: FltAccumConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val rst = in Bool()
    val a_raw = in UInt(C_A_WIDTH bits)
    val valid = in Bool()
    val last = in Bool()
    val subtract_op = in Bits(6 bits)
    val result = out UInt(C_RESULT_WIDTH bits)
    val underflow = out Bool()
    val overflow = out Bool()
    val invalid_op = out Bool()
    val input_overflow = out Bool()
    val accum_overflow = out Bool()
  }

  private val accumClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rst,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(accumClockDomain) {
    val fltToFix = new FltAccumFltToFix(FltAccumFltToFixConfig(
      C_A_WIDTH = C_A_WIDTH,
      C_A_FRACTION_WIDTH = C_A_FRACTION_WIDTH,
      C_RESULT_WIDTH = 96,
      C_RESULT_FRACTION_WIDTH = 46,
      C_HAS_ROUNDING = 0,
      REGISTERS = "000_1001"
    ))
    fltToFix.io.clk := io.clk
    fltToFix.io.ce := io.ce
    fltToFix.io.A := io.a_raw

    val validD = RegNextWhen(io.valid, io.ce) init(False)
    val lastD = RegNextWhen(io.last, io.ce) init(False)
    val subtractD = RegNextWhen(io.subtract_op(0), io.ce) init(False)

    val accumFix = Reg(SInt(96 bits)) init(0)
    val deltaFix = fltToFix.io.RESULT.asSInt
    val nextAccumFix = SInt(96 bits)

    when(subtractD) {
      nextAccumFix := accumFix - deltaFix
    } otherwise {
      nextAccumFix := accumFix + deltaFix
    }

    val fixToFlt = new FltAccumFixToFlt(FltAccumFixToFltConfig(
      C_A_WIDTH = 96,
      C_A_FRACTION_WIDTH = 46,
      C_RESULT_WIDTH = C_RESULT_WIDTH,
      C_RESULT_FRACTION_WIDTH = C_RESULT_FRACTION_WIDTH,
      C_FIXED_DATA_UNSIGNED = 0
    ))
    fixToFlt.io.clk := io.clk
    fixToFlt.io.ce := io.ce
    fixToFlt.io.A := nextAccumFix.asUInt

    when(io.ce && validD) {
      accumFix := nextAccumFix
      when(lastD) {
        accumFix := 0
      }
    }

    io.result := FltDelay(io.clk, io.ce, fixToFlt.io.RESULT.asBits, C_RESULT_WIDTH, 1).asUInt
    io.underflow := fltToFix.io.UNDERFLOW
    io.overflow := fltToFix.io.OVERFLOW
    io.invalid_op := fltToFix.io.INVALID_OP
    io.input_overflow := False
    io.accum_overflow := False
  }
}

object FltAccum {
  def apply(
    clk: Bool,
    ce: Bool,
    rst: Bool,
    a_raw: UInt,
    valid: Bool,
    last: Bool,
    subtract_op: Bits,
    config: FltAccumConfig
  ): (UInt, Bool, Bool, Bool, Bool, Bool) = {
    val module = new FltAccum(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.rst := rst
    module.io.a_raw := a_raw
    module.io.valid := valid
    module.io.last := last
    module.io.subtract_op := subtract_op
    (
      module.io.result,
      module.io.underflow,
      module.io.overflow,
      module.io.invalid_op,
      module.io.input_overflow,
      module.io.accum_overflow
    )
  }
}
