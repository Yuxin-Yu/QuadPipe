





package softmax.fp

import spinal.core._
import softmax.util.FltDelay


class FltAlignment(
    val abFw: Int = 24,
    val ipWidth: Int = 25,
    val opWidth: Int = 26,
    val distWidth: Int = 8,
    val zDetWidth: Int = 7,
    val possLastBits: Int = 2,
    val registers: Bits = null
) extends Component {
  private val localRegisters = if (registers != null) registers else B"0000_0010_1010_1010"

  val io = new Bundle {
    val clk            = in Bool()
    val ce             = in Bool()
    val dataIp         = in Bits(ipWidth bits)
    val dist           = in Bits(distWidth bits)
    val zerosDetIp     = in Bits(zDetWidth bits)

    val dataOp         = out Bits(opWidth bits)
    val zeros         = out Bool()
  }

  private val alignClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )


  val zeroDetStage = 2
  val shiftStage = 2
  val addStage = 2


  val extDataIp = Bits(opWidth bits)
  val distIp = Bits(5 bits)
  val alignedShift = Bits(opWidth bits)
  val shiftDataIp = Bits(opWidth bits)


  extDataIp := io.dataIp ## B(0, 1 bit)
  shiftDataIp := B(0, opWidth - ipWidth bits) ## io.dataIp
  distIp := io.dist(4 downto 1) ## B(0, 1 bit)

  private def lowBitsAllZero(width: Int): Bool = {
    val clampedWidth = width.min(opWidth)
    !extDataIp(clampedWidth - 1 downto 0).orR
  }

  private val logic = new ClockingArea(alignClockDomain) {
    val zerosZDet = Reg(Bool()) init(True)

    when(io.ce) {
      switch(distIp(4 downto 1)) {
        is(B"0001") { zerosZDet := lowBitsAllZero(2) }
        is(B"0010") { zerosZDet := lowBitsAllZero(4) }
        is(B"0011") { zerosZDet := lowBitsAllZero(6) }
        is(B"0100") { zerosZDet := lowBitsAllZero(8) }
        is(B"0101") { zerosZDet := lowBitsAllZero(10) }
        is(B"0110") { zerosZDet := lowBitsAllZero(12) }
        is(B"0111") { zerosZDet := lowBitsAllZero(14) }
        is(B"1000") { zerosZDet := lowBitsAllZero(16) }
        is(B"1001") { zerosZDet := lowBitsAllZero(18) }
        is(B"1010") { zerosZDet := lowBitsAllZero(20) }
        is(B"1011") { zerosZDet := lowBitsAllZero(22) }
        is(B"1100") { zerosZDet := lowBitsAllZero(24) }
        is(B"1101") { zerosZDet := lowBitsAllZero(26) }
        default { zerosZDet := True }
      }
    }
  }



  alignedShift := (shiftDataIp.asUInt >> io.dist(4 downto 1).asUInt).asBits.resized


  val delayDataOp = new FltDelay(
    width = opWidth,
    length = 0
  )
  delayDataOp.io.clk := io.clk
  delayDataOp.io.ce := io.ce
  delayDataOp.io.D := alignedShift
  io.dataOp := delayDataOp.io.Q

  val delayZeros = new FltDelay(
    width = 1,
    length = 0
  )
  delayZeros.io.clk := io.clk
  delayZeros.io.ce := io.ce
  delayZeros.io.D := logic.zerosZDet.asBits
  io.zeros := delayZeros.io.Q.asBool
}
