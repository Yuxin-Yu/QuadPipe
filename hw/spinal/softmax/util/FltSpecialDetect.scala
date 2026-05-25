package softmax.util

import spinal.core._

class FltSpecialDetect(val aW: Int, val aFw: Int, val opDelay: Int) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in Bits(aW bits)
    val MANT_ALL_ZERO = out Bool()
    val EXP_ALL_ONE = out Bool()
    val EXP_ALL_ZERO = out Bool()
  }

  val expWidth = aW - aFw
  val mantWidth = aFw - 1

  val expAllZeroNd = io.A(aW-2 downto aFw-1) === B(expWidth bits, default -> False)
  val expAllOneNd = io.A(aW-2 downto aFw-1) === B(expWidth bits, default -> True)
  val mantAllZeroNd = io.A(aFw-2 downto 0) === B(mantWidth bits, default -> False)

  io.MANT_ALL_ZERO := FltDelay(io.clk, io.ce, mantAllZeroNd.asBits, 1, opDelay).asBool
  io.EXP_ALL_ONE := FltDelay(io.clk, io.ce, expAllOneNd.asBits, 1, opDelay).asBool
  io.EXP_ALL_ZERO := FltDelay(io.clk, io.ce, expAllZeroNd.asBits, 1, opDelay).asBool
}

object FltSpecialDetect {
  def apply(clk: Bool, ce: Bool, A: Bits, aW: Int, aFw: Int, opDelay: Int): (Bool, Bool, Bool) = {
    val detect = new FltSpecialDetect(aW, aFw, opDelay)
    detect.io.clk := clk
    detect.io.ce := ce
    detect.io.A := A
    (detect.io.MANT_ALL_ZERO, detect.io.EXP_ALL_ONE, detect.io.EXP_ALL_ZERO)
  }
}
