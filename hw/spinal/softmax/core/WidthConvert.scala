package softmax.core

import spinal.core._

class WidthConvert extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
    val data_i = in Bits(128 bits)
    val data_i_valid = in Bool()
    val data_i_ready = out Bool()
    val data_o = out Bits(32 bits)
    val data_o_valid = out Bool()
    val data_o_ready = in Bool()
  }

  private val widthClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(widthClockDomain) {
    val rdCnt = Reg(UInt(2 bits)) init(0)
    val bufRdEn = Bool()

    val buf0 = new ResultBuf(
      bufWidth = 128,
      bufDepth = 4,
      progFullThresh = 3,
      cRamS = 0
    )
    buf0.io.clk := io.clk
    buf0.io.rstn := io.rstn
    buf0.io.wr_en := io.data_i_valid && buf0.io.rdy
    buf0.io.din := io.data_i
    buf0.io.rd_en := bufRdEn

    when(!io.rstn) {
      rdCnt := 0
    } elsewhen(io.data_o_ready && buf0.io.valid) {
      rdCnt := rdCnt + 1
    }

    bufRdEn := (rdCnt === 3) && io.data_o_ready

    switch(rdCnt) {
      is(U(0, 2 bits)) {
        io.data_o := buf0.io.dout(31 downto 0)
      }
      is(U(1, 2 bits)) {
        io.data_o := buf0.io.dout(63 downto 32)
      }
      is(U(2, 2 bits)) {
        io.data_o := buf0.io.dout(95 downto 64)
      }
      default {
        io.data_o := buf0.io.dout(127 downto 96)
      }
    }

    io.data_o_valid := buf0.io.valid
    io.data_i_ready := buf0.io.rdy
  }
}
