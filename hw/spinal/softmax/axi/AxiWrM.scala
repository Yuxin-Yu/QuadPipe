package softmax.axi

import spinal.core._

class AxiWrM(
    val axiIdWidth: Int = 4,
    val axiIdSaveId: Int = 0,
    val axiAddrWidth: Int = 64,
    val axiLenWidth: Int = 8,
    val axiProtWidth: Int = 3,
    val axiQosWidth: Int = 4,
    val axiStrbWidth: Int = 32,
    val axiDataWidth: Int = 128,
    val axiBurstLen: Int = 15,
    val axiBurstAddrLen: Int = 256,
    val axiBurstSize: Int = 4,
    val addrWidth: Int = 13,
    val axiOstdThresh: Int = 4
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val rstn = in Bool()
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
    val M_AXI_wid = out UInt(axiIdWidth bits)
    val M_AXI_wdata = out Bits(axiDataWidth bits)
    val M_AXI_wlast = out Bool()
    val M_AXI_wready = in Bool()
    val M_AXI_wstrb = out UInt(axiStrbWidth bits)
    val M_AXI_wvalid = out Bool()
    val wdata = in Bits(axiDataWidth bits)
    val wdata_valid = in Bool()
    val wdata_ready = out Bool()
    val cmd_addr = in UInt(axiAddrWidth bits)
    val cmd_length = in UInt(20 bits)
    val cmd_valid = in Bool()
    val cmd_done = out Bool()
  }

  private val wrClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(wrClockDomain) {
    val SAVE_IDLE = U(0, 3 bits)
    val SAVE_ADDR = U(1, 3 bits)
    val SAVE_FIRST = U(2, 3 bits)
    val SAVE_MIDDLE = U(3, 3 bits)
    val SAVE_LAST = U(4, 3 bits)

    val burstCycleCnt = Reg(UInt(16 bits))
    val burstCycles = Reg(UInt(16 bits))
    val burstCyclesMinus2 = Reg(UInt(16 bits))
    val burstCyclesMinus1 = Reg(UInt(16 bits))
    val saveAxiState = Reg(UInt(3 bits))

    val cmdAddrR = Reg(UInt(axiAddrWidth bits))
    val cmdLengthR = Reg(UInt(20 bits))
    val cmdLengthCmp = Reg(UInt(20 bits))
    val alignedTotalBurstLen = Reg(UInt(18 bits))
    val alignedTotalBurstLenAdderA = Reg(UInt(18 bits))
    val alignedTotalBurstLenAdderB = Reg(UInt(18 bits))
    val firstBurstLengthA = Reg(UInt(18 bits))
    val firstBurstLengthB = Reg(UInt(18 bits))
    val firstBurstLength = Reg(UInt(4 bits))
    val lastBurstLength = Reg(UInt(4 bits))
    val firstBurstEn = Reg(Bool())
    val middleBurstEn = Reg(Bool())
    val lastBurstEn = Reg(Bool())

    val cmdValidD0 = Reg(Bool())
    val cmdValidD1 = Reg(Bool())
    val cmdValidD2 = Reg(Bool())
    val cmdValidPos = Reg(Bool()) init(False)

    val outstandingReady = Reg(Bool())
    val saveDataCnt = Reg(UInt(20 bits))
    val respCnt = Reg(UInt(20 bits))
    val wlastCnt = Reg(UInt(4 bits))
    val awaddrR = Reg(UInt(axiAddrWidth bits)) init(0)
    val awidR = Reg(UInt(axiIdWidth bits)) init(0)
    val awlenR = Reg(UInt(axiLenWidth bits)) init(0)
    val awsizeR = Reg(UInt(3 bits)) init(0)
    val awvalidR = Reg(Bool()) init(False)
    val cmdDoneR = Reg(Bool()) init(False)

    io.M_AXI_awaddr := awaddrR
    io.M_AXI_awid := awidR
    io.M_AXI_awlen := awlenR
    io.M_AXI_awsize := awsizeR
    io.M_AXI_awvalid := awvalidR
    io.cmd_done := cmdDoneR

    cmdValidD0 := io.cmd_valid
    cmdValidD1 := cmdValidD0
    cmdValidD2 := cmdValidD1
    cmdValidPos := cmdValidD1 && !cmdValidD2

    cmdAddrR := io.cmd_addr
    cmdLengthR := io.cmd_length
    cmdLengthCmp := ((cmdLengthR >> 2) - 1).resized
    alignedTotalBurstLenAdderA := (cmdLengthR >> 2).resized
    alignedTotalBurstLenAdderB := ((U(256, 9 bits) - cmdAddrR(7 downto 0).resize(9)) >> 4).resized
    firstBurstLengthA := (cmdLengthR << 2).resized
    firstBurstLengthB := (U(256, 9 bits) - cmdAddrR(7 downto 0).resize(9)).resized
    alignedTotalBurstLen := (alignedTotalBurstLenAdderA - alignedTotalBurstLenAdderB).resized
    burstCyclesMinus2 := burstCycles - 2
    burstCyclesMinus1 := burstCycles - 1

    io.M_AXI_awburst := U"01"
    io.M_AXI_awcache := U"0000"
    io.M_AXI_awlock := U(0, 1 bits)
    io.M_AXI_awprot := U"000"
    io.M_AXI_awqos := U"0000"
    io.M_AXI_wstrb := U((BigInt(1) << axiStrbWidth) - 1, axiStrbWidth bits)
    io.M_AXI_bready := True
    io.M_AXI_wid := U(axiIdSaveId, axiIdWidth bits)
    io.M_AXI_wdata := io.wdata
    io.M_AXI_wvalid := io.wdata_valid
    io.wdata_ready := io.M_AXI_wready

    when(cmdValidPos) {
      when(cmdAddrR(7 downto 0) =/= 0) {
        firstBurstEn := True
        when(firstBurstLengthA <= firstBurstLengthB) {
          burstCycles := 1
          firstBurstLength := (cmdLengthR >> 2).resized
          middleBurstEn := False
          lastBurstEn := False
        } otherwise {
          firstBurstLength := (firstBurstLengthB >> 4).resized
          when(alignedTotalBurstLen >= 16) {
            middleBurstEn := True
            lastBurstLength := alignedTotalBurstLen(3 downto 0).resized
            when(alignedTotalBurstLen(3 downto 0) =/= 0) {
              lastBurstEn := True
              burstCycles := ((alignedTotalBurstLen >> 4) + 2).resized
            } otherwise {
              lastBurstEn := False
              burstCycles := ((alignedTotalBurstLen >> 4) + 1).resized
            }
          } otherwise {
            middleBurstEn := False
            lastBurstEn := True
            lastBurstLength := alignedTotalBurstLen.resized
            burstCycles := 2
          }
        }
      } otherwise {
        firstBurstEn := False
        firstBurstLength := 0
        when((cmdLengthR >> 2) >= 16) {
          middleBurstEn := True
          lastBurstLength := cmdLengthR(5 downto 2).resized
          when(cmdLengthR(5 downto 2) =/= 0) {
            lastBurstEn := True
            burstCycles := ((cmdLengthR >> 6) + 1).resized
          } otherwise {
            lastBurstEn := False
            burstCycles := (cmdLengthR >> 6).resized
          }
        } otherwise {
          middleBurstEn := False
          lastBurstEn := True
          lastBurstLength := cmdLengthR(5 downto 2).resized
          burstCycles := 1
        }
      }
    }

    when(!io.rstn) {
      saveAxiState := SAVE_IDLE
      awvalidR := False
      burstCycleCnt := 0
    } otherwise {
      switch(saveAxiState) {
        is(SAVE_IDLE) {
          burstCycleCnt := 0
          awvalidR := False
          when(cmdValidPos) {
            saveAxiState := SAVE_ADDR
          }
        }
        is(SAVE_ADDR) {
          awaddrR := cmdAddrR
          awidR := U(axiIdSaveId, axiIdWidth bits)

          when(firstBurstEn) {
            awlenR := (firstBurstLength - 1).resized
            saveAxiState := SAVE_FIRST
          } elsewhen(middleBurstEn) {
            awlenR := U(axiBurstLen, axiLenWidth bits)
            saveAxiState := SAVE_MIDDLE
          } otherwise {
            awlenR := (lastBurstLength - 1).resized
            saveAxiState := SAVE_LAST
          }

          awsizeR := U(axiBurstSize, 3 bits)
          awvalidR := True
        }
        is(SAVE_FIRST) {
          when(io.M_AXI_awready && awvalidR) {
            burstCycleCnt := burstCycleCnt + 1
            when(middleBurstEn) {
              awaddrR := (((cmdAddrR >> 8) + 1) << 8).resized
              awlenR := U(axiBurstLen, axiLenWidth bits)
              saveAxiState := SAVE_MIDDLE
              when(outstandingReady) {
                awvalidR := True
              } otherwise {
                awvalidR := False
              }
            } elsewhen(lastBurstEn) {
              awaddrR := (((cmdAddrR >> 8) + 1) << 8).resized
              awlenR := (lastBurstLength - 1).resized
              saveAxiState := SAVE_LAST
              when(outstandingReady) {
                awvalidR := True
              } otherwise {
                awvalidR := False
              }
            } otherwise {
              saveAxiState := SAVE_IDLE
              awvalidR := False
            }
          }
        }
        is(SAVE_MIDDLE) {
          when(io.M_AXI_awready && awvalidR) {
            burstCycleCnt := burstCycleCnt + 1
            when(lastBurstEn) {
              when(burstCycleCnt === burstCyclesMinus2) {
                saveAxiState := SAVE_LAST
                awaddrR := (awaddrR + axiBurstAddrLen).resized
                awlenR := (lastBurstLength - 1).resized
                when(outstandingReady) {
                  awvalidR := True
                } otherwise {
                  awvalidR := False
                }
              } otherwise {
                awaddrR := (awaddrR + axiBurstAddrLen).resized
                when(outstandingReady) {
                  awvalidR := True
                } otherwise {
                  awvalidR := False
                }
              }
            } otherwise {
              when(burstCycleCnt === burstCyclesMinus1) {
                saveAxiState := SAVE_IDLE
                awvalidR := False
              } otherwise {
                awaddrR := (awaddrR + axiBurstAddrLen).resized
                when(outstandingReady) {
                  awvalidR := True
                } otherwise {
                  awvalidR := False
                }
              }
            }
          } elsewhen(outstandingReady) {
            awvalidR := True
          }
        }
        is(SAVE_LAST) {
          when(io.M_AXI_awready && awvalidR) {
            burstCycleCnt := burstCycleCnt + 1
            saveAxiState := SAVE_IDLE
            awvalidR := False
          } elsewhen(outstandingReady) {
            awvalidR := True
          }
        }
      }
    }

    when(!io.rstn) {
      saveDataCnt := 0
    } elsewhen(cmdValidPos) {
      saveDataCnt := 0
    } elsewhen(io.M_AXI_wvalid && io.M_AXI_wready) {
      saveDataCnt := saveDataCnt + 1
    }

    wlastCnt := (firstBurstLength - 1).resized
    io.M_AXI_wlast := (saveDataCnt(3 downto 0) === wlastCnt) || (saveDataCnt === cmdLengthCmp)

    when(!io.rstn) {
      respCnt := 0
    } elsewhen(cmdValidPos) {
      respCnt := 0
    } elsewhen(io.M_AXI_bready && io.M_AXI_bvalid && (io.M_AXI_bid === axiIdSaveId)) {
      respCnt := respCnt + 1
    }

    outstandingReady := (burstCycleCnt - respCnt) < (axiOstdThresh - 2)

    when(!io.rstn) {
      cmdDoneR := False
    } elsewhen(io.M_AXI_bready && io.M_AXI_bvalid && (io.M_AXI_bid === axiIdSaveId)) {
      when(respCnt === (burstCycles - 1)) {
        cmdDoneR := True
      } otherwise {
        cmdDoneR := False
      }
    } otherwise {
      cmdDoneR := False
    }
  }
}
