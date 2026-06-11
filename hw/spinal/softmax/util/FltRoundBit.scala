





package softmax.util

import spinal.core._


class FltRoundBit(
    val fixSupport: Boolean = false,
    val useRtl: Boolean = false,
    val normBits: Int = 1,
    val registers: Bits = B"00000"
) extends Component {
  val io = new Bundle {
    val clk            = in Bool()
    val ce             = in Bool()
    val mantLsbs       = in Bits(3 bits)
    val zero           = in Bool()
    val extraLsb       = in Bool()
    val extraLsbs      = in Bits(2 bits)
    val normalize      = in Bits(2 bits)
    val fixedPoint     = in Bool()
    val fixMantSign    = in Bool()
    val sign           = in Bool()

    val roundCarry     = out Bool()
  }


  val truncate              = False
  val rndInf                = False
  val infNeg                = False


  val localUseRtl = useRtl

  val nonZeroTruncatedPart  = Bool()
  val fixNegTruncate        = Bool()
  val zeroBit               = Bool()
  val lsb                   = Bool()
  val round                 = Bool()
  val roundBit              = Bool()
  val aIp                   = Bits(3 bits)
  val bIp                   = Bits(3 bits)
  val aBSum                 = UInt(4 bits)


  nonZeroTruncatedPart := io.mantLsbs(0) || io.mantLsbs(1) || !io.zero || io.extraLsb


  fixNegTruncate := (if (fixSupport) (io.fixedPoint && truncate && io.fixMantSign) else False)


  when(io.normalize(0)) {
    zeroBit := io.zero && !io.extraLsb && !io.extraLsbs(0) && !io.extraLsbs(1)
  } otherwise {
    zeroBit := io.zero && !io.extraLsb && !io.extraLsbs(0) && !io.extraLsbs(1) && !io.mantLsbs(0)
  }


  when(io.normalize(0)) {
    lsb := io.mantLsbs(1)
  } otherwise {
    lsb := io.mantLsbs(2)
  }


  when(io.normalize(0)) {
    round := !truncate && io.mantLsbs(0)
  } otherwise {
    round := !truncate && io.mantLsbs(1)
  }


  roundBit := (round && !zeroBit) || (round && zeroBit && lsb)


  bIp := B"100"
  aIp := Cat(!roundBit, fixNegTruncate, nonZeroTruncatedPart)
  aBSum := ((bIp ^ aIp).asUInt.resize(4) +^ bIp.asUInt.resize(4) +^ U(1, 4 bits)).resized
  io.roundCarry := aBSum(3)
}
