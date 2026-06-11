

















package softmax.fp

import spinal.core._
import spinal.lib._
import softmax.util.{FltDelay, FltSpecialDetect}

case class FltToFixConvConfig(
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_RESULT_WIDTH: Int = 34,
  C_RESULT_FRACTION_WIDTH: Int = 26,
  C_HAS_ROUNDING: Int = 0
) {
  val A_W  = C_A_WIDTH
  val A_EW = C_A_WIDTH - C_A_FRACTION_WIDTH
  val A_FW = C_A_FRACTION_WIDTH
  val R_W  = C_RESULT_WIDTH
  val R_FW = C_RESULT_FRACTION_WIDTH

  def nBits(x: Int): Int = { var r = x; var n = 0; while (r >= 1) { n += 1; r /= 2 }; n }

  val HAS_OVERUNDER = 1
  val ALIGN_WIDTH = nBits(R_W - 1)
  val ZERO_DET_WIDTH = nBits(A_FW)
  val EXP_BIAS_I = (1 << (A_EW - 1)) - 1
  val MOD_BIAS_I = R_W - (R_FW + 1) + EXP_BIAS_I - 1
  val ZERO_BIAS_I = R_W - (R_FW + 1) - (R_W - A_FW) - 1 + EXP_BIAS_I
  val MOD_BIAS_WIDTH = 9
  val ZERO_BIAS_WIDTH = 8
  val SXT_ALIGN_WIDTH = if (A_EW >= MOD_BIAS_WIDTH) A_EW + 1 else MOD_BIAS_WIDTH + 1
  val SXT_ZERO_DET_WIDTH = if (ZERO_BIAS_WIDTH >= A_EW) ZERO_BIAS_WIDTH + 1 else A_EW + 1
  val MOD_BIAS = MOD_BIAS_I
  val ZERO_BIAS = ZERO_BIAS_I


  val FIX_OP_NORMAL = 0
  val FIX_OP_ZERO = 1
  val FIX_OP_MOST_POSITIVE = 2
  val FIX_OP_MOST_NEGATIVE = 3
}

class FltToFixConv(config: FltToFixConvConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(A_W bits)

    val RESULT = out UInt(R_W bits)
    val INVALID_OP = out Bool()
    val OVERFLOW = out Bool()
    val UNDERFLOW = out Bool()
  }

  private val convCd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(convCd) {

    val a_mant_p0 = io.A(A_FW - 2 downto 0)

    val a_exp_field = io.A(A_EW - 1 + A_FW - 1 downto A_FW - 1)
    val a_exp_zero_p0 = a_exp_field.resize(SXT_ZERO_DET_WIDTH)
    val a_exp_align_p0 = a_exp_field.resize(SXT_ALIGN_WIDTH)
    val a_sign_p0 = io.A(A_W - 1)

    val neg_mant_p0 = a_mant_p0 === 0
    val neg_lsb_bit_p0 = False
    val neg_round_bit_p0 = False
    val extra_bit_p0 = False
    val neg_zero_bit_p0 = True
    val neg_round_p0 = (neg_lsb_bit_p0 && neg_round_bit_p0 && neg_zero_bit_p0) ||
      (neg_round_bit_p0 && !neg_zero_bit_p0)
    val neg_mant_over_p0 = !(neg_mant_p0 && !neg_round_p0)
    val a_mant_flt_all_zero_p0 = neg_mant_p0 && !extra_bit_p0 && neg_zero_bit_p0


    val a_sign_p1 = RegNext(a_sign_p0) init(False)
    val a_mant_p1 = RegNext((True ## a_mant_p0).asUInt) init(0)
    val align_dist_p1 = RegNext((S(MOD_BIAS, SXT_ALIGN_WIDTH bits) - a_exp_align_p0.asSInt)) init(0)
    val zero_det_dist_p1 = RegNext((S(ZERO_BIAS, SXT_ZERO_DET_WIDTH bits) - a_exp_zero_p0.asSInt)) init(0)
    val a_mant_flt_all_zero_p1 = RegNext(a_mant_flt_all_zero_p0) init(False)
    val neg_mant_over_p1 = RegNext(neg_mant_over_p0) init(False)


    val u_special_detect = new FltSpecialDetect(aW = A_W, aFw = A_FW, opDelay = 1)
    u_special_detect.io.clk := io.clk
    u_special_detect.io.ce := io.ce
    u_special_detect.io.A := io.A.asBits

    val a_exp_flt_all_zero_p1 = u_special_detect.io.EXP_ALL_ONE
    val a_exp_flt_all_one_p1  = u_special_detect.io.EXP_ALL_ZERO


    val align_overflow_p1 = align_dist_p1(SXT_ALIGN_WIDTH - 1)
    val align_underflow_p1 = align_dist_p1 >= S(R_W, SXT_ALIGN_WIDTH bits)


    val op_state_p1 = UInt(2 bits)
    val invalid_op_p1 = Bool()
    val overflow_p1 = Bool()
    op_state_p1 := U(FIX_OP_NORMAL)
    invalid_op_p1 := False
    overflow_p1 := False
    when(a_exp_flt_all_zero_p1 && a_mant_flt_all_zero_p1) {
      op_state_p1 := U(FIX_OP_ZERO)
    } elsewhen (a_exp_flt_all_one_p1 && !a_mant_flt_all_zero_p1) {
      op_state_p1 := U(FIX_OP_MOST_NEGATIVE)
      invalid_op_p1 := True
    } elsewhen (a_exp_flt_all_one_p1 && a_mant_flt_all_zero_p1) {
      invalid_op_p1 := True
      when(!a_sign_p1) { op_state_p1 := U(FIX_OP_MOST_POSITIVE) } otherwise { op_state_p1 := U(FIX_OP_MOST_NEGATIVE) }
    }


    val op_state_p1_updated = UInt(2 bits)
    val overflow_p1_updated = Bool()
    val invalid_op_p1_updated = Bool()
    op_state_p1_updated := op_state_p1
    overflow_p1_updated := overflow_p1
    invalid_op_p1_updated := invalid_op_p1
    when(align_overflow_p1 && op_state_p1 === U(FIX_OP_NORMAL)) {
      when(align_dist_p1 === S(-1, SXT_ALIGN_WIDTH bits) && a_sign_p1) {
        overflow_p1_updated := neg_mant_over_p1 | overflow_p1
      } otherwise {
        overflow_p1_updated := True
      }
      when(a_sign_p1) { op_state_p1_updated := U(FIX_OP_MOST_NEGATIVE) } otherwise { op_state_p1_updated := U(FIX_OP_MOST_POSITIVE) }
      invalid_op_p1_updated := invalid_op_p1
    } elsewhen (align_underflow_p1) {
      op_state_p1_updated := U(FIX_OP_ZERO)
    }


    val op_state_pcntrl = FltDelay(io.clk, io.ce, op_state_p1_updated.asBits, 2, 1).asUInt

    val invalid_op_pr = FltDelay(io.clk, io.ce, invalid_op_p1_updated.asBits, 1, 2)(0)
    val overflow_pr   = FltDelay(io.clk, io.ce, overflow_p1_updated.asBits, 1, 2)(0)





    val zeros_pz = False
    val padded = (a_mant_p1 ## B(R_W - A_FW bits, default -> False)).asUInt
    val shiftDist = align_dist_p1(ALIGN_WIDTH - 1 downto 0).asUInt
    val aligned_comb = (padded >> shiftDist)
    val aligned_mant_pa = RegNext(RegNext(aligned_comb) init(0)) init(0)


    val a_sign_pza = FltDelay(io.clk, io.ce, a_sign_p1.asBits, 1, 2)(0)
    val aligned_mant_pza = aligned_mant_pa
    val zeros_pza = FltDelay(io.clk, io.ce, zeros_pz.asBits, 1, 1)(0)


    val round_bit_mod_pr = a_sign_pza ^ ((aligned_mant_pza(0) && !zeros_pza) ||
      (aligned_mant_pza(0) && zeros_pza && aligned_mant_pza(1)))
    val neg_mant_pza = UInt(R_W bits)
    neg_mant_pza(R_W - 2 downto 0) := Mux(a_sign_pza, ~aligned_mant_pza(R_W - 1 downto 1), aligned_mant_pza(R_W - 1 downto 1))
    neg_mant_pza(R_W - 1) := a_sign_pza
    val round_ip_pza = (False ## neg_mant_pza(R_W - 1 downto 0) ## round_bit_mod_pr).asUInt
    val round_bypass_i = (a_sign_pza ## aligned_mant_pza(R_W - 1 downto 1)).asUInt


    val round_op_pr = (round_ip_pza + 1).resize(R_W + 2)
    val round_bypass = round_bypass_i
    val a_sign_pr = a_sign_pza


    val rounded_mant_pr =
      if (C_HAS_ROUNDING == 1) round_op_pr(R_W downto 1) else round_bypass
    val round_carry_pr = (if (C_HAS_ROUNDING == 1) round_op_pr(R_W) else False) && !a_sign_pr


    val force_msb_one_pr = Reg(Bool()) init(False)
    val force_msb_zero_pr = Reg(Bool()) init(False)
    val force_lsbs_one_pr = Reg(Bool()) init(False)
    val force_lsbs_zero_pr = Reg(Bool()) init(False)
    switch(op_state_pcntrl) {
      is(U(FIX_OP_MOST_NEGATIVE)) {
        force_msb_one_pr := True;  force_msb_zero_pr := False; force_lsbs_one_pr := False; force_lsbs_zero_pr := True
      }
      is(U(FIX_OP_MOST_POSITIVE)) {
        force_msb_one_pr := False; force_msb_zero_pr := True;  force_lsbs_one_pr := True;  force_lsbs_zero_pr := False
      }
      is(U(FIX_OP_ZERO)) {
        force_msb_one_pr := False; force_msb_zero_pr := True;  force_lsbs_one_pr := False; force_lsbs_zero_pr := True
      }
      default {
        force_msb_one_pr := False; force_msb_zero_pr := False; force_lsbs_one_pr := False; force_lsbs_zero_pr := False
      }
    }

    val force_lsbs_one_pr_mod = (round_carry_pr && !force_lsbs_zero_pr && !a_sign_pr) ? True | force_lsbs_one_pr
    val force_msb_zero_pr_mod = (round_carry_pr && !force_msb_one_pr && !a_sign_pr) ? True | force_msb_zero_pr



    val overflow_i = RegNext(overflow_pr || force_lsbs_one_pr_mod) init(False)
    val invalid_op_i = RegNext(invalid_op_pr) init(False)

    val result_msb = Bool()
    when(force_msb_zero_pr_mod) { result_msb := False
    } elsewhen (force_msb_one_pr) { result_msb := True
    } otherwise { result_msb := rounded_mant_pr(R_W - 1) }

    val result_lsbs = UInt(R_W - 1 bits)
    when(force_lsbs_zero_pr) { result_lsbs := 0
    } elsewhen (force_lsbs_one_pr_mod) { result_lsbs := (default -> true)
    } otherwise { result_lsbs := rounded_mant_pr(R_W - 2 downto 0) }

    val result_i = RegNext((result_msb ## result_lsbs).asUInt) init(0)

    io.RESULT := result_i
    io.OVERFLOW := overflow_i
    io.INVALID_OP := invalid_op_i
    io.UNDERFLOW := False
  }
}

object FltToFixConv {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltToFixConvConfig
  ): (UInt, Bool, Bool, Bool) = {
    val module = new FltToFixConv(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    (module.io.RESULT, module.io.INVALID_OP, module.io.OVERFLOW, module.io.UNDERFLOW)
  }
}
