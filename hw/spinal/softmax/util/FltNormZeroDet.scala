package softmax.util

import spinal.core._

class FltNormZeroDet(
    val dataWidth: Int = 7,
    val normWidth: Int = 32,
    val distWidth: Int = 8,
    val lastBitsToOmit: Int = 0,
    val legacy: Int = 1
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val data = in Bits(dataWidth bits)
    val dist = in Bits(distWidth bits)
    val remainingZero = out Bool()
  }

  // Conservative compatibility behavior: remaining bits are considered zero
  // when the tail input bits are all zero.
  val remainingZeroComb = !io.data.orR
  io.remainingZero := FltDelay(io.clk, io.ce, remainingZeroComb.asBits, 1, 1).asBool
}
