package softmax.axi

import spinal.core._
import spinal.lib._

class AxiRdM(
    val axiIdWidth: Int = 4,
    val axiIdLoadId: Int = 0,
    val axiAddrWidth: Int = 64,
    val axiLenWidth: Int = 8,
    val axiProtWidth: Int = 3,
    val axiQosWidth: Int = 4,
    val axiStrbWidth: Int = 32,
    val axiDataWidth: Int = 128,
    val axiBurstLen: Int = 15,
    val axiBurstAddrLen: Int = 256,
    val axiBurstSize: Int = 4
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
    val M_AXI_rdata = in Bits(axiDataWidth bits)
    val M_AXI_rid = in UInt(axiIdWidth bits)
    val M_AXI_rlast = in Bool()
    val M_AXI_rready = out Bool()
    val M_AXI_rresp = in UInt(2 bits)
    val M_AXI_rvalid = in Bool()
    val rdata = out Bits(axiDataWidth bits)
    val rdata_valid = out Bool()
    val rdata_ready = in Bool()
    val cmd_addr = in UInt(axiAddrWidth bits)
    val cmd_length = in UInt(20 bits)
    val cmd_valid = in Bool()
    val cmd_done = out Bool()
  }

  private val rdClockDomain = ClockDomain(
    clock = io.clk,
    reset = io.rstn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(rdClockDomain) {
    val LOAD_IDLE = U(0, 3 bits)
    val LOAD_ADDR = U(1, 3 bits)
    val LOAD_FIRST = U(2, 3 bits)
    val LOAD_MIDDLE = U(3, 3 bits)
    val LOAD_LAST = U(4, 3 bits)

    val burstCycles = Reg(UInt(12 bits))
    val burstCyclesMinus2 = Reg(UInt(12 bits))
    val burstCyclesMinus1 = Reg(UInt(12 bits))
    val burstCycleCnt = Reg(UInt(12 bits))

    val cmdAddrR = Reg(UInt(axiAddrWidth bits))
    val cmdLengthR = Reg(UInt(20 bits))
    val alignedTotalBurstLen = Reg(UInt(16 bits))
    val alignedTotalBurstLenAdderA = Reg(UInt(16 bits))
    val alignedTotalBurstLenAdderB = Reg(UInt(16 bits))
    val firstBurstLengthA = Reg(UInt(16 bits))
    val firstBurstLengthB = Reg(UInt(16 bits))
    val firstBurstLength = Reg(UInt(4 bits))
    val lastBurstLength = Reg(UInt(4 bits))
    val firstBurstEn = Reg(Bool())
    val middleBurstEn = Reg(Bool())
    val lastBurstEn = Reg(Bool())

    val cmdValidD0 = Reg(Bool())
    val cmdValidD1 = Reg(Bool())
    val cmdValidD2 = Reg(Bool())
    val cmdValidPos = Reg(Bool())

    val loadAxiState = Reg(UInt(3 bits))
    val araddrR = Reg(UInt(axiAddrWidth bits)) init(0)
    val aridR = Reg(UInt(axiIdWidth bits)) init(0)
    val arlenR = Reg(UInt(axiLenWidth bits)) init(0)
    val arsizeR = Reg(UInt(3 bits)) init(0)
    val arvalidR = Reg(Bool()) init(False)
    val cmdDoneR = Reg(Bool()) init(False)

    io.M_AXI_araddr := araddrR
    io.M_AXI_arid := aridR
    io.M_AXI_arlen := arlenR
    io.M_AXI_arsize := arsizeR
    io.M_AXI_arvalid := arvalidR
    io.cmd_done := cmdDoneR

    cmdValidD0 := io.cmd_valid
    cmdValidD1 := cmdValidD0
    cmdValidD2 := cmdValidD1
    cmdValidPos := cmdValidD1 && !cmdValidD2

    alignedTotalBurstLenAdderA := (cmdLengthR >> 2).resized
    alignedTotalBurstLenAdderB := ((U(256, 9 bits) - cmdAddrR(7 downto 0).resize(9)) >> 4).resized
    firstBurstLengthA := (cmdLengthR << 2).resized
    firstBurstLengthB := (U(256, 9 bits) - cmdAddrR(7 downto 0).resize(9)).resized
    alignedTotalBurstLen := (alignedTotalBurstLenAdderA - alignedTotalBurstLenAdderB).resized
    burstCyclesMinus2 := burstCycles - 2
    burstCyclesMinus1 := burstCycles - 1

    when(io.cmd_valid) {
      cmdAddrR := io.cmd_addr
      when(io.cmd_length(3 downto 2) =/= 0) {
        cmdLengthR := ((io.cmd_length >> 2) - io.cmd_length(3 downto 2).resized + 4).resized
      } otherwise {
        cmdLengthR := (io.cmd_length >> 2).resized
      }
    }

    io.M_AXI_arburst := U"01"
    io.M_AXI_arcache := U"0000"
    io.M_AXI_arlock := U(0, 1 bits)
    io.M_AXI_arprot := U"000"
    io.M_AXI_arqos := U"0000"
    io.rdata := io.M_AXI_rdata
    io.rdata_valid := io.M_AXI_rvalid
    io.M_AXI_rready := io.rdata_ready

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
            lastBurstLength := (alignedTotalBurstLen & U"0000000000001111").resized
            when((alignedTotalBurstLen & U"0000000000001111") =/= 0) {
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
          burstCycles := (cmdLengthR >> 6).resized
        }
      }
    }

    when(!io.rstn) {
      loadAxiState := LOAD_IDLE
      arvalidR := False
      burstCycleCnt := 0
      cmdDoneR := False
      araddrR := 0
      aridR := 0
      arlenR := 0
      arsizeR := 0
    } otherwise {
      cmdDoneR := False
      switch(loadAxiState) {
        is(LOAD_IDLE) {
          burstCycleCnt := 0
          arvalidR := False
          when(cmdValidPos) {
            loadAxiState := LOAD_ADDR
          }
        }
        is(LOAD_ADDR) {
          araddrR := cmdAddrR
          aridR := U(axiIdLoadId, axiIdWidth bits)

          when(firstBurstEn) {
            arlenR := (firstBurstLength - 1).resized
            loadAxiState := LOAD_FIRST
          } elsewhen(middleBurstEn) {
            arlenR := U(axiBurstLen, axiLenWidth bits)
            loadAxiState := LOAD_MIDDLE
          } otherwise {
            arlenR := (lastBurstLength - 1).resized
            loadAxiState := LOAD_LAST
          }

          arsizeR := U(axiBurstSize, 3 bits)
          arvalidR := True
        }
        is(LOAD_FIRST) {
          when(io.M_AXI_arready) {
            burstCycleCnt := burstCycleCnt + 1
            when(middleBurstEn) {
              araddrR := (((cmdAddrR >> 8) + 1) << 8).resized
              arlenR := U(axiBurstLen, axiLenWidth bits)
              loadAxiState := LOAD_MIDDLE
            } elsewhen(lastBurstEn) {
              araddrR := (((cmdAddrR >> 8) + 1) << 8).resized
              arlenR := (lastBurstLength - 1).resized
              loadAxiState := LOAD_LAST
            } otherwise {
              loadAxiState := LOAD_IDLE
              cmdDoneR := True
              arvalidR := False
            }
          }
        }
        is(LOAD_MIDDLE) {
          when(io.M_AXI_arready) {
            burstCycleCnt := burstCycleCnt + 1
            when(lastBurstEn) {
              when(burstCycleCnt === burstCyclesMinus2) {
                loadAxiState := LOAD_LAST
                araddrR := (araddrR + axiBurstAddrLen).resized
                arlenR := (lastBurstLength - 1).resized
              } otherwise {
                araddrR := (araddrR + axiBurstAddrLen).resized
              }
            } otherwise {
              when(burstCycleCnt === burstCyclesMinus1) {
                loadAxiState := LOAD_IDLE
                cmdDoneR := True
                arvalidR := False
              } otherwise {
                araddrR := (araddrR + axiBurstAddrLen).resized
              }
            }
          }
        }
        is(LOAD_LAST) {
          when(io.M_AXI_arready) {
            burstCycleCnt := burstCycleCnt + 1
            loadAxiState := LOAD_IDLE
            cmdDoneR := True
            arvalidR := False
          }
        }
      }
    }
  }
}
