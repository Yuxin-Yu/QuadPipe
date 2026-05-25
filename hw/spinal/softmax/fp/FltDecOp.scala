package softmax.fp

import spinal.core._

class FltDecOp(
    val rW: Int = 32,
    val rFw: Int = 24,
    val registered: Int = 1,
    val speed: Int = 1,
    val reducedRange: Int = 0,
    val expAdder: Int = 1,
    val updateFlagsLate: Int = 0,
    val noSr: Int = 0,
    val hasDivideByZero: Int = 0
) extends Component {

  val rEw = rW - rFw

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val DEC_OP_STATE = in Bits(12 bits)
    val FLOW = in Bits(4 bits)
    val INVALID_OP_IN = in Bool()
    val MANT = in Bits(rFw - 1 bits)
    val EXP = in Bits(rEw bits)
    val SIGN = in Bool()
    val EXP_INC = in Bool()
    val RESULT = out Bits(rW bits)
    val UNDERFLOW = out Bool()
    val OVERFLOW = out Bool()
    val INVALID_OP = out Bool()
  }

  private val opClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(opClockDomain) {
    val expValue = (io.EXP.asUInt + io.EXP_INC.asUInt).resized
    val resultValue = io.SIGN ## expValue.asBits ## io.MANT

    if (registered == 1) {
      io.RESULT := RegNextWhen(resultValue, io.ce) init(0)
      io.UNDERFLOW := RegNextWhen(io.FLOW(1), io.ce) init(False)
      io.OVERFLOW := RegNextWhen(io.FLOW(0), io.ce) init(False)
      io.INVALID_OP := RegNextWhen(io.INVALID_OP_IN, io.ce) init(False)
    } else {
      io.RESULT := resultValue
      io.UNDERFLOW := io.FLOW(1)
      io.OVERFLOW := io.FLOW(0)
      io.INVALID_OP := io.INVALID_OP_IN
    }
  }
}
