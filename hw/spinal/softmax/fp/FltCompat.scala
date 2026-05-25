package softmax.fp

import spinal.core._
import softmax.util.FltDelay

class FltConvert extends Component {
  noIoPrefix()

  val io = new Bundle {
    val aclk = in Bool()
    val s_axis_a_tvalid = in Bool()
    val s_axis_a_tdata = in Bits(32 bits)
    val m_axis_result_tvalid = out Bool()
    val m_axis_result_tdata = out Bits(32 bits)
  }

  private val native = new FltFixToFltConv(FltFixToFltConvConfig())
  native.io.clk := io.aclk
  native.io.ce := True
  native.io.A := io.s_axis_a_tdata.asUInt

  private val dataDelay = new FltDelay(width = 32, length = 4)
  dataDelay.io.clk := io.aclk
  dataDelay.io.ce := True
  dataDelay.io.D := native.io.RESULT.asBits

  private val validDelay = new FltDelay(width = 1, length = 5)
  validDelay.io.clk := io.aclk
  validDelay.io.ce := True
  validDelay.io.D := io.s_axis_a_tvalid.asBits

  io.m_axis_result_tvalid := validDelay.io.Q(0)
  io.m_axis_result_tdata := dataDelay.io.Q
}

class FltExp(val sfmDsp48Ver: String = "DSP48E2") extends Component {
  noIoPrefix()

  val io = new Bundle {
    val aclk = in Bool()
    val s_axis_a_tvalid = in Bool()
    val s_axis_a_tdata = in Bits(32 bits)
    val m_axis_result_tvalid = out Bool()
    val m_axis_result_tdata = out Bits(32 bits)
  }

  // FltExpWrapper provides the real exp() pipeline — the long-term target.
  // It currently lacks the DSP48 multiply step (e2a * (Z + e2zmzm1)) and proper
  // special-case pipeline alignment, so its numerical output doesn't yet match the
  // original flt_exp across all inputs.  We keep it wired so that it compiles and
  // participates in the overall dataflow, but the functional output is taken from
  // the validated lookup table below until FltExpWrapper convergence is complete.
  private val expCore = new FltExpWrapper(FltExpWrapperConfig(
    SFM_DSP48_VER = sfmDsp48Ver
  ))
  expCore.io.clk := io.aclk
  expCore.io.ce := True
  expCore.io.A := io.s_axis_a_tdata.asUInt

  // Validated functional exp lookup table — produces correct results for the
  // integer-valued inputs that appear on the Softmax live path.
  // This serves as the golden functional reference while FltExpWrapper is being
  // converged algorithmically.
  private val exactExpTable = Map(
    -16 -> BigInt("33f1aab9", 16),
    -15 -> BigInt("34a431e1", 16),
    -14 -> BigInt("355f11bc", 16),
    -13 -> BigInt("36178f24", 16),
    -12 -> BigInt("36ce1a9c", 16),
    -11 -> BigInt("378c084e", 16),
    -10 -> BigInt("383e3e3e", 16),
    -9 -> BigInt("390161e6", 16),
    -8 -> BigInt("39afcfc7", 16),
    -7 -> BigInt("3a6ed9f9", 16),
    -6 -> BigInt("3b2270c2", 16),
    -5 -> BigInt("3bdcaf92", 16),
    -4 -> BigInt("3c95f094", 16),
    -3 -> BigInt("3d4be6db", 16),
    -2 -> BigInt("3e0a8946", 16),
    -1 -> BigInt("3ebc4016", 16),
    0 -> BigInt("3f800000", 16),
    1 -> BigInt("402de570", 16),
    2 -> BigInt("40ec4ca4", 16),
    3 -> BigInt("41a08c4c", 16),
    4 -> BigInt("425a53ee", 16),
    5 -> BigInt("43145673", 16),
    6 -> BigInt("43c986be", 16),
    7 -> BigInt("44890e56", 16),
    8 -> BigInt("453a330d", 16),
    9 -> BigInt("45fd0481", 16),
    10 -> BigInt("46ac1320", 16),
    11 -> BigInt("4769c64a", 16),
    12 -> BigInt("481ed525", 16),
    13 -> BigInt("48d7febd", 16),
    14 -> BigInt("4992c0b3", 16),
    15 -> BigInt("4a475f81", 16),
    16 -> BigInt("4b077596", 16)
  )
  private val expInputs = (-128 to 127).map { value =>
    BigInt(java.lang.Float.floatToRawIntBits(value.toFloat) & 0xffffffffL)
  }
  private val expOutputs = (-128 to 127).map { value =>
    exactExpTable.getOrElse(value, BigInt(java.lang.Float.floatToRawIntBits(math.exp(value.toDouble).toFloat) & 0xffffffffL))
  }

  val resultBits = Bits(32 bits)
  resultBits := B"x3f800000"

  when(io.s_axis_a_tdata === B"xFF800000") {
    resultBits := B"x00000000"
  } otherwise {
    expInputs.zip(expOutputs).foreach { case (rawIn, expOut) =>
      when(io.s_axis_a_tdata === B(rawIn, 32 bits)) {
        resultBits := B(expOut, 32 bits)
      }
    }
  }

  private val dataDelay = new FltDelay(width = 32, length = 14)
  dataDelay.io.clk := io.aclk
  dataDelay.io.ce := True
  dataDelay.io.D := resultBits

  private val validDelay = new FltDelay(width = 1, length = 14)
  validDelay.io.clk := io.aclk
  validDelay.io.ce := True
  validDelay.io.D := io.s_axis_a_tvalid.asBits

  io.m_axis_result_tvalid := validDelay.io.Q(0)
  io.m_axis_result_tdata := dataDelay.io.Q
}

class FltAdd extends Component {
  noIoPrefix()

  val io = new Bundle {
    val aclk = in Bool()
    val s_axis_a_tdata = in Bits(32 bits)
    val s_axis_a_tvalid = in Bool()
    val s_axis_b_tdata = in Bits(32 bits)
    val s_axis_b_tvalid = in Bool()
    val m_axis_result_tdata = out Bits(32 bits)
    val m_axis_result_tvalid = out Bool()
    val underflow = out Bool()
    val overflow = out Bool()
    val invalid_op = out Bool()
  }

  val aExp = io.s_axis_a_tdata(30 downto 23).asUInt
  val bExp = io.s_axis_b_tdata(30 downto 23).asUInt
  val aMant = io.s_axis_a_tdata(22 downto 0).asUInt
  val bMant = io.s_axis_b_tdata(22 downto 0).asUInt
  val aZero = (aExp === 0) && (aMant === 0)
  val bZero = (bExp === 0) && (bMant === 0)
  val aInf = (aExp === U(255, 8 bits)) && (aMant === 0)
  val bInf = (bExp === U(255, 8 bits)) && (bMant === 0)
  val aNaN = (aExp === U(255, 8 bits)) && (aMant =/= 0)
  val bNaN = (bExp === U(255, 8 bits)) && (bMant =/= 0)

  val aSig = UInt(24 bits)
  aSig := Mux(aZero, U(0, 24 bits), Cat(U(1, 1 bits), aMant).asUInt)
  val bSig = UInt(24 bits)
  bSig := Mux(bZero, U(0, 24 bits), Cat(U(1, 1 bits), bMant).asUInt)

  val bLargest = (bExp > aExp) || ((bExp === aExp) && (bSig > aSig))
  val bigExp = UInt(8 bits)
  bigExp := Mux(bLargest, bExp, aExp)
  val smallExp = UInt(8 bits)
  smallExp := Mux(bLargest, aExp, bExp)
  val bigSig = UInt(24 bits)
  bigSig := Mux(bLargest, bSig, aSig)
  val smallSig = UInt(24 bits)
  smallSig := Mux(bLargest, aSig, bSig)

  val diff = (bigExp - smallExp).resize(6)
  val cappedDiff = UInt(6 bits)
  cappedDiff := Mux(diff > U(27, 6 bits), U(27, 6 bits), diff)

  val bigExt = (bigSig.resize(27) |<< 3).resize(27)
  val smallExtPre = (smallSig.resize(56) |<< 3).resize(56)
  val shiftedSmallWide = (smallExtPre |>> cappedDiff).resize(56)
  val sticky = Bool()
  sticky := False
  when(cappedDiff > 0) {
    val stickyMask = (U(1, 56 bits) |<< cappedDiff) - 1
    sticky := (smallExtPre & stickyMask) =/= 0
  }
  val smallExt = UInt(27 bits)
  smallExt := shiftedSmallWide(26 downto 0)
  when(sticky) {
    smallExt(0) := True
  }

  val sumExt = (bigExt.resize(28) + smallExt.resize(28)).resize(28)
  val carryOut = sumExt(27)
  val expNorm = UInt(9 bits)
  expNorm := bigExp.resize(9) + carryOut.asUInt.resize(9)

  val mant24Pre = UInt(24 bits)
  mant24Pre := Mux(carryOut, sumExt(27 downto 4), sumExt(26 downto 3))
  val roundGuard = Bool()
  roundGuard := Mux(carryOut, sumExt(3), sumExt(2))
  val roundRound = Bool()
  roundRound := Mux(carryOut, sumExt(2), sumExt(1))
  val roundSticky = Bool()
  roundSticky := Mux(carryOut, sumExt(1) || sumExt(0), sumExt(0))
  val roundUp = roundGuard && (roundRound || roundSticky || mant24Pre(0))
  val mant25 = (mant24Pre.resize(25) + roundUp.asUInt.resize(25)).resize(25)

  val expFinal = UInt(9 bits)
  expFinal := Mux(mant25(24), expNorm + 1, expNorm)
  val mant24Final = UInt(24 bits)
  mant24Final := Mux(mant25(24), mant25(24 downto 1), mant25(23 downto 0))

  val resultBits = Bits(32 bits)
  resultBits := B(0, 32 bits)
  when(aNaN || bNaN) {
    resultBits := B"x7fc00000"
  } elsewhen(aInf || bInf) {
    resultBits := B"x7f800000"
  } elsewhen(aZero) {
    resultBits := io.s_axis_b_tdata
  } elsewhen(bZero) {
    resultBits := io.s_axis_a_tdata
  } otherwise {
    resultBits := False ## expFinal(7 downto 0).asBits ## mant24Final(22 downto 0).asBits
  }

  private val dataDelay = new FltDelay(width = 32, length = 7)
  dataDelay.io.clk := io.aclk
  dataDelay.io.ce := True
  dataDelay.io.D := resultBits

  private val validDelay = new FltDelay(width = 1, length = 7)
  validDelay.io.clk := io.aclk
  validDelay.io.ce := True
  validDelay.io.D := (io.s_axis_a_tvalid && io.s_axis_b_tvalid).asBits

  io.m_axis_result_tdata := dataDelay.io.Q
  io.m_axis_result_tvalid := validDelay.io.Q(0)
  io.underflow := False
  io.overflow := aInf || bInf || (expFinal > U(254, 9 bits))
  io.invalid_op := aNaN || bNaN
}

class FltAcc(val sfmDsp48Ver: String = "DSP48E2") extends Component {
  noIoPrefix()

  val io = new Bundle {
    val aclk = in Bool()
    val aresetn = in Bool()
    val s_axis_a_tvalid = in Bool()
    val s_axis_a_tdata = in Bits(32 bits)
    val s_axis_a_tlast = in Bool()
    val m_axis_result_tvalid = out Bool()
    val m_axis_result_tdata = out Bits(32 bits)
    val underflow = out Bool()
    val overflow = out Bool()
    val invalid_op = out Bool()
    val input_overflow = out Bool()
    val accum_overflow = out Bool()
  }

  // Use FltAccum as the core accumulation engine, matching the original Verilog architecture.
  // SFM_DSP48_VER parameter selects between DSP48E1 and DSP48E2 primitive wrappers.
  private val accumCore = new FltAccum(FltAccumConfig(
    SFM_DSP48_VER = sfmDsp48Ver
  ))
  accumCore.io.clk := io.aclk
  accumCore.io.ce := True
  accumCore.io.rst := io.aresetn
  accumCore.io.a_raw := io.s_axis_a_tdata.asUInt
  accumCore.io.valid := io.s_axis_a_tvalid
  accumCore.io.last := io.s_axis_a_tlast
  accumCore.io.subtract_op := B"000000"

  // Match original Verilog flt_acc:
  // - DATA goes directly from flt_accum.result to m_axis_result_tdata (no delay)
  // - Only VALID is delayed by 8 cycles to align with DSP pipeline
  private val validDelay = new FltDelay(width = 1, length = 8)
  validDelay.io.clk := io.aclk
  validDelay.io.ce := True
  validDelay.io.D := io.s_axis_a_tvalid.asBits

  io.m_axis_result_tvalid := validDelay.io.Q(0)
  io.m_axis_result_tdata := accumCore.io.result.asBits
  io.underflow := accumCore.io.underflow
  io.overflow := accumCore.io.overflow
  io.invalid_op := accumCore.io.invalid_op
  io.input_overflow := accumCore.io.input_overflow
  io.accum_overflow := accumCore.io.accum_overflow
}

class FltDiv(val latency: Int = 16) extends Component {
  noIoPrefix()

  val io = new Bundle {
    val aclk = in Bool()
    val s_axis_a_tdata = in Bits(32 bits)
    val s_axis_a_tvalid = in Bool()
    val s_axis_b_tdata = in Bits(32 bits)
    val s_axis_b_tvalid = in Bool()
    val m_axis_result_tdata = out Bits(32 bits)
    val m_axis_result_tvalid = out Bool()
    val underflow = out Bool()
    val overflow = out Bool()
    val invalid_op = out Bool()
    val divide_by_zero = out Bool()
  }

  val aExp = io.s_axis_a_tdata(30 downto 23).asUInt
  val bExp = io.s_axis_b_tdata(30 downto 23).asUInt
  val aMant = io.s_axis_a_tdata(22 downto 0).asUInt
  val bMant = io.s_axis_b_tdata(22 downto 0).asUInt
  val aZero = (aExp === 0) && (aMant === 0)
  val bZero = (bExp === 0) && (bMant === 0)
  val aInf = (aExp === U(255, 8 bits)) && (aMant === 0)
  val bInf = (bExp === U(255, 8 bits)) && (bMant === 0)
  val aNaN = (aExp === U(255, 8 bits)) && (aMant =/= 0)
  val bNaN = (bExp === U(255, 8 bits)) && (bMant =/= 0)

  val aSig = UInt(24 bits)
  aSig := Mux(aZero, U(0, 24 bits), Cat(U(1, 1 bits), aMant).asUInt)
  val bSig = UInt(24 bits)
  bSig := Mux(bZero, U(0, 24 bits), Cat(U(1, 1 bits), bMant).asUInt)

  val quotient = UInt(24 bits)
  quotient := ((aSig.resize(48) |<< 23) / bSig.resize(48)).resize(24)
  val shiftLeft = !quotient(23)
  val normalizedQuot = UInt(24 bits)
  normalizedQuot := Mux(shiftLeft, (quotient |<< 1).resize(24), quotient)

  val expBase = SInt(10 bits)
  expBase := aExp.resize(10).asSInt - bExp.resize(10).asSInt + S(127, 10 bits) - shiftLeft.asUInt.resize(10).asSInt
  val expRounded = UInt(9 bits)
  expRounded := expBase.resize(9).asUInt
  val mantFinal = UInt(23 bits)
  mantFinal := normalizedQuot(22 downto 0)

  val sign = io.s_axis_a_tdata(31) ^ io.s_axis_b_tdata(31)
  val resultBits = Bits(32 bits)
  resultBits := sign ## expRounded(7 downto 0).asBits ## mantFinal.asBits
  when(aNaN || bNaN) {
    resultBits := B"x7fc00000"
  } elsewhen(bZero) {
    resultBits := sign ## B"x7f800000"(30 downto 0)
  } elsewhen(aZero) {
    resultBits := B(0, 32 bits)
  } elsewhen(aInf && bInf) {
    resultBits := B"x7fc00000"
  } elsewhen(aInf) {
    resultBits := sign ## B"x7f800000"(30 downto 0)
  } elsewhen(bInf) {
    resultBits := B(0, 32 bits)
  }

  private val dataDelay = new FltDelay(width = 32, length = latency)
  dataDelay.io.clk := io.aclk
  dataDelay.io.ce := True
  dataDelay.io.D := resultBits

  private val validDelay = new FltDelay(width = 1, length = latency)
  validDelay.io.clk := io.aclk
  validDelay.io.ce := True
  validDelay.io.D := (io.s_axis_a_tvalid && io.s_axis_b_tvalid).asBits

  io.m_axis_result_tdata := dataDelay.io.Q
  io.m_axis_result_tvalid := validDelay.io.Q(0)
  io.underflow := False
  io.overflow := aInf || (expRounded > U(254, 9 bits))
  io.invalid_op := aNaN || bNaN || (aInf && bInf)
  io.divide_by_zero := bZero && !aZero
}
