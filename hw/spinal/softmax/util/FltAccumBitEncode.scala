package softmax.util

import spinal.core._

class FltAccumBitEncode(
    val dataWidth: Int = 48,
    val encodeWidth: Int = 6,
    val dataLsbWeight: Int = 0,
    val lsbDetect: Int = 0,
    val delay: Int = 1,
    val lsbEncodeWidth: Int = 2,
    val msbEncodeChunk: Int = 6
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val data = in Bits(dataWidth bits)
    val encodeIn = in Bits(encodeWidth bits)
    val allZeroIn = in Bool()
    val encodeOut = out Bits(encodeWidth bits)
    val allZeroOut = out Bool()
  }

  private val idxWidth = log2Up(dataWidth + 1)
  private val localDelay = Math.max(delay, 0)

  val found = Bool()
  val hitIdx = UInt(idxWidth bits)
  found := False
  hitIdx := 0

  if (lsbDetect == 1) {
    for (i <- 0 until dataWidth) {
      when(io.data(i) && !found) {
        found := True
        hitIdx := U(i, idxWidth bits)
      }
    }
  } else {
    for (i <- (0 until dataWidth).reverse) {
      when(io.data(i) && !found) {
        found := True
        hitIdx := U((dataWidth - 1) - i, idxWidth bits)
      }
    }
  }

  val encodeAdd = hitIdx.resize(encodeWidth)
  val encodeComb = (io.encodeIn.asUInt + encodeAdd).asBits.resize(encodeWidth)
  val allZeroComb = io.allZeroIn && !io.data.orR

  io.encodeOut := FltDelay(io.clk, io.ce, encodeComb, encodeWidth, localDelay)
  io.allZeroOut := FltDelay(io.clk, io.ce, allZeroComb.asBits, 1, localDelay).asBool
}
