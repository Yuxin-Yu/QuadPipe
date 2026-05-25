package softmax.util

import spinal.core._

class FltShiftMsbFirst(
    val aWidth: Int = 27,
    val resultWidth: Int = 29,
    val distanceWidth: Int = 8,
    val aSigned: Int = 0,
    val shiftLeft: Int = 0,
    val lastStagesToOmit: Int = 2,
    val skewedDist: Int = 0,
    val reducedPipe: Int = 0,
    val registers: Bits = B"0000_0000_0001_0101"
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in Bits(aWidth bits)
    val distance = in Bits(distanceWidth bits)
    val result = out Bits(resultWidth bits)
  }

  private val width = if (shiftLeft == 1) aWidth else resultWidth
  private val shiftWidth = log2Up(width + 1)

  private def reverseBits(value: Bits): Bits = {
    val reversed = Bits(value.getWidth bits)
    for (idx <- 0 until value.getWidth) {
      reversed(idx) := value(value.getWidth - 1 - idx)
    }
    reversed
  }

  val paddedA = Bits(width bits)
  if (width > aWidth) {
    paddedA := io.A ## B(width - aWidth bits, default -> False)
  } else {
    paddedA := io.A(aWidth - 1 downto aWidth - width)
  }

  val shiftAmount = io.distance.asUInt.resize(shiftWidth)
  val shifted = Bits(width bits)

  if (shiftLeft == 1) {
    shifted := (reverseBits(paddedA).asUInt |>> shiftAmount).asBits
  } else if (aSigned == 1) {
    shifted := (paddedA.asSInt |>> shiftAmount).asBits
  } else {
    shifted := (paddedA.asUInt |>> shiftAmount).asBits
  }

  val resultComb = Bits(resultWidth bits)
  if (resultWidth > width) {
    val widened = B(resultWidth - width bits, default -> False) ## shifted
    resultComb := (if (shiftLeft == 1) reverseBits(widened) else widened)
  } else {
    val sliced = shifted(resultWidth - 1 downto 0)
    resultComb := (if (shiftLeft == 1) reverseBits(sliced) else sliced)
  }

  io.result := FltDelay(io.clk, io.ce, resultComb, resultWidth, 0)
}
