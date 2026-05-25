package softmax.util

import spinal.core._

class FltDelay(val width: Int, val length: Int) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val D = in Bits(width bits)
    val Q = out Bits(width bits)
  }

  private val delayClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(delayClockDomain) {
    if (length == 0) {
      io.Q := io.D
    } else {
      val delayRegs = Vec.fill(length)(Reg(Bits(width bits)) init(0))
      when(io.ce) {
        delayRegs(0) := io.D
        for (i <- 1 until length) {
          delayRegs(i) := delayRegs(i - 1)
        }
      }
      io.Q := delayRegs(length - 1)
    }
  }
}

object FltDelay {
  def apply(clk: Bool, ce: Bool, D: Bits, width: Int, length: Int): Bits = {
    val delay = new FltDelay(width, length)
    delay.io.clk := clk
    delay.io.ce := ce
    delay.io.D := D
    delay.io.Q
  }
}
