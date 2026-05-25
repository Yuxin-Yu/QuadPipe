package softmax.core

import spinal.core._

class RamS2p1cSm(val cRamS: Int, val cRamW: Int, val cRamD: Int, val cOutputReg: Int = 0) extends Component {
  val io = new Bundle {
    val sleep = in Bool()
    val clka = in Bool()
    val addra = in UInt(log2Up(cRamD) bits)
    val addrb = in UInt(log2Up(cRamD) bits)
    val dina = in Bits(cRamW bits)
    val wea = in Bool()
    val enb = in Bool()
    val doutb = out Bits(cRamW bits)
  }

  private val ramClockDomain = ClockDomain(
    clock = io.clka,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(ramClockDomain) {
    val ram = Mem(Bits(cRamW bits), wordCount = cRamD)
    val readData = ram.readAsync(io.addrb)
    val ramData = Reg(Bits(cRamW bits)) init (0)

    when(io.enb) {
      ramData := readData
    }

    when(io.wea) {
      ram.write(io.addra, io.dina)
    }

    if (cOutputReg == 1) {
      val doutbReg = Reg(Bits(cRamW bits)) init (0)
      doutbReg := ramData
      io.doutb := doutbReg
    } else {
      io.doutb := ramData
    }
  }
}
