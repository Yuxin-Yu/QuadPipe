package softmax.core

import spinal.core._
import softmax.fp._

class SoftmaxCalcM(val ramDepth: Int = 1024, val sfmDsp48Ver: String = "DSP48E2") extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
    val cmd_x_len = in UInt(10 bits)
    val cmd_y_len = in UInt(16 bits)
    val cmd_valid = in Bool()
    val cmd_done = out Bool()
    val scale = in UInt(5 bits)
    val offset = in Bits(32 bits)
    val data_i = in Bits(32 bits)
    val data_i_valid = in Bool()
    val data_i_ready = out Bool()
    val data_o = out Bits(128 bits)
    val data_o_valid = out Bool()
    val data_o_ready = in Bool()
    val ram_wr_en = out Bool()
    val ram_wr_data = out Bits(128 bits)
    val ram_wr_addr = out UInt(11 bits)
    val ram_rd_data = in Bits(128 bits)
    val ram_rd_addr = out UInt(11 bits)
    val dbg_sum_buf = out Bits(32 bits)
    val dbg_sum_buf_d = out Bits(32 bits)
    val dbg_sum_buf_valid = out Bool()
    val dbg_sum_buf_dout = out Bits(32 bits)
    val dbg_acc_result = out Bits(32 bits)
    val dbg_acc_result_valid = out Bool()
    val dbg_acc_last = out Bool()
    val dbg_vector_last = out Bool()
    val dbg_fp_data = out Bits(128 bits)
    val dbg_exp_in_data = out Bits(128 bits)
    val dbg_exp_result_data = out Bits(128 bits)
    val dbg_add_result_data = out Bits(64 bits)
    val dbg_acc_data_in = out Bits(32 bits)
    val dbg_acc_data_in_valid = out Bool()
    val dbg_div_result_data = out Bits(128 bits)
    val dbg_ram_wr_data = out Bits(128 bits)
    val dbg_ram_wr_en = out Bool()
    val dbg_ram_rd_data = out Bits(128 bits)
  }

  private val calcClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(calcClockDomain) {
    val NEG_INF = B"xFF800000"
    val CALC_IDLE = U(0, 2 bits)
    val CALC_FEED = U(1, 2 bits)

    val ramAddrWidth = log2Up(ramDepth)

    val calcState = Reg(UInt(2 bits)) init(CALC_IDLE)
    val cmdValidD = Reg(Bool()) init(False)
    val dimXLen = Reg(UInt(10 bits)) init(0)
    val dimYLen = Reg(UInt(16 bits)) init(0)
    val dimXCnt = Reg(UInt(10 bits)) init(0)
    val dimYCnt = Reg(UInt(16 bits)) init(0)
    val dimXReg = Reg(UInt(2 bits)) init(0)
    val divCnt = Reg(UInt(10 bits)) init(0)
    val dataID = Reg(Bits(32 bits)) init(0)
    val fixedWords = Vec(Reg(Bits(32 bits)) init(0), 4)
    val feedEn = Reg(Bool()) init(False)
    val divEn = Reg(Bool()) init(False)
    val divState = Reg(Bool()) init(False)
    val sumRdEn = Reg(Bool()) init(False)
    val sumBuf = Reg(Bits(32 bits)) init(0)
    val sumBufD = Reg(Bits(32 bits)) init(0)
    val vectorLastD = Reg(Bits(53 bits)) init(0)
    val calcEnD = Reg(Bits(2 bits)) init(0)
    val divEnD = Reg(Bits(5 bits)) init(0)
    val expCnt = Reg(UInt(10 bits)) init(0)
    val bufWrAddr = Reg(UInt(ramAddrWidth bits)) init(0)
    val bufRdAddr = Reg(UInt(ramAddrWidth bits)) init(0)

    val cmdValidPos = Reg(Bool()) init(False)
    val calcEn = Bool()
    val vectorLast = Bool()
    val sumBufReady = Bool()
    val sumBufValid = Bool()
    val sumBufDout = Bits(32 bits)
    val ramBufReady = Bool()
    val srcDataValid = Bool()
    val accDataInValid = Bool()
    val addValid = Bool()
    val accResultValid = Bool()
    val accResultData = Bits(32 bits)
    val accDataIn = Bits(32 bits)
    val addResultData = Bits(64 bits)
    val fpValid = Vec(Bool(), 4)
    val fpData = Vec(Bits(32 bits), 4)
    val expInData = Vec(Bits(32 bits), 4)
    val expResultValid = Vec(Bool(), 4)
    val expResultData = Vec(Bits(32 bits), 4)
    val divResultValid = Vec(Bool(), 4)
    val divResultData = Vec(Bits(32 bits), 4)
    val bufDataCnt = Reg(UInt(ramAddrWidth bits)) init(0)
    val ramWrEnR = Reg(Bool()) init(False)
    val ramWrAddrR = Reg(UInt(11 bits)) init(0)
    val ramWrDataR = Reg(Bits(128 bits)) init(0)

    srcDataValid := io.data_i_valid
    dataID := io.data_i
    sumBufD := sumBuf
    cmdValidPos := io.cmd_valid && !cmdValidD
    cmdValidD := io.cmd_valid
    calcEn := feedEn && srcDataValid
    vectorLast := (dimXCnt === (dimXLen - 1)) && calcEn
    vectorLastD := vectorLastD(51 downto 0) ## vectorLast
    calcEnD := calcEnD(0) ## calcEn

    when(dimXLen === 1) {
      divEnD(0) := divEn && sumBufValid
    } otherwise {
      divEnD(0) := divEn
    }
    divEnD(4 downto 1) := divEnD(3 downto 0)

    when(!io.rstn) {
      calcState := CALC_IDLE
      dimXCnt := 0
      dimYCnt := 0
      feedEn := False
    } otherwise {
      switch(calcState) {
        is(CALC_IDLE) {
          feedEn := False
          dimXCnt := 0
          dimYCnt := 0
          when(cmdValidPos) {
            calcState := CALC_FEED
            dimYLen := io.cmd_y_len
            dimXReg := io.cmd_x_len(1 downto 0)
            when(io.cmd_x_len(1 downto 0) =/= 0) {
              dimXLen := ((io.cmd_x_len >> 2) + 1).resized
            } otherwise {
              dimXLen := (io.cmd_x_len >> 2).resized
            }
          }
        }
        is(CALC_FEED) {
          when(srcDataValid && feedEn && (dimXCnt === (dimXLen - 1)) && (dimYCnt === (dimYLen - 1))) {
            feedEn := False
            calcState := CALC_IDLE
          } elsewhen(sumBufReady && ramBufReady) {
            feedEn := True
          } otherwise {
            feedEn := False
          }

          when(srcDataValid && feedEn && (dimXCnt === (dimXLen - 1))) {
            dimXCnt := 0
            dimYCnt := dimYCnt + 1
          } elsewhen(srcDataValid && feedEn) {
            dimXCnt := dimXCnt + 1
          }
        }
      }
    }

    when(!io.rstn) {
      divState := False
      divEn := False
      divCnt := 0
      sumRdEn := False
    } elsewhen(!divState) {
      divCnt := 0
      when(sumBufValid && io.data_o_ready && (dimXLen === 1)) {
        divEn := True
        sumRdEn := True
      } elsewhen(sumBufValid && io.data_o_ready) {
        divState := True
        divEn := True
        sumRdEn := True
      } otherwise {
        divEn := False
        sumRdEn := False
      }
    } otherwise {
      when((divCnt === (dimXLen - 1)) && sumBufValid && io.data_o_ready) {
        divEn := True
        divCnt := 0
        sumRdEn := True
      } elsewhen(divCnt === (dimXLen - 1)) {
        divEn := False
        divState := False
        divCnt := 0
        sumRdEn := False
      } elsewhen(io.data_o_ready) {
        divCnt := divCnt + 1
        divEn := True
        sumRdEn := False
      } otherwise {
        divEn := False
        sumRdEn := False
      }
    }

    when(sumRdEn && sumBufValid) {
      sumBuf := sumBufDout
    }

    accResultValid := vectorLastD(42)

    val accResultBuf = new ResultBuf(
      bufWidth = 32,
      bufDepth = 64,
      progFullThresh = 8,
      cRamS = 0
    )
    accResultBuf.io.clk := io.clk
    accResultBuf.io.rstn := io.rstn
    accResultBuf.io.wr_en := accResultValid
    accResultBuf.io.din := accResultData
    accResultBuf.io.rd_en := sumRdEn
    sumBufReady := accResultBuf.io.rdy
    sumBufDout := accResultBuf.io.dout
    sumBufValid := accResultBuf.io.valid

    when(!io.rstn) {
      expCnt := 0
    } elsewhen((expCnt === (dimXLen - 1)) && fpValid(0)) {
      expCnt := 0
    } elsewhen(fpValid(0)) {
      expCnt := expCnt + 1
    }

    for (i <- 0 until 4) {
      val raw48 = Bits(48 bits)
      val shifted48 = Bits(48 bits)

      when(dataID(i * 8 + 7)) {
        raw48 := B(24 bits, default -> True) ## dataID(i * 8 + 7 downto i * 8) ## B(16 bits, default -> False)
      } otherwise {
        raw48 := B(24 bits, default -> False) ## dataID(i * 8 + 7 downto i * 8) ## B(16 bits, default -> False)
      }

      when(!io.scale(4)) {
        shifted48 := raw48 |>> io.scale.resize(6)
      } otherwise {
        shifted48 := raw48 |<< (U(32, 6 bits) - io.scale.resize(6))
      }

      fixedWords(i) := shifted48(31 downto 0)

      val fpConvertInst = new FltConvert
      fpConvertInst.io.aclk := io.clk
      fpConvertInst.io.s_axis_a_tvalid := calcEnD(1)
      fpConvertInst.io.s_axis_a_tdata := fixedWords(i)
      fpValid(i) := fpConvertInst.io.m_axis_result_tvalid
      fpData(i) := fpConvertInst.io.m_axis_result_tdata

      expInData(i) := fpData(i)
    }

    when(expCnt === (dimXLen - 1)) {
      switch(dimXReg) {
        is(U(1, 2 bits)) {
          expInData(1) := NEG_INF
          expInData(2) := NEG_INF
          expInData(3) := NEG_INF
        }
        is(U(2, 2 bits)) {
          expInData(2) := NEG_INF
          expInData(3) := NEG_INF
        }
        is(U(3, 2 bits)) {
          expInData(3) := NEG_INF
        }
      }
    }

    for (i <- 0 until 4) {
      val fpExpInst = new FltExp(sfmDsp48Ver)
      fpExpInst.io.aclk := io.clk
      fpExpInst.io.s_axis_a_tvalid := fpValid(i)
      fpExpInst.io.s_axis_a_tdata := expInData(i)
      expResultValid(i) := fpExpInst.io.m_axis_result_tvalid
      expResultData(i) := fpExpInst.io.m_axis_result_tdata
    }

    val fpAddInst0 = new FltAdd
    fpAddInst0.io.aclk := io.clk
    fpAddInst0.io.s_axis_a_tvalid := expResultValid(0)
    fpAddInst0.io.s_axis_a_tdata := expResultData(0)
    fpAddInst0.io.s_axis_b_tvalid := expResultValid(0)
    fpAddInst0.io.s_axis_b_tdata := expResultData(1)
    addValid := fpAddInst0.io.m_axis_result_tvalid
    addResultData(31 downto 0) := fpAddInst0.io.m_axis_result_tdata

    val fpAddInst1 = new FltAdd
    fpAddInst1.io.aclk := io.clk
    fpAddInst1.io.s_axis_a_tvalid := expResultValid(0)
    fpAddInst1.io.s_axis_a_tdata := expResultData(2)
    fpAddInst1.io.s_axis_b_tvalid := expResultValid(0)
    fpAddInst1.io.s_axis_b_tdata := expResultData(3)
    addResultData(63 downto 32) := fpAddInst1.io.m_axis_result_tdata

    val fpAddInst2 = new FltAdd
    fpAddInst2.io.aclk := io.clk
    fpAddInst2.io.s_axis_a_tvalid := addValid
    fpAddInst2.io.s_axis_a_tdata := addResultData(31 downto 0)
    fpAddInst2.io.s_axis_b_tvalid := addValid
    fpAddInst2.io.s_axis_b_tdata := addResultData(63 downto 32)
    accDataInValid := fpAddInst2.io.m_axis_result_tvalid
    accDataIn := fpAddInst2.io.m_axis_result_tdata

    val fpAccInst = new FltAcc(sfmDsp48Ver)
    fpAccInst.io.aclk := io.clk
    fpAccInst.io.aresetn := io.rstn
    fpAccInst.io.s_axis_a_tvalid := accDataInValid
    fpAccInst.io.s_axis_a_tdata := accDataIn
    fpAccInst.io.s_axis_a_tlast := vectorLastD(34)
    accResultData := fpAccInst.io.m_axis_result_tdata

    for (i <- 0 until 4) {
      val fpDivInst = new FltDiv
      fpDivInst.io.aclk := io.clk
      fpDivInst.io.s_axis_a_tdata := io.ram_rd_data(i * 32 + 31 downto i * 32)
      fpDivInst.io.s_axis_a_tvalid := divEnD(1)
      fpDivInst.io.s_axis_b_tdata := sumBufD
      fpDivInst.io.s_axis_b_tvalid := divEnD(1)
      divResultData(i) := fpDivInst.io.m_axis_result_tdata
      divResultValid(i) := fpDivInst.io.m_axis_result_tvalid
    }

    when(!io.rstn) {
      bufWrAddr := 0
    } elsewhen(expResultValid(0)) {
      bufWrAddr := bufWrAddr + 1
    }

    when(!io.rstn) {
      bufRdAddr := 0
    } otherwise {
      when(dimXLen === 1) {
        when(divEn && sumBufValid) {
          bufRdAddr := bufRdAddr + 1
        }
      } otherwise {
        when(divEn) {
          bufRdAddr := bufRdAddr + 1
        }
      }
    }

    when(!io.rstn) {
      bufDataCnt := 0
    } otherwise {
      bufDataCnt := bufWrAddr - bufRdAddr
    }

    ramBufReady := bufDataCnt < U(ramDepth - 64, ramAddrWidth bits)

    ramWrEnR := expResultValid(0)
    ramWrAddrR := bufWrAddr.resized
    ramWrDataR := expResultData(3) ## expResultData(2) ## expResultData(1) ## expResultData(0)

    io.ram_wr_en := ramWrEnR
    io.ram_wr_addr := ramWrAddrR
    io.ram_wr_data := ramWrDataR
    io.ram_rd_addr := bufRdAddr.resized
    io.data_i_ready := feedEn
    io.data_o_valid := divResultValid(0)
    io.data_o := divResultData(3) ## divResultData(2) ## divResultData(1) ## divResultData(0)
    io.cmd_done := False
    io.dbg_sum_buf := sumBuf
    io.dbg_sum_buf_d := sumBufD
    io.dbg_sum_buf_valid := sumBufValid
    io.dbg_sum_buf_dout := sumBufDout
    io.dbg_acc_result := accResultData
    io.dbg_acc_result_valid := accResultValid
    io.dbg_acc_last := vectorLastD(34)
    io.dbg_vector_last := vectorLast
    io.dbg_fp_data := fpData.asBits
    io.dbg_exp_in_data := expInData.asBits
    io.dbg_exp_result_data := expResultData.asBits
    io.dbg_add_result_data := addResultData
    io.dbg_acc_data_in := accDataIn
    io.dbg_acc_data_in_valid := accDataInValid
    io.dbg_div_result_data := divResultData.asBits
    io.dbg_ram_wr_data := ramWrDataR
    io.dbg_ram_wr_en := ramWrEnR
    io.dbg_ram_rd_data := io.ram_rd_data
  }
}
