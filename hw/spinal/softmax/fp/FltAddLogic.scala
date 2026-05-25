package softmax.fp

import spinal.core._
import softmax.util.FltDelay

class FltAddLogic(
    val cPart: Int = 0,
    val cAWidth: Int = 32,
    val cAFractionWidth: Int = 24,
    val cBWidth: Int = 32,
    val cBFractionWidth: Int = 24,
    val cResultWidth: Int = 32,
    val cResultFractionWidth: Int = 24,
    val cMultUsage: Int = 0,
    val registers: Bits = B"0000_1010_1010_1011"
) extends Component {
  private val abW = cAWidth
  private val abFw = cAFractionWidth
  private val abEw = cAWidth - cAFractionWidth
  private val chunks = 7

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val a = in Bits(cAWidth bits)
    val b = in Bits(cBWidth bits)
    val bNegate = in Bool()
    val result = out Bits(cResultWidth bits)
    val underflow = out Bool()
    val overflow = out Bool()
    val invalidOp = out Bool()
  }

  val aFrac = io.a(abFw - 2 downto 0)
  val bFrac = io.b(abFw - 2 downto 0)

  val aFracDel = FltDelay(io.clk, io.ce, aFrac, abFw - 1, 1)
  val bFracDel = FltDelay(io.clk, io.ce, bFrac, abFw - 1, 1)

  val alignDist0Ip = io.a(abFw - 1) ^ io.b(abFw - 1)
  val alignDist0Mux = FltDelay(io.clk, io.ce, alignDist0Ip.asBits, 1, 1).asBool

  val zeroLargest = Bool()
  val zeroSmallest = Bool()
  val bLargest = Bool()
  val alignDist = Bits(abEw + 1 bits)
  val zeros = Bool()
  val addMant = Bits(abFw + 3 bits)
  val addMantMsbs = Bits(2 bits)
  val cancellation = Bool()
  val normDist = Bits(abEw bits)
  val roundExpInc = Bool()
  val roundMant = Bits(abFw - 1 bits)
  val subtract = Bool()
  val opState = Bits(12 bits)
  val expOp = Bits(abEw bits)
  val opSign = Bool()
  val opFlow = Bits(4 bits)
  val opInvalid = Bool()

  val alignAdd = new FltAlignAdd(
    cMultUsage = 0,
    abFw = abFw,
    distWidth = abEw + 1,
    zDetWidth = chunks
  )
  alignAdd.io.clk := io.clk
  alignAdd.io.ce := io.ce
  alignAdd.io.aFrac := aFracDel
  alignAdd.io.bFrac := bFracDel
  alignAdd.io.zeroLargest := zeroLargest
  alignAdd.io.zeroSmallest := zeroSmallest
  alignAdd.io.bLargest := bLargest
  alignAdd.io.distBit0 := alignDist0Mux
  alignAdd.io.dist := alignDist
  alignAdd.io.subtract := subtract
  addMant := alignAdd.io.sum
  zeros := alignAdd.io.zeros
  addMantMsbs := addMant(abFw + 2 downto abFw + 1)

  val normRound = new FltNormAndRoundLogic(
    FltNormAndRoundLogicConfig(
      AB_FW = abFw,
      AB_EW = abEw,
      REGISTERS = "0000_0000_0010_1010"
    )
  )
  normRound.io.clk := io.clk
  normRound.io.ce := io.ce
  normRound.io.MANT_IN := addMant.asUInt
  normRound.io.ZEROS := zeros
  normDist := normRound.io.NORM_DIST.asBits
  roundMant := normRound.io.MANT_OUT.asBits
  cancellation := normRound.io.CANCELLATION
  roundExpInc := normRound.io.ROUND_EXP_INC

  val addExp = new FltAddExp(
    abW = abW,
    abEw = abEw,
    abFw = abFw
  )
  addExp.io.clk := io.clk
  addExp.io.ce := io.ce
  addExp.io.a := io.a
  addExp.io.b := io.b
  addExp.io.bNegate := io.bNegate
  addExp.io.normDist := normDist
  addExp.io.cancellation := cancellation
  addExp.io.roundExpInc := roundExpInc
  addExp.io.addMantMsbs := addMantMsbs
  bLargest := addExp.io.bLargest
  zeroLargest := addExp.io.zeroLargest
  zeroSmallest := addExp.io.zeroSmallest
  alignDist := addExp.io.alignDist
  subtract := addExp.io.subtract
  opState := addExp.io.decState
  expOp := addExp.io.expOut
  opSign := addExp.io.signOut
  opFlow := addExp.io.flow
  opInvalid := addExp.io.invalidOp

  val decOp = new FltDecOp(
    rW = abW,
    rFw = abFw,
    registered = 1,
    reducedRange = 0,
    expAdder = 1
  )
  decOp.io.clk := io.clk
  decOp.io.ce := io.ce
  decOp.io.DEC_OP_STATE := opState
  decOp.io.FLOW := opFlow
  decOp.io.INVALID_OP_IN := opInvalid
  decOp.io.MANT := roundMant
  decOp.io.EXP := expOp
  decOp.io.SIGN := opSign
  decOp.io.EXP_INC := roundExpInc

  io.result := decOp.io.RESULT
  io.underflow := decOp.io.UNDERFLOW
  io.overflow := decOp.io.OVERFLOW
  io.invalidOp := decOp.io.INVALID_OP
}
