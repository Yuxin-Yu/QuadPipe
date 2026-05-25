package softmax.axi

import spinal.core._

class AxiRsSm(
    val cDataWidth: Int = 32,
    val cRegConfig: Int = 1
) extends Component {
  val io = new Bundle {
    val ACLK = in Bool()
    val aresetn = in Bool()
    val S_PAYLOAD_DATA = in Bits(cDataWidth bits)
    val S_VALID = in Bool()
    val S_READY = out Bool()
    val M_PAYLOAD_DATA = out Bits(cDataWidth bits)
    val M_VALID = out Bool()
    val M_READY = in Bool()
  }

  private val rsClockDomain = ClockDomain(
    clock = io.ACLK,
    reset = io.aresetn,
    config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
  )

  private val logic = new ClockingArea(rsClockDomain) {
    if (cRegConfig == 0) {
      io.M_PAYLOAD_DATA := io.S_PAYLOAD_DATA
      io.M_VALID := io.S_VALID
      io.S_READY := io.M_READY
    } else if (cRegConfig == 1) {
      val mPayloadI = Reg(Bits(cDataWidth bits)) init(0)
      val skidBuffer = Reg(Bits(cDataWidth bits)) init(0)
      val sReadyI = Reg(Bool()) init(False)
      val mValidI = Reg(Bool()) init(False)
      val aresetnD = Reg(Bits(2 bits)) init(0)

      io.S_READY := sReadyI
      io.M_VALID := mValidI
      io.M_PAYLOAD_DATA := mPayloadI

      aresetnD := aresetnD(0) ## io.aresetn

      when(!aresetnD(0)) {
        sReadyI := False
      } otherwise {
        sReadyI := io.M_READY || !mValidI || (sReadyI && !io.S_VALID)
      }

      when(!aresetnD(1)) {
        mValidI := False
      } otherwise {
        mValidI := io.S_VALID || !sReadyI || (mValidI && !io.M_READY)
      }

      when(io.M_READY || !mValidI) {
        mPayloadI := Mux(sReadyI, io.S_PAYLOAD_DATA, skidBuffer)
      }

      when(sReadyI) {
        skidBuffer := io.S_PAYLOAD_DATA
      }
    } else if (cRegConfig == 2) {
      val storageData = Reg(Bits(cDataWidth bits)) init(0)
      val mValidI = Reg(Bool()) init(False)
      val aresetnD = Reg(Bool()) init(False)
      val sReadyI = Bool()

      io.S_READY := sReadyI
      io.M_VALID := mValidI
      io.M_PAYLOAD_DATA := storageData

      aresetnD := io.aresetn

      when(io.S_VALID && sReadyI) {
        storageData := io.S_PAYLOAD_DATA
      }

      when(!aresetnD) {
        mValidI := False
      } otherwise {
        when(io.S_VALID) {
          mValidI := True
        } elsewhen(io.M_READY) {
          mValidI := False
        }
      }

      sReadyI := (io.M_READY || !mValidI) && aresetnD
    } else if (cRegConfig == 3) {
      val storageData = Reg(Bits(cDataWidth bits)) init(0)
      val sReadyI = Reg(Bool()) init(False)
      val hasValidStorage = Reg(Bool()) init(False)
      val hasValidStorageI = Bool()
      val aresetnD = Reg(Bits(2 bits)) init(0)

      aresetnD := aresetnD(0) ## io.aresetn

      when(io.S_VALID && sReadyI) {
        storageData := io.S_PAYLOAD_DATA
      }

      io.M_PAYLOAD_DATA := Mux(hasValidStorage, storageData, io.S_PAYLOAD_DATA)

      when(io.S_VALID && sReadyI && !io.M_READY) {
        hasValidStorageI := True
      } elsewhen(hasValidStorage && io.M_READY && (!io.S_VALID || !sReadyI)) {
        hasValidStorageI := False
      } otherwise {
        hasValidStorageI := hasValidStorage
      }

      when(!aresetnD(0)) {
        hasValidStorage := False
      } otherwise {
        hasValidStorage := hasValidStorageI
      }

      when(!aresetnD(0)) {
        sReadyI := False
      } otherwise {
        sReadyI := io.M_READY || !hasValidStorageI
      }

      io.S_READY := sReadyI
      io.M_VALID := (io.S_VALID || hasValidStorage) && aresetnD(1)
    } else {
      io.M_PAYLOAD_DATA := io.S_PAYLOAD_DATA
      io.M_VALID := io.S_VALID
      io.S_READY := io.M_READY
    }
  }
}
