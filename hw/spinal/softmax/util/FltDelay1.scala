





package softmax.util

import spinal.core._


class FltDelay1(val width: Int = 1, val length: Int = 1) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce  = in Bool()
    val D   = in Bits(width bits)
    val Q   = out Bits(width bits)
  }

  private val delayClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(delayClockDomain) {
    val delayReg = Reg(Bits(width bits)) init(0)

    when(io.ce) {
      delayReg := io.D
    }

    io.Q := (if (length == 0) io.D else delayReg)
  }
}
