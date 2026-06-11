



package softmax.fp

import spinal.core._
import spinal.lib._

case class FltDivMantAddSubConfig(
  W: Int = 25,
  LENGTH: Int = 1
) {}

class FltDivMantAddSub(config: FltDivMantAddSubConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(W bits)
    val B = in UInt(W bits)
    val SUB = in Bool()

    val Q_delay = out UInt(W bits)
    val Q = out UInt(W bits)
  }

  private val mantClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(mantClockDomain) {
    val qNow = Mux(io.SUB, io.A - io.B, io.A + io.B)
    val qDelayTemp = RegNextWhen(qNow, io.ce) init(0)

    io.Q_delay := Cat(~qDelayTemp.msb, qDelayTemp(W - 2 downto 0)).asUInt
    io.Q := Cat(~qNow.msb, qNow(W - 2 downto 0)).asUInt
  }
}


object FltDivMantAddSub {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    B: UInt,
    SUB: Bool,
    config: FltDivMantAddSubConfig
  ): (UInt, UInt) = {
    val module = new FltDivMantAddSub(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.B := B
    module.io.SUB := SUB
    (module.io.Q_delay, module.io.Q)
  }
}
