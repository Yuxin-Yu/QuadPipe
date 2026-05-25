package softmax.fp

import spinal.core._

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
    // DSP48 primitive wrapper instantiation based on SFM_DSP48_VER
    if (config.SFM_DSP48_VER == "DSP48E1") {
      val dsp48e1 = new FltDsp48e1Wrapper(FltDsp48e1WrapperConfig(
        A_WIDTH = 2, B_WIDTH = 16, C_WIDTH = 16,
        D_WIDTH = 27, P_WIDTH = C_RESULT_WIDTH
      ))
      dsp48e1.io.clk := io.clk
      dsp48e1.io.ce := io.ce
      dsp48e1.io.A_IN := U(0, 2 bits)
      dsp48e1.io.B_IN := U(0, 16 bits)
      dsp48e1.io.C_IN := U(0, 16 bits)
      dsp48e1.io.D_IN := U(0, 27 bits)
      dsp48e1.io.CARRY_IN := False
      dsp48e1.io.OP_MODE := B"000000000"
      dsp48e1.io.ALU_MODE := B"0000"
      dsp48e1.io.IN_MODE := B"00000"
    } else {
      val dsp48e2 = new FltDsp48e2Wrapper(FltDsp48e2WrapperConfig(
        A_WIDTH = 2, B_WIDTH = 16, C_WIDTH = 16,
        D_WIDTH = 27, P_WIDTH = C_RESULT_WIDTH
      ))
      dsp48e2.io.clk := io.clk
      dsp48e2.io.ce := io.ce
      dsp48e2.io.A_IN := U(0, 2 bits)
      dsp48e2.io.B_IN := U(0, 16 bits)
      dsp48e2.io.C_IN := U(0, 16 bits)
      dsp48e2.io.D_IN := U(0, 27 bits)
      dsp48e2.io.CARRY_IN := False
      dsp48e2.io.OP_MODE := B"000000000"
      dsp48e2.io.ALU_MODE := B"0000"
      dsp48e2.io.IN_MODE := B"00000"
    }

    val accum = Reg(UInt(C_RESULT_WIDTH bits)) init(0)
    val resultReg = Reg(UInt(C_RESULT_WIDTH bits)) init(0)
    val delta = io.a_raw.resize(C_RESULT_WIDTH)
    val nextAccum = UInt(C_RESULT_WIDTH bits)

    when(io.subtract_op(0)) {
      nextAccum := accum - delta
    } otherwise {
      nextAccum := accum + delta
    }

    when(io.ce && io.valid) {
      accum := nextAccum
      when(io.last) {
        resultReg := nextAccum
        accum := 0
      }
    }

    io.result := resultReg
    io.underflow := False
    io.overflow := False
    io.invalid_op := False
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
