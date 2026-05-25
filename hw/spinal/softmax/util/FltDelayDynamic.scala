package softmax.util

import spinal.core._

class FltDelayDynamic(val width: Int, val registers: Bits) extends Component {
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
    val delayChain = Vec.fill(31)(Reg(Bits(width bits)) init(0))
    val delayOut = Vec(Bits(width bits), 31)

    when(io.ce) {
      delayChain(0) := io.D
      for (i <- 1 until 31) {
        delayChain(i) := delayChain(i - 1)
      }
    }

    delayOut(0) := io.D
    for (i <- 1 until 31) {
      when(registers(i - 1)) {
        delayOut(i) := delayChain(i)
      } otherwise {
        delayOut(i) := delayOut(i - 1)
      }
    }

    io.Q := delayOut(30)
  }
}

object FltDelayDynamic {
  def apply(clk: Bool, ce: Bool, D: Bits, width: Int, registers: Bits): Bits = {
    val delay = new FltDelayDynamic(width, registers)
    delay.io.clk := clk
    delay.io.ce := ce
    delay.io.D := D
    delay.io.Q
  }
}
