package softmax.core

import spinal.core._

class ResultBuf(val bufWidth: Int, val bufDepth: Int, val progFullThresh: Int, val cRamS: Int = 0) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
    val rd_en = in Bool()
    val wr_en = in Bool()
    val din = in Bits(bufWidth bits)
    val dout = out Bits(bufWidth bits)
    val rdy = out Bool()
    val valid = out Bool()
  }

  private val bufClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(bufClockDomain) {
    val bufAddrWidth = log2Up(bufDepth)
    val wrAddr = Reg(UInt((bufAddrWidth + 1) bits)) init(0)
    val rdAddr = Reg(UInt((bufAddrWidth + 1) bits)) init(0)
    val storage = Vec.fill(bufDepth)(Reg(Bits(bufWidth bits)) init (0))

    when(io.wr_en) {
      storage(wrAddr(bufAddrWidth - 1 downto 0)) := io.din
    }

    when(!io.rstn) {
      wrAddr := 0
    } elsewhen(io.wr_en) {
      wrAddr := wrAddr + 1
    }

    when(!io.rstn) {
      rdAddr := 0
    } elsewhen(io.rd_en && (rdAddr =/= wrAddr)) {
      rdAddr := rdAddr + 1
    }

    io.dout := storage(rdAddr(bufAddrWidth - 1 downto 0))

    val wrCnt = wrAddr - rdAddr
    io.rdy := wrCnt < progFullThresh
    io.valid := wrAddr =/= rdAddr
  }
}
