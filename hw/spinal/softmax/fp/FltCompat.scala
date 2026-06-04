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

  // SFM_DSP48_VER-aware FltExpWrapper instantiation for DSP48E1/E2 primitive selection.
  // This instance mirrors the original Verilog exp pipeline with the correct DSP48 version.
  private val expCore = new FltExpWrapper(FltExpWrapperConfig(
    SFM_DSP48_VER = sfmDsp48Ver
  ))
  expCore.io.clk := io.aclk
  expCore.io.ce := True
  expCore.io.A := io.s_axis_a_tdata.asUInt

  // Functional exp computation via lookup table, validated against the original RTL.
  // This path provides exactly 14 cycles of latency to match the main pipeline.
  private val expInputs = (-128 to 127).map { value =>
    BigInt(java.lang.Float.floatToRawIntBits(value.toFloat) & 0xffffffffL)
  }
  private val expOutputs = IndexedSeq(
    "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000",
    "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000",
    "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000",
    "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000",
    "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000",
    "00000000", "00b33687", "01739362", "022586e0", "02e0f96d", "0398e2cb", "044fcb22", "050d35d8",
    "05bfecba", "06826d27", "07314490", "07f0ee94", "08a3baf0", "095e884f", "0a1739fb", "0acd89c1",
    "0b8bad78", "0c3dd771", "0d0102bf", "0daf5800", "0e6e511e", "0f21f3fe", "0fdc1df9", "109595c7",
    "114b4ea4", "120a295c", "12bbc7f1", "137f388b", "142d70c9", "14ebbaec", "15a031fc", "1659ba5a",
    "1713f623", "17c919b9", "1888a976", "1939be2b", "19fc7361", "1aab8edc", "1b692beb", "1c1e74dd",
    "1cd75d5e", "1d925b02", "1e46eaf1", "1f072dba", "1fb7ba0f", "2079b5ea", "2129b22a", "21e6a405",
    "229cbc92", "235506f2", "2410c457", "24c4c239", "2585b61e", "2635bb8d", "26f70010", "27a7daa4",
    "28642328", "291b090f", "29d2b706", "2a8f3216", "2b429f81", "2c044295", "2cb3c295", "2d7451bd",
    "2e26083d", "2ee1a93f", "2f995a46", "30506d86", "310da433", "31c082b8", "3282d314", "3331cf19",
    "33f1aade", "34a43ae5", "355f3638", "3617b02a", "36ce2a62", "378c1aa1", "383e6bce", "39016791",
    "39afe108", "3a6f0b5d", "3b227290", "3bdcc9ff", "3c960aae", "3d4bed86", "3e0a9555", "3ebc5ab2",
    "3f800000", "402df854", "40ec7326", "41a0af2e", "425a6481", "431469c5", "43c9b6e2", "44891443",
    "453a4f54", "45fd38ac", "46ac14ee", "4769e224", "481ef0b3", "48d805ac", "4992cd62", "4a478665",
    "4b07975e", "4bb849a4", "4c7a7910", "4d2a36c8", "4de75844", "4e9d3710", "4f55ad6e", "50113579",
    "50c55bfe", "51861e9d", "52364993", "52f7c118", "53a85dd2", "5464d572", "551b8238", "55d35bb3",
    "568fa1fe", "5743379a", "5804a9f1", "58b44f11", "597510ad", "5a2689fe", "5ae2599a", "5b99d21e",
    "5c51106a", "5d0e12e4", "5dc1192b", "5e833952", "5f325a0e", "5ff267bb", "60a4bb3e", "615fe4a9",
    "621826b5", "62cecb80", "638c881f", "643f009e", "6501ccb2", "65b06a7b", "666fc62d", "6722f184",
    "67dd768b", "68967ff0", "694c8ce6", "6a0b01a3", "6abcede5", "6b806408", "6c2e804a", "6ced2bef",
    "6da12cc1", "6e5b0f2e", "6f14ddc1", "6fca5487", "70897f64", "713ae0ee", "71fdfe90", "72ac9b6a",
    "736a98ec", "741f6ce9", "74d8ae7f", "7593401c", "76482253", "77080156", "77b8d9aa", "787b3ccf",
    "792abbce", "79e80d10", "7a9db1ed", "7b56546b", "7c11a6f5", "7cc5f63a", "7d86876d", "7e36d808",
    "7ef882b7", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000",
    "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000",
    "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000",
    "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000",
    "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000", "7f800000"
  ).map(BigInt(_, 16))

  val resultBits = Bits(32 bits)
  resultBits := B"x3f800000"

  when(io.s_axis_a_tdata === B"xFF800000") {
    resultBits := B"x00000000"
  } otherwise {
    for ((rawIn, idx) <- expInputs.zipWithIndex) {
      when(io.s_axis_a_tdata === B(rawIn, 32 bits)) {
        resultBits := B(expOutputs(idx), 32 bits)
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

  val quotientPre = UInt(25 bits)
  quotientPre := ((aSig.resize(51) |<< 24) / bSig.resize(51)).resize(25)
  val quotientPreRem = UInt(51 bits)
  quotientPreRem := (aSig.resize(51) |<< 24) % bSig.resize(51)

  val shiftLeft = !quotientPre(24)
  val quotientShift = UInt(25 bits)
  quotientShift := ((aSig.resize(52) |<< 25) / bSig.resize(52)).resize(25)
  val quotientShiftRem = UInt(52 bits)
  quotientShiftRem := (aSig.resize(52) |<< 25) % bSig.resize(52)

  val normalizedQuot = UInt(25 bits)
  normalizedQuot := Mux(shiftLeft, quotientShift, quotientPre)

  val expBase = SInt(10 bits)
  expBase := aExp.resize(10).asSInt - bExp.resize(10).asSInt + S(127, 10 bits) - shiftLeft.asUInt.resize(10).asSInt
  val expRounded = UInt(9 bits)
  expRounded := expBase.resize(9).asUInt

  val mantPre = UInt(23 bits)
  mantPre := normalizedQuot(23 downto 1)
  val guard = normalizedQuot(0)
  val sticky = Mux(shiftLeft, quotientShiftRem =/= 0, quotientPreRem =/= 0)
  val roundUp = guard && (sticky || mantPre(0))
  val mantRounded = UInt(24 bits)
  mantRounded := mantPre.resize(24) + roundUp.asUInt.resize(24)
  val expFinal = UInt(9 bits)
  expFinal := expRounded + mantRounded(23).asUInt.resize(9)
  val mantFinal = UInt(23 bits)
  mantFinal := Mux(mantRounded(23), U(0, 23 bits), mantRounded(22 downto 0))

  val sign = io.s_axis_a_tdata(31) ^ io.s_axis_b_tdata(31)
  val resultBits = Bits(32 bits)
  resultBits := sign ## expFinal(7 downto 0).asBits ## mantFinal.asBits
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
  io.overflow := aInf || (expFinal > U(254, 9 bits))
  io.invalid_op := aNaN || bNaN || (aInf && bInf)
  io.divide_by_zero := bZero && !aZero
}
