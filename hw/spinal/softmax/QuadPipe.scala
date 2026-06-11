package softmax

import spinal.core._
import softmax.axi._
import softmax.core._

class QuadPipe(
    val axiIdWidth: Int = 6,
    val axiIdLoadId: Int = 0,
    val axiIdSaveId: Int = 0,
    val axiAddrWidth: Int = 32,
    val axiLenWidth: Int = 8,
    val axiProtWidth: Int = 3,
    val axiQosWidth: Int = 4,
    val axiStrbWidth: Int = 16,
    val axiDataWidth: Int = 128,
    val sfmDsp48Ver: String = "DSP48E2"
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
    val M_AXI_araddr = out UInt(axiAddrWidth bits)
    val M_AXI_arburst = out UInt(2 bits)
    val M_AXI_arcache = out UInt(4 bits)
    val M_AXI_arid = out UInt(axiIdWidth bits)
    val M_AXI_arlen = out UInt(axiLenWidth bits)
    val M_AXI_arlock = out UInt(1 bits)
    val M_AXI_arprot = out UInt(axiProtWidth bits)
    val M_AXI_arqos = out UInt(axiQosWidth bits)
    val M_AXI_arready = in Bool()
    val M_AXI_arsize = out UInt(3 bits)
    val M_AXI_arvalid = out Bool()
    val M_AXI_awaddr = out UInt(axiAddrWidth bits)
    val M_AXI_awburst = out UInt(2 bits)
    val M_AXI_awcache = out UInt(4 bits)
    val M_AXI_awid = out UInt(axiIdWidth bits)
    val M_AXI_awlen = out UInt(axiLenWidth bits)
    val M_AXI_awlock = out UInt(1 bits)
    val M_AXI_awprot = out UInt(axiProtWidth bits)
    val M_AXI_awqos = out UInt(axiQosWidth bits)
    val M_AXI_awready = in Bool()
    val M_AXI_awsize = out UInt(3 bits)
    val M_AXI_awvalid = out Bool()
    val M_AXI_bid = in UInt(axiIdWidth bits)
    val M_AXI_bready = out Bool()
    val M_AXI_bresp = in UInt(2 bits)
    val M_AXI_bvalid = in Bool()
    val M_AXI_rdata = in Bits(128 bits)
    val M_AXI_rid = in UInt(axiIdWidth bits)
    val M_AXI_rlast = in Bool()
    val M_AXI_rready = out Bool()
    val M_AXI_rresp = in UInt(2 bits)
    val M_AXI_rvalid = in Bool()
    val M_AXI_wid = out UInt(axiIdWidth bits)
    val M_AXI_wdata = out Bits(128 bits)
    val M_AXI_wlast = out Bool()
    val M_AXI_wready = in Bool()
    val M_AXI_wstrb = out UInt(16 bits)
    val M_AXI_wvalid = out Bool()
    val reg_sm_cmd_x_len = in UInt(12 bits)
    val reg_sm_cmd_y_len = in UInt(16 bits)
    val reg_sm_cmd_valid = in Bool()
    val reg_sm_cmd_done = out Bool()
    val reg_sm_cmd_src_addr = in UInt(axiAddrWidth bits)
    val reg_sm_cmd_dst_addr = in UInt(axiAddrWidth bits)
    val reg_sm_cmd_done_clr = in Bool()
    val reg_sm_cmd_scale = in UInt(5 bits)
    val reg_sm_cmd_offset = in Bits(32 bits)
  }


  val rdata = Bits(axiDataWidth bits)
  val rdataValid = Bool()
  val rdataReady = Bool()
  val rdataInt = Bits(axiDataWidth/4 bits)
  val rdataValidInt = Bool()
  val rdataReadyInt = Bool()
  val resultO = Bits(axiDataWidth bits)
  val resultOValid = Bool()
    val resultOReady = Bool()
    val rbufDout = Bits(axiDataWidth bits)
    val rbufRdEn = Bool()
    val rbufValid = Bool()
    val rsValid = Bool()
    val rsReady = Bool()
    val wdataReady = Bool()
    val rsData = Bits(axiDataWidth bits)
    val scale = UInt(5 bits)
    val offset = Bits(32 bits)
    val totalLen = UInt(20 bits)
    val cmdXLen = UInt(12 bits)
    val cmdYLen = UInt(16 bits)
    val cmdValid = Bool()
    val cmdSrcAddr = UInt(axiAddrWidth bits)
    val cmdDstAddr = UInt(axiAddrWidth bits)
    val ramWrEn = Bool()
    val ramWrData = Bits(128 bits)
    val ramWrAddr = UInt(12 bits)
    val ramRdData = Bits(128 bits)
    val ramRdAddr = UInt(12 bits)
    val axiWrCmdDone = Bool()


  val cmdParMInst = new CmdParM(axiAddrWidth)
  cmdParMInst.io.clk := io.clk
  cmdParMInst.io.rstn := io.rstn
  cmdParMInst.io.cmd_x_len_i := io.reg_sm_cmd_x_len
  cmdParMInst.io.cmd_y_len_i := io.reg_sm_cmd_y_len
  cmdParMInst.io.cmd_valid_i := io.reg_sm_cmd_valid
  cmdParMInst.io.cmd_src_addr_i := io.reg_sm_cmd_src_addr
  cmdParMInst.io.cmd_dst_addr_i := io.reg_sm_cmd_dst_addr
  cmdParMInst.io.cmd_done_clr_i := io.reg_sm_cmd_done_clr
  cmdParMInst.io.cmd_scale_i := io.reg_sm_cmd_scale
  cmdParMInst.io.cmd_offset_i := io.reg_sm_cmd_offset
  io.reg_sm_cmd_done := cmdParMInst.io.cmd_done_o
  totalLen := cmdParMInst.io.total_len_o
  cmdXLen := cmdParMInst.io.cmd_x_len_o
  cmdYLen := cmdParMInst.io.cmd_y_len_o.resized
  cmdValid := cmdParMInst.io.cmd_valid_o
  cmdSrcAddr := cmdParMInst.io.cmd_src_addr_o
  cmdDstAddr := cmdParMInst.io.cmd_dst_addr_o
  scale := cmdParMInst.io.cmd_scale_o
  offset := cmdParMInst.io.cmd_offset_o


  val axiRdMInst = new AxiRdM(
    axiIdWidth = axiIdWidth,
    axiIdLoadId = axiIdLoadId,
    axiAddrWidth = axiAddrWidth,
    axiLenWidth = axiLenWidth,
    axiProtWidth = axiProtWidth,
    axiQosWidth = axiQosWidth,
    axiStrbWidth = axiStrbWidth,
    axiDataWidth = axiDataWidth
  )
  axiRdMInst.io.clk := io.clk
  axiRdMInst.io.rstn := io.rstn
  io.M_AXI_araddr := axiRdMInst.io.M_AXI_araddr
  io.M_AXI_arburst := axiRdMInst.io.M_AXI_arburst
  io.M_AXI_arcache := axiRdMInst.io.M_AXI_arcache
  io.M_AXI_arid := axiRdMInst.io.M_AXI_arid
  io.M_AXI_arlen := axiRdMInst.io.M_AXI_arlen
  io.M_AXI_arlock := axiRdMInst.io.M_AXI_arlock
  io.M_AXI_arprot := axiRdMInst.io.M_AXI_arprot
  io.M_AXI_arqos := axiRdMInst.io.M_AXI_arqos
  axiRdMInst.io.M_AXI_arready := io.M_AXI_arready
  io.M_AXI_arsize := axiRdMInst.io.M_AXI_arsize
  io.M_AXI_arvalid := axiRdMInst.io.M_AXI_arvalid
  axiRdMInst.io.M_AXI_rdata := io.M_AXI_rdata
  axiRdMInst.io.M_AXI_rid := io.M_AXI_rid
  axiRdMInst.io.M_AXI_rlast := io.M_AXI_rlast
  io.M_AXI_rready := axiRdMInst.io.M_AXI_rready
  axiRdMInst.io.M_AXI_rresp := io.M_AXI_rresp
  axiRdMInst.io.M_AXI_rvalid := io.M_AXI_rvalid
  rdata := axiRdMInst.io.rdata
  rdataValid := axiRdMInst.io.rdata_valid
  axiRdMInst.io.rdata_ready := rdataReady
  axiRdMInst.io.cmd_addr := cmdSrcAddr
  axiRdMInst.io.cmd_length := totalLen
  axiRdMInst.io.cmd_valid := cmdValid


  val widthConvertInst = new WidthConvert
  widthConvertInst.io.clk := io.clk
  widthConvertInst.io.rstn := io.rstn && !axiWrCmdDone
  widthConvertInst.io.data_i := rdata
  widthConvertInst.io.data_i_valid := rdataValid
  rdataReady := widthConvertInst.io.data_i_ready
  rdataInt := widthConvertInst.io.data_o
  rdataValidInt := widthConvertInst.io.data_o_valid
  widthConvertInst.io.data_o_ready := rdataReadyInt


  val softmaxCalcMInst = new SoftmaxCalcM(ramDepth = 1024, sfmDsp48Ver = sfmDsp48Ver)
  softmaxCalcMInst.io.clk := io.clk
  softmaxCalcMInst.io.rstn := io.rstn
  softmaxCalcMInst.io.cmd_x_len := cmdXLen(9 downto 0)
  softmaxCalcMInst.io.cmd_y_len := cmdYLen
  softmaxCalcMInst.io.cmd_valid := cmdValid
  softmaxCalcMInst.io.scale := scale
  softmaxCalcMInst.io.offset := offset
  softmaxCalcMInst.io.data_i := rdataInt
  softmaxCalcMInst.io.data_i_valid := rdataValidInt
  rdataReadyInt := softmaxCalcMInst.io.data_i_ready
  resultO := softmaxCalcMInst.io.data_o
  resultOValid := softmaxCalcMInst.io.data_o_valid
  softmaxCalcMInst.io.data_o_ready := resultOReady
  ramWrEn := softmaxCalcMInst.io.ram_wr_en
  ramWrData := softmaxCalcMInst.io.ram_wr_data
  ramWrAddr := softmaxCalcMInst.io.ram_wr_addr.resized
  softmaxCalcMInst.io.ram_rd_data := ramRdData
  ramRdAddr := softmaxCalcMInst.io.ram_rd_addr.resized


  val ramS2p1cSmInst = new RamS2p1cSm(
    cRamS = 1,
    cRamW = 128,
    cRamD = 1024,
    cOutputReg = 1
  )
  ramS2p1cSmInst.io.sleep := False
  ramS2p1cSmInst.io.clka := io.clk
  ramS2p1cSmInst.io.addra := ramWrAddr.resized
  ramS2p1cSmInst.io.addrb := ramRdAddr.resized
  ramS2p1cSmInst.io.dina := ramWrData
  ramS2p1cSmInst.io.wea := ramWrEn
  ramS2p1cSmInst.io.enb := True
  ramRdData := ramS2p1cSmInst.io.doutb


  val resultBufInst = new ResultBuf(
    bufWidth = 128,
    bufDepth = 32,
    progFullThresh = 8,
    cRamS = 0
  )
  resultBufInst.io.clk := io.clk
  resultBufInst.io.rstn := io.rstn
  resultBufInst.io.wr_en := resultOValid
  resultBufInst.io.din := resultO
  resultOReady := resultBufInst.io.rdy
  rbufDout := resultBufInst.io.dout
  resultBufInst.io.rd_en := rbufRdEn
  rbufValid := resultBufInst.io.valid


  val axiRsSmInst = new AxiRsSm(
    cDataWidth = 128,
    cRegConfig = 1
  )
  axiRsSmInst.io.ACLK := io.clk
  axiRsSmInst.io.aresetn := io.rstn
  axiRsSmInst.io.S_PAYLOAD_DATA := rbufDout
  axiRsSmInst.io.S_VALID := rbufValid
  rsReady := axiRsSmInst.io.S_READY
  rsData := axiRsSmInst.io.M_PAYLOAD_DATA
  rsValid := axiRsSmInst.io.M_VALID
  axiRsSmInst.io.M_READY := wdataReady
  rbufRdEn := rbufValid && rsReady


  val axiWrMInst = new AxiWrM(
    axiIdWidth = axiIdWidth,
    axiIdSaveId = axiIdSaveId,
    axiAddrWidth = axiAddrWidth,
    axiLenWidth = axiLenWidth,
    axiProtWidth = axiProtWidth,
    axiQosWidth = axiQosWidth,
    axiStrbWidth = axiStrbWidth,
    axiDataWidth = axiDataWidth
  )
  axiWrMInst.io.clk := io.clk
  axiWrMInst.io.rstn := io.rstn
  io.M_AXI_awaddr := axiWrMInst.io.M_AXI_awaddr
  io.M_AXI_awburst := axiWrMInst.io.M_AXI_awburst
  io.M_AXI_awcache := axiWrMInst.io.M_AXI_awcache
  io.M_AXI_awid := axiWrMInst.io.M_AXI_awid
  io.M_AXI_awlen := axiWrMInst.io.M_AXI_awlen
  io.M_AXI_awlock := axiWrMInst.io.M_AXI_awlock
  io.M_AXI_awprot := axiWrMInst.io.M_AXI_awprot
  io.M_AXI_awqos := axiWrMInst.io.M_AXI_awqos
  axiWrMInst.io.M_AXI_awready := io.M_AXI_awready
  io.M_AXI_awsize := axiWrMInst.io.M_AXI_awsize
  io.M_AXI_awvalid := axiWrMInst.io.M_AXI_awvalid
  axiWrMInst.io.M_AXI_bid := io.M_AXI_bid
  io.M_AXI_bready := axiWrMInst.io.M_AXI_bready
  axiWrMInst.io.M_AXI_bresp := io.M_AXI_bresp
  axiWrMInst.io.M_AXI_bvalid := io.M_AXI_bvalid
  io.M_AXI_wid := axiWrMInst.io.M_AXI_wid
  io.M_AXI_wdata := axiWrMInst.io.M_AXI_wdata
  io.M_AXI_wlast := axiWrMInst.io.M_AXI_wlast
  axiWrMInst.io.M_AXI_wready := io.M_AXI_wready
  io.M_AXI_wstrb := axiWrMInst.io.M_AXI_wstrb
  io.M_AXI_wvalid := axiWrMInst.io.M_AXI_wvalid
  axiWrMInst.io.wdata := rsData
  axiWrMInst.io.wdata_valid := rsValid
  wdataReady := axiWrMInst.io.wdata_ready
  axiWrMInst.io.cmd_addr := cmdDstAddr
  axiWrMInst.io.cmd_length := totalLen
  axiWrMInst.io.cmd_valid := cmdValid
  axiWrCmdDone := axiWrMInst.io.cmd_done
  cmdParMInst.io.cmd_done_i := axiWrCmdDone
}

object QuadPipe {
  def main(args: Array[String]) {
    SpinalConfig(
      targetDirectory = "/home/yyx/riscv/SpinalHDL-Softmax/hw/gen",
      defaultClockDomainFrequency = FixedFrequency(100 MHz),
      oneFilePerComponent = false,
      netlistFileName = "QuadPipe.v"
    ).generateVerilog(new QuadPipe)
  }
}
