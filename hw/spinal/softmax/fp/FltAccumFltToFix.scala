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

  private val logic = new ClockingArea(convClockDomain) {
    val resultReg = Reg(UInt(R_W bits)) init(0)

    when(io.ce) {
      resultReg := io.A.resize(R_W)
    }

    io.RESULT := resultReg
    io.INVALID_OP := False
    io.OVERFLOW := False
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
