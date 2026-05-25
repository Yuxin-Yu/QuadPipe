package softmax.util

import spinal.core._

class FltLeadZeroEncode(
    val useRtl: Boolean,
    val dataWidth: Int,
    val distWidth: Int,
    val useDistIn: Boolean,
    val noLastTwoStages: Boolean,
    val hasAdd: Boolean
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val DATA = in Bits(dataWidth bits)
    val DATA_DEL = in Bits(dataWidth bits)
    val DIST_IN = in Bits(distWidth bits)
    val DIST_SKEW = out Bits(distWidth bits)
    val DIST = out Bits(distWidth bits)
    val ALL_BITS_ZERO = out Bool()
  }

  private val countWidth = log2Up(dataWidth + 1)
  val lzc = UInt(countWidth bits)
  lzc := U(dataWidth, countWidth bits)
  for (bit <- 0 until dataWidth) {
    when(io.DATA(bit)) {
      lzc := U(dataWidth - 1 - bit, countWidth bits)
    }
  }

  val baseDist = lzc.resize(distWidth)
  val allZeroComb = !io.DATA.orR
  val adjustedBaseDist = UInt(distWidth bits)
  adjustedBaseDist := baseDist
  if (hasAdd) {
    when(!allZeroComb && baseDist =/= 0) {
      adjustedBaseDist := (baseDist - 1).resized
    }
  }
  val distSum = UInt(distWidth bits)
  distSum := adjustedBaseDist
  if (useDistIn) {
    distSum := (adjustedBaseDist + io.DIST_IN.asUInt).resize(distWidth)
  }

  val distComb = distSum.asBits

  io.DIST_SKEW := distComb
  io.DIST := FltDelay(io.clk, io.ce, distComb, distWidth, 1)
  io.ALL_BITS_ZERO := FltDelay(io.clk, io.ce, allZeroComb.asBits, 1, 1).asBool
}

object FltLeadZeroEncode {
  def apply(
      clk: Bool,
      ce: Bool,
      DATA: Bits,
      DATA_DEL: Bits,
      DIST_IN: Bits,
      useRtl: Boolean,
      dataWidth: Int,
      distWidth: Int,
      useDistIn: Boolean,
      noLastTwoStages: Boolean,
      hasAdd: Boolean
  ): (Bits, Bits, Bool) = {
    val encoder = new FltLeadZeroEncode(useRtl, dataWidth, distWidth, useDistIn, noLastTwoStages, hasAdd)
    encoder.io.clk := clk
    encoder.io.ce := ce
    encoder.io.DATA := DATA
    encoder.io.DATA_DEL := DATA_DEL
    encoder.io.DIST_IN := DIST_IN
    (encoder.io.DIST_SKEW, encoder.io.DIST, encoder.io.ALL_BITS_ZERO)
  }
}
