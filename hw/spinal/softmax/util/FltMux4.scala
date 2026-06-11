package softmax.util

import spinal.core._

class FltMux4(val width: Int, val threeSel: Boolean, val length: Int) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val IP0 = in Bits(width bits)
    val IP1 = in Bits(width bits)
    val IP2 = in Bits(width bits)
    val IP3 = in Bits(width bits)
    val SEL0 = in Bool()
    val SEL0_3 = in Bool()
    val SEL1 = in Bool()
    val OP = out Bits(width bits)
  }

  val selB = if (threeSel) io.SEL0_3 else io.SEL0
  val opA = Mux(io.SEL0, io.IP1, io.IP0)
  val opB = Mux(selB, io.IP3, io.IP2)
  val opInt = Mux(io.SEL1, opB, opA)

  io.OP := FltDelay(io.clk, io.ce, opInt, width, length)
}

object FltMux4 {
  def apply(clk: Bool, ce: Bool, IP0: Bits, IP1: Bits, IP2: Bits, IP3: Bits,
            SEL0: Bool, SEL0_3: Bool, SEL1: Bool,
            width: Int, threeSel: Boolean, length: Int): Bits = {
    val mux = new FltMux4(width, threeSel, length)
    mux.io.clk := clk
    mux.io.ce := ce
    mux.io.IP0 := IP0
    mux.io.IP1 := IP1
    mux.io.IP2 := IP2
    mux.io.IP3 := IP3
    mux.io.SEL0 := SEL0
    mux.io.SEL0_3 := SEL0_3
    mux.io.SEL1 := SEL1
    mux.io.OP
  }
}
