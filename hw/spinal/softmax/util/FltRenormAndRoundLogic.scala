


























package softmax.util

import spinal.core._

case class FltRenormAndRoundLogicConfig(
  FW: Int = 24,
  EW: Int = 8,
  CONFIG_IMP_TYPE: Int = 0,
  CONFIG_LEGACY: Int = 1,
  HAS_ADD: Int = 0,
  FIX_SUPPORT: Int = 0,
  EXP_DELAY: Int = 1,
  NO_SHIFT_INC: Int = 0,
  NORM_BITS: Int = 1,
  EXP_ADDER: Int = 0,
  EXP_INC: Int = 0,
  SPEED: Int = 2
) {
  val FLT_PT_IMP_LOGIC = 0
  val RR_IMP_TYPE = CONFIG_IMP_TYPE

  val LOCAL_REG = if (RR_IMP_TYPE == FLT_PT_IMP_LOGIC) 0x08 else 0x04
  val RND1_WIDTH = FW >> 1
  val RND2_WIDTH = FW - RND1_WIDTH
}

class FltRenormAndRoundLogic(config: FltRenormAndRoundLogicConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val MANT_IN = in UInt(FW + 2 bits)
    val FIX_MANT_SIGN = in Bool()
    val SIGN = in Bool()
    val ZERO_LSBS = in Bool()
    val EXTRA_LSB = in Bool()
    val EXTRA_LSBS = in Bits(2 bits)
    val NORMALIZE = in Bool()
    val NORMALIZE2 = in Bool()
    val EXP_INC_IN = in Bool()
    val EXP_IN = in UInt(EW bits)
    val EXP_OFF = in UInt(EW bits)
    val FIXED_POINT = in Bool()

    val MANT_OUT = out UInt(FW - 1 bits)
    val EXP_OUT = out UInt(EW bits)
    val EXP_INC_OUT = out Bool()
  }

  private val cd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(cd) {
    import softmax.util.{FltDelay => D}
    def dly(d: Bits, w: Int, len: Int): Bits = D(io.clk, io.ce, d, w, len)


    val fixed_point_bit = dly(io.FIXED_POINT.asBits, 1, 0)(0)
    val mant_rnd1 = dly(io.MANT_IN(RND1_WIDTH + 1 downto 0).asBits, RND1_WIDTH + 2, 0).asUInt
    val mant_rnd2 = dly(io.MANT_IN(FW downto FW - RND2_WIDTH - 1).asBits, RND2_WIDTH + 2, HAS_ADD).asUInt
    val full_mant_rnd1 = dly(io.MANT_IN(FW + 1 downto 1).asBits, FW + 1, 0).asUInt
    val mant_lsbs_bit = dly(io.MANT_IN(2 downto 0).asBits, 3, 0)
    val zero_lsbs_bit = dly(io.ZERO_LSBS.asBits, 1, 0)(0)
    val extra_lsb_bit = dly(io.EXTRA_LSB.asBits, 1, 0)(0)
    val extra_lsb_rnd1 = dly(io.EXTRA_LSB.asBits, 1, 0)(0)
    val extra_lsbs_bit = dly(io.EXTRA_LSBS, 2, 0)
    val normalize_ext_bit = io.NORMALIZE2 ## io.NORMALIZE
    val normalize_bit = dly(normalize_ext_bit, 2, 0)
    val exp_inc_sl = if (EXP_INC == 1) io.EXP_INC_IN else False
    val exp_inc_rnd2 = dly(exp_inc_sl.asBits, 1, HAS_ADD)(0)
    val exp_inc_rnd1 = dly(exp_inc_sl.asBits, 1, 0)(0)
    val exp_op = dly(io.EXP_IN.asBits, EW, 1 - HAS_ADD).asUInt
    val exp_off_op = dly(io.EXP_OFF.asBits, EW, 1 - HAS_ADD).asUInt
    val fix_mant_sign_bit = dly(io.FIX_MANT_SIGN.asBits, 1, 0)(0)
    val sign_bit = dly(io.SIGN.asBits, 1, 0)(0)



    val rb_norm0 = normalize_bit(0)
    val rb_truncate = False
    val rb_non_zero_trunc = mant_lsbs_bit(0) || mant_lsbs_bit(1) || !zero_lsbs_bit || extra_lsb_bit
    val rb_fix_neg_trunc = False
    val rb_zero_bit = rb_norm0 ?
      (zero_lsbs_bit && !extra_lsb_bit && !extra_lsbs_bit(0) && !extra_lsbs_bit(1)) |
      (zero_lsbs_bit && !extra_lsb_bit && !extra_lsbs_bit(0) && !extra_lsbs_bit(1) && !mant_lsbs_bit(0))
    val rb_lsb = rb_norm0 ? mant_lsbs_bit(1) | mant_lsbs_bit(2)
    val rb_round = rb_norm0 ? (!rb_truncate && mant_lsbs_bit(0)) | (!rb_truncate && mant_lsbs_bit(1))
    val rb_round_bit = (rb_round && !rb_zero_bit) || (rb_round && rb_zero_bit && rb_lsb)
    val rb_b_ip = U(4, 3 bits)
    val rb_a_ip = (!rb_round_bit ## rb_fix_neg_trunc ## rb_non_zero_trunc).asUInt
    val rb_a_b_sum = ((rb_b_ip ^ rb_a_ip) +^ rb_b_ip + 1)
    val round_rnd1 = rb_a_b_sum(3)


    val normalize_rnd1 = dly(normalize_bit, 2, 0)


    val mant_shifted_rnd1 = normalize_rnd1(0) ?
      mant_rnd1(RND1_WIDTH downto 1) | mant_rnd1(RND1_WIDTH + 1 downto 2)
    val mant_round_op_rnd1 = (mant_shifted_rnd1 +^ round_rnd1.asUInt)
    val mant_round_op_lo = dly(mant_round_op_rnd1(RND1_WIDTH - 1 downto 0).asBits, RND1_WIDTH, 1).asUInt
    val carry_rnd2 = dly(mant_round_op_rnd1(RND1_WIDTH).asBits, 1, HAS_ADD)(0)

    val sh0_mant_rnd2 = (exp_inc_rnd2 ## mant_rnd2(RND2_WIDTH + 1 downto 3)).asUInt
    val sh1_mant_rnd2 = (exp_inc_rnd2 ## mant_rnd2(RND2_WIDTH downto 2)).asUInt
    val normalize_rnd2 = dly(normalize_rnd1, 2, HAS_ADD)
    val mant_shifted_rnd2 = normalize_rnd2(0) ? sh1_mant_rnd2 | sh0_mant_rnd2
    val b_rnd2 = (U(1, 1 bits) ## U(0, RND2_WIDTH - 1 bits)).asUInt
    val mant_round_op_rnd2 = (b_rnd2 +^ mant_shifted_rnd2 + carry_rnd2.asUInt)
    val mant_round_op_hi = dly(mant_round_op_rnd2(RND2_WIDTH - 1 downto 0).asBits, RND2_WIDTH, 1 - HAS_ADD).asUInt
    val carry_op = dly(mant_round_op_rnd2(RND2_WIDTH).asBits, 1, 1 - HAS_ADD)(0)


    val mant_round_op = (mant_round_op_hi ## mant_round_op_lo).asUInt
    val EXP_INC_OUT_LOGIC = !mant_round_op(FW - 1)
    val MANT_OUT_LOGIC = mant_round_op(FW - 2 downto 0)


    val ext_mant_rnd1 = (exp_inc_rnd1 ## full_mant_rnd1(FW downto 0)).asUInt
    val dsp_c = ext_mant_rnd1
    val dsp_b = (U(0, 16 bits) ## round_rnd1 ## U(0, 1 bits)).asUInt.resize(FW + 2)
    val dsp_p = Reg(UInt(FW + 2 bits)) init(0)
    when(normalize_rnd1(0)) {
      dsp_p := (dsp_b + dsp_c + dsp_c).resized
    } otherwise {
      dsp_p := (dsp_b + dsp_c).resized
    }
    val EXP_INC_OUT_DSP = dsp_p(FW + 1)
    val MANT_OUT_DSP = dsp_p(FW - 1 downto 1)


    io.MANT_OUT := (if (RR_IMP_TYPE == 1) MANT_OUT_DSP else MANT_OUT_LOGIC)
    io.EXP_INC_OUT := (if (RR_IMP_TYPE == 1) EXP_INC_OUT_DSP else EXP_INC_OUT_LOGIC)
    io.EXP_OUT := (exp_op + exp_off_op + carry_op.asUInt).resized
  }
}

object FltRenormAndRoundLogic {
  def apply(
    clk: Bool,
    ce: Bool,
    MANT_IN: UInt,
    FIX_MANT_SIGN: Bool,
    SIGN: Bool,
    ZERO_LSBS: Bool,
    EXTRA_LSB: Bool,
    EXTRA_LSBS: Bits,
    NORMALIZE: Bool,
    NORMALIZE2: Bool,
    EXP_INC_IN: Bool,
    EXP_IN: UInt,
    EXP_OFF: UInt,
    FIXED_POINT: Bool,
    config: FltRenormAndRoundLogicConfig
  ): (UInt, UInt, Bool) = {
    val module = new FltRenormAndRoundLogic(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.MANT_IN := MANT_IN
    module.io.FIX_MANT_SIGN := FIX_MANT_SIGN
    module.io.SIGN := SIGN
    module.io.ZERO_LSBS := ZERO_LSBS
    module.io.EXTRA_LSB := EXTRA_LSB
    module.io.EXTRA_LSBS := EXTRA_LSBS
    module.io.NORMALIZE := NORMALIZE
    module.io.NORMALIZE2 := NORMALIZE2
    module.io.EXP_INC_IN := EXP_INC_IN
    module.io.EXP_IN := EXP_IN
    module.io.EXP_OFF := EXP_OFF
    module.io.FIXED_POINT := FIXED_POINT
    (module.io.MANT_OUT, module.io.EXP_OUT, module.io.EXP_INC_OUT)
  }
}
