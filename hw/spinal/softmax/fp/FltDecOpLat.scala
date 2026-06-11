





package softmax.fp

import spinal.core._
import softmax.util.FltDelay


class FltDecOpLat(
    val rW: Int = 32,
    val rFw: Int = 24,
    val registered: Int = 1,
    val speed: Int = 1,
    val reducedRange: Int = 0,
    val expAdder: Int = 1,
    val updateFlagsLate: Int = 0,
    val noSr: Int = 0,
    val hasDivideByZero: Int = 0
) extends Component {
  val io = new Bundle {
    val clk                = in Bool()
    val ce                 = in Bool()
    val decOpState         = in Bits(14 bits)
    val flow               = in Bits(4 bits)
    val invalidOpIn        = in Bool()
    val divideByZeroIn     = in Bool()
    val mant               = in Bits(rFw - 1 bits)
    val exp                = in Bits(rW - rFw bits)
    val sign               = in Bool()
    val expInc             = in Bool()

    val result             = out Bits(rW bits)
    val underflow          = out Bool()
    val overflow           = out Bool()
    val divideByZero       = out Bool()
    val invalidOp          = out Bool()
  }

  private val opClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(opClockDomain) {

    val rEw = rW - rFw


    val fltFlowOver         = 0
    val fltFlowUnder        = 1
    val fltFlowAlmostOver   = 2
    val fltFlowJustUnder    = 3

    val fltDecOpStateExpOne        = 0
    val fltDecOpStateExpZero       = 1
    val fltDecOpStateMantMsbOne    = 2
    val fltDecOpStateMantMsbZero   = 3
    val fltDecOpStateMantLsbsOne   = 4
    val fltDecOpStateMantLsbsZero  = 5
    val fltDecOpStateSignOne       = 6
    val fltDecOpStateSignZero      = 7
    val fltDecOpStateMidBitOne     = 8
    val fltDecOpStateMidBitZero    = 9
    val fltDecOpStateMantMsbsOne   = 10
    val fltDecOpStateMantMsbsZero  = 11
    val fltDecOpStateExpLsbOne     = 12
    val fltDecOpStateExpLsbZero    = 13


    val expPreOp = Bits(rEw bits)
    val expOp = Reg(Bits(rEw bits)) init(0)
    val mantOp = Reg(Bits(rFw - 1 bits)) init(0)
    val signOp = Reg(Bool()) init(False)
    val underflowQ = Reg(Bool()) init(False)
    val overflowQ = Reg(Bool()) init(False)
    val invalidOpQ = Reg(Bool()) init(False)


    expPreOp := io.exp


    val delayDivideByZero = new FltDelay(
      width = 1,
      length = registered
    )
    delayDivideByZero.io.clk := io.clk
    delayDivideByZero.io.ce := io.ce
    delayDivideByZero.io.D := io.divideByZeroIn.asBits
    io.divideByZero := delayDivideByZero.io.Q.asBool


    when(io.ce) {
      invalidOpQ := io.invalidOpIn
      overflowQ := (io.flow(fltFlowAlmostOver) && io.expInc) || io.flow(fltFlowOver)
      underflowQ := (io.flow(fltFlowJustUnder) && !io.expInc) || io.flow(fltFlowUnder)
    }

    io.underflow := underflowQ
    io.overflow := overflowQ
    io.invalidOp := invalidOpQ


    when(io.ce) {
      when(io.decOpState(fltDecOpStateSignZero)) {
        signOp := False
      } elsewhen(io.decOpState(fltDecOpStateSignOne)) {
        signOp := True
      } otherwise {
        signOp := io.sign
      }
    }


    when(io.ce) {
      when(io.decOpState(fltDecOpStateExpZero)) {
        expOp(rEw - 1 downto 1) := B(0, rEw - 1 bits)
      } elsewhen(io.decOpState(fltDecOpStateExpOne)) {
        expOp(rEw - 1 downto 1) := B((BigInt(1) << (rEw - 1)) - 1, rEw - 1 bits)
      } otherwise {
        expOp(rEw - 1 downto 1) := expPreOp(rEw - 1 downto 1)
      }
    }


    when(io.ce) {
      when(io.decOpState(fltDecOpStateExpLsbZero)) {
        expOp(0) := False
      } elsewhen(io.decOpState(fltDecOpStateExpLsbOne)) {
        expOp(0) := True
      } otherwise {
        expOp(0) := expPreOp(0)
      }
    }


    when(io.ce) {
      when(io.decOpState(fltDecOpStateMantMsbZero)) {
        mantOp(rFw - 2) := False
      } elsewhen(io.decOpState(fltDecOpStateMantMsbOne)) {
        mantOp(rFw - 2) := True
      } otherwise {
        mantOp(rFw - 2) := io.mant(rFw - 2)
      }
    }


    when(io.ce) {
      when(io.decOpState(fltDecOpStateMantLsbsZero)) {
        mantOp(rFw - 3 downto 0) := B(0, rFw - 2 bits)
      } elsewhen(io.decOpState(fltDecOpStateMantLsbsOne)) {
        mantOp(rFw - 3 downto 0) := B((BigInt(1) << (rFw - 2)) - 1, rFw - 2 bits)
      } otherwise {
        mantOp(rFw - 3 downto 0) := io.mant(rFw - 3 downto 0)
      }
    }


    io.result := signOp ## expOp ## mantOp
  }
}
