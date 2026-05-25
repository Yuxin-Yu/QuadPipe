// SPDX-License-Identifier: MIT
// 浮点除法指数计算模块
// 实现浮点除法的指数计算和特殊情况处理

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltDivExpConfig(
  LATENCY_EXP: Int = 12,
  W: Int = 32,
  EW: Int = 8,
  FW: Int = 24,
  DIV_STAGES: Int = 26
) {
  // 内部常量定义
  val FLT_FLOW_OVER = 0
  val FLT_FLOW_UNDER = 1
  val FLT_FLOW_ALMOST_OVER = 2
  val FLT_FLOW_JUST_UNDER = 3
  
  val FLT_EXT_STATE_NORMAL = 0
  val FLT_EXT_STATE_ZERO = 2
  val FLT_EXT_STATE_INF = 3
  val FLT_EXT_STATE_NAN = 1
  val FLT_EXT_STATE_MOST_POSITIVE = 7
  val FLT_EXT_STATE_MOST_NEGATIVE = 6
  val FLT_EXT_STATE_MAX = 4
  
  val FLT_STATE_NORMAL = 0
  val FLT_STATE_ZERO = 2
  val FLT_STATE_INF = 3
  val FLT_STATE_NAN = 1
  
  val FLT_DEC_OP_STATE_EXP_ONE = 0
  val FLT_DEC_OP_STATE_EXP_ZERO = 1
  val FLT_DEC_OP_STATE_MANT_MSB_ONE = 2
  val FLT_DEC_OP_STATE_MANT_MSB_ZERO = 3
  val FLT_DEC_OP_STATE_MANT_LSBS_ONE = 4
  val FLT_DEC_OP_STATE_MANT_LSBS_ZERO = 5
  val FLT_DEC_OP_STATE_SIGN_ONE = 6
  val FLT_DEC_OP_STATE_SIGN_ZERO = 7
  val FLT_DEC_OP_STATE_MID_BIT_ONE = 8
  val FLT_DEC_OP_STATE_MID_BIT_ZERO = 9
  val FLT_DEC_OP_STATE_MANT_MSBS_ONE = 10
  val FLT_DEC_OP_STATE_MANT_MSBS_ZERO = 11
  val FLT_DEC_OP_STATE_EXP_LSB_ONE = 12
  val FLT_DEC_OP_STATE_EXP_LSB_ZERO = 13
  
  val IP_STAGE = 0
  val DET_STAGE = 1
  val SIG_STAGE = 2
  val NORM_STAGE = 2
  val RND_STAGE = DIV_STAGES
  val UP_STAGE = 3
  val DEC_STAGE = DIV_STAGES
  val OP_STAGE = DIV_STAGES + 1
  
  val EXP_BIAS_I = (1 << (EW - 1)) - 1
  val MAX_EXP_THRESH_I = (1 << EW) - 2 - (EXP_BIAS_I - 1)
  val MIN_EXP_THRESH_I = 1 - (EXP_BIAS_I - 1) - 1
}

class FltDivExp(config: FltDivExpConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(W bits)
    val B = in UInt(W bits)
    val ROUND_EXP_INC = in Bool()
    val NORMALIZE = in Bool()
    
    val EXP_OUT = out UInt(EW bits)
    val SIGN_RND = out Bool()
    val SIGN_OUT = out Bool()
    val INVALID_OP = out Bool()
    val DIVIDE_BY_ZERO = out Bool()
    val FLOW = out Bits(4 bits)
    val DEC_STATE = out Bits(14 bits)
  }
  
  // 内部信号定义
  val a_sign_ip = io.A(W-1)
  val b_sign_ip = io.B(W-1)
  val prod_sign_ip = a_sign_ip ^ b_sign_ip
  
  // 提取指数和尾数
  val a_exp_ip = io.A(W-2 downto FW-1)
  val b_exp_ip = io.B(W-2 downto FW-1)
  val a_mant_ip = io.A(FW-2 downto 0)
  val b_mant_ip = io.B(FW-2 downto 0)
  
  // 检测特殊情况
  val a_mant_is_zero = (a_mant_ip === U(0))
  val b_mant_is_zero = (b_mant_ip === U(0))
  val a_exp_all_one = (a_exp_ip === U((1 << EW) - 1))
  val b_exp_all_one = (b_exp_ip === U((1 << EW) - 1))
  val a_exp_all_zero = (a_exp_ip === U(0))
  val b_exp_all_zero = (b_exp_ip === U(0))
  
  // 检测特殊值
  val a_is_inf = a_exp_all_one && a_mant_is_zero
  val b_is_inf = b_exp_all_one && b_mant_is_zero
  val a_is_nan = a_exp_all_one && !a_mant_is_zero
  val b_is_nan = b_exp_all_one && !b_mant_is_zero
  val a_is_zero = a_exp_all_zero && a_mant_is_zero
  val b_is_zero = b_exp_all_zero && b_mant_is_zero
  
  // 计算除法结果的指数
  val exp_bias = U(EXP_BIAS_I, EW+2 bits)
  val a_exp_ext = S(a_exp_ip.asBits, EW+2 bits)
  val b_exp_ext = S(b_exp_ip.asBits, EW+2 bits)
  val exp_diff = a_exp_ext - b_exp_ext
  val exp_div = exp_diff + exp_bias.asSInt
  
  // 处理特殊情况
  val invalid_op = a_is_nan || b_is_nan || (a_is_inf && b_is_inf)
  val divide_by_zero = b_is_zero && !a_is_zero
  
  // 延迟信号
  val prod_sign_det = RegNext(prod_sign_ip)
  val sign_rnd = RegNext(prod_sign_det)
  val sign_out = RegNext(sign_rnd)
  
  // 计算最终指数
  val exp_out = UInt(EW bits)
  val exp_norm = exp_div.asUInt - io.NORMALIZE.asUInt.resize(EW + 2)
  val exp_final = exp_norm + io.ROUND_EXP_INC.asUInt.resize(EW + 2)
  
  // 溢出和下溢检测
  val overflow = (exp_final >= U((1 << EW) - 1))
  val underflow = (exp_final <= U(0))
  
  // 确定最终指数
  when(invalid_op || divide_by_zero || a_is_inf || b_is_inf || a_is_zero || b_is_zero) {
    exp_out := U(0)
  } elsewhen(overflow) {
    exp_out := U((1 << EW) - 1)
  } elsewhen(underflow) {
    exp_out := U(0)
  } otherwise {
    exp_out := exp_final(EW-1 downto 0)
  }
  
  // 输出赋值
  io.EXP_OUT := exp_out
  io.SIGN_RND := sign_rnd
  io.SIGN_OUT := sign_out
  io.INVALID_OP := invalid_op
  io.DIVIDE_BY_ZERO := divide_by_zero
  io.FLOW := B"0000"
  io.DEC_STATE := B"00000000000000"
}

// 伴生对象，用于简化实例化
object FltDivExp {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    B: UInt,
    ROUND_EXP_INC: Bool,
    NORMALIZE: Bool,
    config: FltDivExpConfig
  ): (UInt, Bool, Bool, Bool, Bool, Bits, Bits) = {
    val module = new FltDivExp(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.B := B
    module.io.ROUND_EXP_INC := ROUND_EXP_INC
    module.io.NORMALIZE := NORMALIZE
    (module.io.EXP_OUT, module.io.SIGN_RND, module.io.SIGN_OUT, module.io.INVALID_OP, module.io.DIVIDE_BY_ZERO, module.io.FLOW, module.io.DEC_STATE)
  }
}
