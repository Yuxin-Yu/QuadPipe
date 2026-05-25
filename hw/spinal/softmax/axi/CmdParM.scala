package softmax.axi

import spinal.core._

class CmdParM(
    val axiAddrWidth: Int = 64
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
    val cmd_x_len_i = in UInt(12 bits)
    val cmd_y_len_i = in UInt(16 bits)
    val cmd_valid_i = in Bool()
    val cmd_src_addr_i = in UInt(axiAddrWidth bits)
    val cmd_dst_addr_i = in UInt(axiAddrWidth bits)
    val cmd_done_clr_i = in Bool()
    val cmd_scale_i = in UInt(5 bits)
    val cmd_offset_i = in Bits(32 bits)
    val cmd_done_o = out Bool()
    val total_len_o = out UInt(20 bits)
    val cmd_x_len_o = out UInt(12 bits)
    val cmd_y_len_o = out UInt(16 bits)
    val cmd_valid_o = out Bool()
    val cmd_done_i = in Bool()
    val cmd_src_addr_o = out UInt(axiAddrWidth bits)
    val cmd_dst_addr_o = out UInt(axiAddrWidth bits)
    val cmd_scale_o = out UInt(5 bits)
    val cmd_offset_o = out Bits(32 bits)
  }

  private val cmdClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(cmdClockDomain) {
    val CMD_IDLE = U(0, 1 bits)
    val CMD_VLD = U(1, 1 bits)

    val cmdState = Reg(UInt(1 bits)) init(CMD_IDLE)
    val cmdVldPos = Reg(Bool()) init(False)
    val cmdVldD = Reg(UInt(4 bits)) init(0)
    val cmdDoneClrD = Reg(UInt(4 bits)) init(0)
    val cmdDoneClrPos = Reg(Bool()) init(False)
    val totalLenO = Reg(UInt(20 bits)) init(0)
    val cmdXLenO = Reg(UInt(12 bits)) init(0)
    val cmdYLenO = Reg(UInt(16 bits)) init(0)
    val cmdValidO = Reg(Bool()) init(False)
    val cmdSrcAddrO = Reg(UInt(axiAddrWidth bits)) init(0)
    val cmdDstAddrO = Reg(UInt(axiAddrWidth bits)) init(0)
    val cmdScaleO = Reg(UInt(5 bits)) init(0)
    val cmdOffsetO = Reg(Bits(32 bits)) init(0)
    val cmdDoneO = Reg(Bool()) init(False)

    io.total_len_o := totalLenO
    io.cmd_x_len_o := cmdXLenO
    io.cmd_y_len_o := cmdYLenO
    io.cmd_valid_o := cmdValidO
    io.cmd_src_addr_o := cmdSrcAddrO
    io.cmd_dst_addr_o := cmdDstAddrO
    io.cmd_scale_o := cmdScaleO
    io.cmd_offset_o := cmdOffsetO
    io.cmd_done_o := cmdDoneO

    cmdVldD := (cmdVldD(2 downto 0) ## io.cmd_valid_i).asUInt
    cmdVldPos := cmdVldD(2) && !cmdVldD(3)

    cmdDoneClrD := (cmdDoneClrD(2 downto 0) ## io.cmd_done_clr_i).asUInt
    cmdDoneClrPos := cmdDoneClrD(2) && !cmdDoneClrD(3)

    when(!io.rstn) {
      cmdState := CMD_IDLE
      totalLenO := 0
      cmdXLenO := 0
      cmdYLenO := 0
      cmdValidO := False
      cmdSrcAddrO := 0
      cmdDstAddrO := 0
      cmdScaleO := 0
      cmdOffsetO := 0
    } otherwise {
      cmdValidO := False
      when(cmdVldPos) {
        cmdState := CMD_VLD
        cmdSrcAddrO := io.cmd_src_addr_i
        cmdDstAddrO := io.cmd_dst_addr_i
        cmdYLenO := io.cmd_y_len_i
        cmdXLenO := io.cmd_x_len_i
        cmdScaleO := io.cmd_scale_i
        cmdOffsetO := io.cmd_offset_i

        when(io.cmd_x_len_i(1 downto 0) =/= 0) {
          val roundedBytes = UInt(12 bits)
          roundedBytes := ((((io.cmd_x_len_i >> 2) + U(1, 10 bits)).resized) << 2).resized
          totalLenO := (roundedBytes * io.cmd_y_len_i).resized
        } otherwise {
          totalLenO := (io.cmd_x_len_i * io.cmd_y_len_i).resized
        }

        cmdValidO := True
      }
    }

    when(!io.rstn) {
      cmdDoneO := False
    } elsewhen(cmdDoneClrPos) {
      cmdDoneO := False
    } elsewhen(io.cmd_done_i) {
      cmdDoneO := True
    }
  }
}
