package softmax.fp

import spinal.core._

class FltAddExp(
    val abW: Int = 32,
    val abEw: Int = 8,
    val abFw: Int = 24,
    val addType: Int = 0,
    val registers: Bits = B"0000_1010_1010_1011"
) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val a = in Bits(abW bits)
    val b = in Bits(abW bits)
    val bNegate = in Bool()
    val normDist = in Bits(abEw bits)
    val cancellation = in Bool()
    val roundExpInc = in Bool()
    val addMantMsbs = in Bits(2 bits)
    val bLargest = out Bool()
    val zeroLargest = out Bool()
    val zeroSmallest = out Bool()
    val alignDist = out Bits(abEw + 1 bits)
    val subtract = out Bool()
    val decState = out Bits(12 bits)
    val expOut = out Bits(abEw bits)
    val signOut = out Bool()
    val flow = out Bits(4 bits)
    val invalidOp = out Bool()
  }

  private val addClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(addClockDomain) {
    val aExp = io.a(abW - 2 downto abFw - 1).asUInt
    val bExp = io.b(abW - 2 downto abFw - 1).asUInt
    val aMant = io.a(abFw - 2 downto 0).asUInt
    val bMant = io.b(abFw - 2 downto 0).asUInt
    val aZero = (aExp === 0) && (aMant === 0)
    val bZero = (bExp === 0) && (bMant === 0)
    val aNan = aExp.andR && (aMant =/= 0)
    val bNan = bExp.andR && (bMant =/= 0)
    val bLargestNow = (bExp > aExp) || ((bExp === aExp) && (bMant > aMant))
    val largestExp = Mux(bLargestNow, bExp, aExp)
    val smallestExp = Mux(bLargestNow, aExp, bExp)
    val alignDistNow = (largestExp - smallestExp).resize(abEw + 1)
    val signNow = Mux(bLargestNow, io.b.msb ^ io.bNegate, io.a.msb)

    val bLargestR = Reg(Bool()) init(False)
    val zeroLargestR = Reg(Bool()) init(False)
    val zeroSmallestR = Reg(Bool()) init(False)
    val alignDistR = Reg(Bits(abEw + 1 bits)) init(0)
    val subtractR = Reg(Bool()) init(False)
    val decStateR = Reg(Bits(12 bits)) init(0)
    val expOutR = Reg(Bits(abEw bits)) init(0)
    val signOutR = Reg(Bool()) init(False)
    val flowR = Reg(Bits(4 bits)) init(0)
    val invalidOpR = Reg(Bool()) init(False)

    when(io.ce) {
      bLargestR := bLargestNow
      zeroLargestR := Mux(bLargestNow, aZero, bZero)
      zeroSmallestR := Mux(bLargestNow, bZero, aZero)
      alignDistR := alignDistNow.asBits
      subtractR := io.bNegate ^ (io.a.msb ^ io.b.msb)
      decStateR := B(0, 12 bits)
      expOutR := largestExp.asBits
      signOutR := signNow
      flowR := B(0, 4 bits)
      invalidOpR := aNan || bNan
    }

    io.bLargest := bLargestR
    io.zeroLargest := zeroLargestR
    io.zeroSmallest := zeroSmallestR
    io.alignDist := alignDistR
    io.subtract := subtractR
    io.decState := decStateR
    io.expOut := expOutR
    io.signOut := signOutR
    io.flow := flowR
    io.invalidOp := invalidOpR
  }
}
