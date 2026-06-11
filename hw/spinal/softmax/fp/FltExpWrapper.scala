






































package softmax.fp

import spinal.core._
import softmax.util.{FltDelay, FltRenormAndRoundLogic, FltRenormAndRoundLogicConfig}

case class FltExpWrapperConfig(
  C_MULT_USAGE: Int = 0,
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,
  SFM_DSP48_VER: String = "DSP48E2"
) {
  val W = C_A_WIDTH
  val FW = C_A_FRACTION_WIDTH
  val EW = C_A_WIDTH - C_A_FRACTION_WIDTH
  val RESULT_W = C_RESULT_WIDTH
  val RESULT_FW = C_RESULT_FRACTION_WIDTH
  val RESULT_EW = C_RESULT_WIDTH - C_RESULT_FRACTION_WIDTH


  val C_we = 8
  val C_wf = 23
  val C_k = 10
  val C_g = 3
  val IEEE_BIAS = (1 << (EW - 1)) - 1


  val XFix_width = 33
  val XFixTrunc_width = 10
  val Xi_width = 8
  val XiLN2_width = 34
  val Xf_width = 26
  val A_width = 10
  val Z_width = 16
  val Z_trunc_width = 6
  val e2a_width = 27
  val e2zmzm1_width = 6
  val e2zm1_width = 17
  val e2a_trunc_width = 17
  val Fr_width = 27
  val DSP_P_WIDTH = 43

  val NORM_AND_ROUND_WIDTH = 26
}

class FltExpWrapper(config: FltExpWrapperConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(C_A_WIDTH bits)

    val RESULT = out UInt(C_RESULT_WIDTH bits)
    val OVERFLOW = out Bool()
    val UNDERFLOW = out Bool()
    val INVALID_OP = out Bool()
  }

  private val cd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(cd) {
    def dly(d: Bits, w: Int, len: Int): Bits = FltDelay(io.clk, io.ce, d, w, len)


    val specialcase = new FltExpSpecialcase(FltExpSpecialcaseConfig(
      C_A_WIDTH = C_A_WIDTH, C_A_FRACTION_WIDTH = C_A_FRACTION_WIDTH,
      C_WE = C_we, C_WF = C_wf, C_G = C_g))
    specialcase.io.clk := io.clk
    specialcase.io.ce := io.ce
    specialcase.io.x := io.A
    val special_case = specialcase.io.special_case
    val input_is_overflow = specialcase.io.input_is_overflow
    val input_is_underflow = specialcase.io.input_is_underflow
    val input_sign = specialcase.io.input_sign


    val X_unsigned = (False ## io.A(C_A_WIDTH - 2 downto 0)).asUInt


    val toFix = new FltToFixConv(FltToFixConvConfig(
      C_A_WIDTH = C_A_WIDTH, C_A_FRACTION_WIDTH = C_A_FRACTION_WIDTH,
      C_RESULT_WIDTH = 34, C_RESULT_FRACTION_WIDTH = 26, C_HAS_ROUNDING = 0))
    toFix.io.clk := io.clk
    toFix.io.ce := io.ce
    toFix.io.A := X_unsigned
    val XFix_signed = toFix.io.RESULT
    val XFix = XFix_signed(32 downto 0)
    val XFixTrunc = XFix(32 downto 23)


    val Sx = dly(io.A(C_A_WIDTH - 1).asBits, 1, 4)(0)


    val ccmRecip = new FltExpCcm(FltExpCcmConfig(
      C_WF = C_wf, C_X_WIDTH = 10, C_RESULT_WIDTH = 8, C_TABLE_USAGE = 0))
    ccmRecip.io.clk := io.clk
    ccmRecip.io.ce := io.ce
    ccmRecip.io.x_sign := Sx
    ccmRecip.io.x := XFixTrunc
    val Xi = ccmRecip.io.result

    val Sx_at_Xi = dly(Sx.asBits, 1, 1)(0)


    val ccmLn2 = new FltExpCcm(FltExpCcmConfig(
      C_WF = C_wf, C_X_WIDTH = 8, C_RESULT_WIDTH = 34, C_TABLE_USAGE = 1))
    ccmLn2.io.clk := io.clk
    ccmLn2.io.ce := io.ce
    ccmLn2.io.x_sign := Sx_at_Xi
    ccmLn2.io.x := Xi
    val XiLN2 = ccmLn2.io.result

    val XFix_at_Xf = dly(XFix.asBits, 33, 2).asUInt
    val Sx_at_Xf = dly(Sx_at_Xi.asBits, 1, 1)(0)


    val Xf_ip1 = XiLN2
    val Xf_ip2 = (False ## XFix_at_Xf).asUInt
    val Xf_op_reg = RegNext(Mux(Sx_at_Xf, Xf_ip1 - Xf_ip2, Xf_ip1 + Xf_ip2)) init(0)
    val Xf = Xf_op_reg(25 downto 0)
    val A_addr = Xf(25 downto 16)
    val Z_V = Xf(15 downto 0)


    val e2a = new FltExpE2a(FltExpE2aConfig(C_WF = C_wf, C_A_WIDTH = 10, C_RESULT_WIDTH = 27))
    e2a.io.clk := io.clk
    e2a.io.ce := io.ce
    e2a.io.A := A_addr
    val e2a_r = e2a.io.result
    val e2a_trunc = e2a_r(26 downto 10)
    val Z_trunc = Z_V(15 downto 10)


    val e2zmzm1 = new FltExpE2zmzm1(FltExpE2zmzm1Config(C_WF = C_wf, C_Z_WIDTH = 6, C_RESULT_WIDTH = 6))
    e2zmzm1.io.clk := io.clk
    e2zmzm1.io.ce := io.ce
    e2zmzm1.io.Z := Z_trunc
    val e2zmzm1_r = e2zmzm1.io.result

    val Z_at_e2zm1 = dly(Z_V.asBits, 16, 1).asUInt

    val e2zm1_ip1 = e2zmzm1_r.resize(17)
    val e2zm1_ip2 = Z_at_e2zm1.resize(17)
    val e2a_full = (e2a_r ## U(0, 16 bits)).asUInt


    val Fr_full = UInt(43 bits)
    if (SFM_DSP48_VER == "DSP48E1") {
      val dsp = new FltDsp48e1Wrapper(FltDsp48e1WrapperConfig(
        A_WIDTH = 17, D_WIDTH = 17, B_WIDTH = 17, C_WIDTH = 43, P_WIDTH = 43,
        A_SIGNED = 0, B_SIGNED = 0, C_SIGNED = 0, D_SIGNED = 0,
        USE_DPORT = 1, USE_MULTIPLY = 1,
        A_REG = 1, AD_REG = 0, B_REG = 1, C_REG = 2, D_REG = 1, M_REG = 1, P_REG = 1, OP_REG = 0, INMODE_REG = 0))
      dsp.io.clk := io.clk; dsp.io.ce := io.ce
      dsp.io.A_IN := e2zm1_ip1; dsp.io.D_IN := e2zm1_ip2
      dsp.io.B_IN := e2a_trunc; dsp.io.C_IN := e2a_full
      dsp.io.CARRY_IN := False
      dsp.io.OP_MODE := B"000110101"; dsp.io.ALU_MODE := B"0000"; dsp.io.IN_MODE := B"00100"
      Fr_full := dsp.io.P_OUT
    } else {
      val dsp = new FltDsp48e2Wrapper(FltDsp48e2WrapperConfig(
        A_WIDTH = 17, D_WIDTH = 17, B_WIDTH = 17, C_WIDTH = 43, P_WIDTH = 43,
        A_SIGNED = 0, B_SIGNED = 0, C_SIGNED = 0, D_SIGNED = 0,
        USE_DPORT = 1, USE_MULTIPLY = 1,
        A_REG = 1, AD_REG = 0, B_REG = 1, C_REG = 2, D_REG = 1, M_REG = 1, P_REG = 1, OP_REG = 0, INMODE_REG = 0))
      dsp.io.clk := io.clk; dsp.io.ce := io.ce
      dsp.io.A_IN := e2zm1_ip1; dsp.io.D_IN := e2zm1_ip2
      dsp.io.B_IN := e2a_trunc; dsp.io.C_IN := e2a_full
      dsp.io.CARRY_IN := False
      dsp.io.OP_MODE := B"000110101"; dsp.io.ALU_MODE := B"0000"; dsp.io.IN_MODE := B"00100"
      Fr_full := dsp.io.P_OUT
    }

    val Fr = Fr_full(42 downto 16)
    val Fr_less_than_one = ~Fr(26)


    val Xi_at_op = dly(Xi.asBits, 8, 7).asUInt
    val Sx_at_op = dly(Sx_at_Xf.asBits, 1, 6)(0)
    val Fr_less_than_one_at_res_exp = dly(Fr_less_than_one.asBits, 1, 1)(0)


    val renorm = new FltRenormAndRoundLogic(FltRenormAndRoundLogicConfig(
      FW = 24, EW = 8, CONFIG_IMP_TYPE = 1, CONFIG_LEGACY = 0, EXP_INC = 1, NORM_BITS = 1))
    renorm.io.clk := io.clk; renorm.io.ce := io.ce
    renorm.io.MANT_IN := Fr(26 downto 1)
    renorm.io.FIX_MANT_SIGN := False
    renorm.io.SIGN := False
    renorm.io.ZERO_LSBS := True
    renorm.io.EXTRA_LSB := Fr(0)
    renorm.io.EXTRA_LSBS := B"00"
    renorm.io.NORMALIZE := Fr_less_than_one
    renorm.io.NORMALIZE2 := False
    renorm.io.EXP_INC_IN := False
    renorm.io.EXP_IN := U(0, 8 bits)
    renorm.io.EXP_OFF := U(0, 8 bits)
    renorm.io.FIXED_POINT := False
    val res_mant = renorm.io.MANT_OUT
    val round_overflowed = renorm.io.EXP_INC_OUT


    val Xi_padded = (False ## Xi_at_op).asUInt
    val res_bias = UInt(9 bits)
    when(Fr_less_than_one_at_res_exp && round_overflowed) { res_bias := U(IEEE_BIAS, 9 bits)
    } elsewhen (Fr_less_than_one_at_res_exp) { res_bias := U(IEEE_BIAS - 1, 9 bits)
    } elsewhen (round_overflowed) { res_bias := U(IEEE_BIAS + 1, 9 bits)
    } otherwise { res_bias := U(IEEE_BIAS, 9 bits) }
    val res_exp = RegNext(Mux(Sx_at_op, res_bias - Xi_padded, res_bias + Xi_padded)) init(0)


    val unbiased_is_127 = Xi_at_op === U(127, 8 bits)
    val unbiased_lower_not_all_zeros = Xi_at_op(6 downto 0) =/= 0
    val xi_top = Xi_at_op(7)
    val output_exp_gt_eq_255_1_i = Bool()
    when(Sx_at_op) { output_exp_gt_eq_255_1_i := False
    } elsewhen (xi_top && !(Fr_less_than_one_at_res_exp && !round_overflowed)) { output_exp_gt_eq_255_1_i := True
    } elsewhen (!Fr_less_than_one_at_res_exp && round_overflowed) { output_exp_gt_eq_255_1_i := unbiased_is_127
    } otherwise { output_exp_gt_eq_255_1_i := False }
    val output_exp_gt_eq_255_2_i = Bool()
    when(Sx_at_op) { output_exp_gt_eq_255_2_i := False
    } elsewhen (xi_top && (Fr_less_than_one_at_res_exp && !round_overflowed)) { output_exp_gt_eq_255_2_i := unbiased_lower_not_all_zeros
    } otherwise { output_exp_gt_eq_255_2_i := False }
    val output_exp_gt_eq_255_1 = dly(output_exp_gt_eq_255_1_i.asBits, 1, 1)(0)
    val output_exp_gt_eq_255_2 = dly(output_exp_gt_eq_255_2_i.asBits, 1, 1)(0)
    val output_is_overflow = output_exp_gt_eq_255_1 || output_exp_gt_eq_255_2


    val res_bias_sub1 = UInt(9 bits)
    when(Fr_less_than_one_at_res_exp && round_overflowed) { res_bias_sub1 := U(IEEE_BIAS - 1, 9 bits)
    } elsewhen (Fr_less_than_one_at_res_exp) { res_bias_sub1 := U(IEEE_BIAS - 2, 9 bits)
    } elsewhen (round_overflowed) { res_bias_sub1 := U(IEEE_BIAS, 9 bits)
    } otherwise { res_bias_sub1 := U(IEEE_BIAS - 1, 9 bits) }
    val Xi_gt_bias_sub1 = Xi_padded > res_bias_sub1
    val output_is_underflow_i = Mux(Sx_at_op, Xi_gt_bias_sub1, False)
    val output_is_underflow = dly(output_is_underflow_i.asBits, 1, 1)(0)

    val res_mant_at_recomb = dly(res_mant.asBits, C_RESULT_FRACTION_WIDTH - 1, 1).asUInt


    val recomb = new FltExpRecomb(FltExpRecombConfig(
      EXPONENT_WIDTH = C_we, MANTISSA_WIDTH = C_wf,
      C_RESULT_WIDTH = C_RESULT_WIDTH, C_RESULT_FRACTION_WIDTH = C_RESULT_FRACTION_WIDTH))
    recomb.io.clk := io.clk; recomb.io.ce := io.ce
    recomb.io.special_case := special_case
    recomb.io.input_is_overflow := input_is_overflow
    recomb.io.input_is_underflow := input_is_underflow
    recomb.io.input_sign := input_sign
    recomb.io.output_is_overflow := output_is_overflow
    recomb.io.output_is_underflow := output_is_underflow
    recomb.io.res_sign := False
    recomb.io.res_exponent := res_exp(C_we - 1 downto 0)
    recomb.io.res_mantissa := res_mant_at_recomb

    io.RESULT := recomb.io.result
    io.OVERFLOW := recomb.io.overflow
    io.UNDERFLOW := recomb.io.underflow

    io.INVALID_OP := special_case === B"01"
  }
}


object FltExpWrapper {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltExpWrapperConfig
  ): (UInt, Bool, Bool, Bool) = {
    val module = new FltExpWrapper(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    (module.io.RESULT, module.io.OVERFLOW, module.io.UNDERFLOW, module.io.INVALID_OP)
  }
}


