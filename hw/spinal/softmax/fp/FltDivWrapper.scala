// SPDX-License-Identifier: MIT
// 浮点除法包装模块
// 集成尾数除法和指数计算模块，处理最终结果输出

package softmax.fp

import spinal.core._
import spinal.lib._
import softmax.util._

case class FltDivWrapperConfig(
  C_MULT_USAGE: Int = 0,
  LATENCY_MANT: Int = 14,
  LATENCY_EXP: Int = 12,
  C_A_WIDTH: Int = 32,
  C_A_FRACTION_WIDTH: Int = 24,
  C_B_WIDTH: Int = 32,
  C_B_FRACTION_WIDTH: Int = 24,
  C_RESULT_WIDTH: Int = 32,
  C_RESULT_FRACTION_WIDTH: Int = 24,
  C_RATE: Int = 1
) {
  val W = C_A_WIDTH
  val FW = C_A_FRACTION_WIDTH
  val EW = C_A_WIDTH - C_A_FRACTION_WIDTH
  val DIV_STAGES = FW + 2
  val IP_STAGE = 0
  val RND_STAGE = DIV_STAGES
  val OP_STAGE = RND_STAGE + 1
  val EXP_BIAS_I = (1 << (EW - 1)) - 1
  val NEG_EXP_BIAS = EXP_BIAS_I - 1
}

class FltDivWrapper(config: FltDivWrapperConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(C_A_WIDTH bits)
    val B = in UInt(C_B_WIDTH bits)
    val ND = in Bool()
    
    val RESULT = out UInt(C_RESULT_WIDTH bits)
    val UNDERFLOW = out Bool()
    val OVERFLOW = out Bool()
    val INVALID_OP = out Bool()
    val DIVIDE_BY_ZERO = out Bool()
  }
  
  // 内部信号定义
  val a_mant_ip = Cat(U(1, 1 bits), io.A(FW-2 downto 0)).asUInt
  val b_mant_ip = Cat(U(1, 1 bits), io.B(FW-2 downto 0)).asUInt
  
  val mant_rnd = UInt(FW+3 bits)
  val msb_rnd = Bool()
  val normalize_rnd = !mant_rnd(FW+2)
  val exp_rnd = UInt(EW bits)
  val sign_rnd = Bool()
  val sign_op = Bool()
  val invalid_op_op = Bool()
  val divide_by_zero_op = Bool()
  val flow_op = Bits(4 bits)
  val state_op = Bits(14 bits)
  val exp_op = UInt(EW bits)
  val round_exp_inc_op = Bool()
  
  // 实例化尾数除法模块
  val divMant = new FltDivMant(FltDivMantConfig(
    LATENCY_MANT = LATENCY_MANT,
    FW = FW,
    RATE = C_RATE
  ))
  
  divMant.io.clk := io.clk
  divMant.io.ce := io.ce
  divMant.io.ND := io.ND
  divMant.io.N_MANT := a_mant_ip
  divMant.io.D_MANT := b_mant_ip
  
  msb_rnd := divMant.io.Q_MSB
  mant_rnd := divMant.io.Q_MANT
  
  // 实例化指数计算模块
  val divExp = new FltDivExp(FltDivExpConfig(
    LATENCY_EXP = LATENCY_EXP,
    W = W,
    EW = EW,
    FW = FW,
    DIV_STAGES = DIV_STAGES
  ))
  
  divExp.io.clk := io.clk
  divExp.io.ce := io.ce
  divExp.io.A := io.A
  divExp.io.B := io.B
  divExp.io.ROUND_EXP_INC := round_exp_inc_op
  divExp.io.NORMALIZE := msb_rnd
  
  exp_rnd := divExp.io.EXP_OUT
  sign_rnd := divExp.io.SIGN_RND
  sign_op := divExp.io.SIGN_OUT
  invalid_op_op := divExp.io.INVALID_OP
  divide_by_zero_op := divExp.io.DIVIDE_BY_ZERO
  flow_op := divExp.io.FLOW
  state_op := divExp.io.DEC_STATE
  
  // 简化的舍入和归一化处理
  val mant_op = UInt(FW-1 bits)
  val round_bit = Bool()
  val sticky_bit = Bool()
  
  // 提取舍入位和粘性位
  round_bit := mant_rnd(FW-1)
  sticky_bit := mant_rnd(FW-2 downto 0).orR
  
  // 舍入逻辑
  round_exp_inc_op := round_bit && (sticky_bit || mant_rnd(FW))
  
  // 归一化处理
  val norm_mant = Mux(normalize_rnd, mant_rnd(FW+1 downto 2), mant_rnd(FW+2 downto 3))
  mant_op := norm_mant(FW-2 downto 0)
  
  // 最终指数计算
  exp_op := exp_rnd + U(round_exp_inc_op)
  
  // 溢出和下溢检测
  val overflow = (exp_op >= U((1 << EW) - 1))
  val underflow = (exp_op <= U(0))
  
  // 构建最终结果
  val result = UInt(C_RESULT_WIDTH bits)
  result := Cat(sign_op, exp_op, mant_op).asUInt
  
  // 处理特殊情况
  when(invalid_op_op || divide_by_zero_op) {
    io.RESULT := Cat(sign_op, U((1 << EW) - 1, EW bits), U(0, FW-1 bits)).asUInt
  } elsewhen(overflow) {
    io.RESULT := Cat(sign_op, U((1 << EW) - 1, EW bits), U(0, FW-1 bits)).asUInt
  } elsewhen(underflow) {
    io.RESULT := Cat(sign_op, U(0, EW bits), U(0, FW-1 bits)).asUInt
  } otherwise {
    io.RESULT := result
  }
  
  // 输出状态标志
  io.UNDERFLOW := underflow
  io.OVERFLOW := overflow
  io.INVALID_OP := invalid_op_op
  io.DIVIDE_BY_ZERO := divide_by_zero_op
}

// 伴生对象，用于简化实例化
object FltDivWrapper {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    B: UInt,
    ND: Bool,
    config: FltDivWrapperConfig
  ): (UInt, Bool, Bool, Bool, Bool) = {
    val module = new FltDivWrapper(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.B := B
    module.io.ND := ND
    (module.io.RESULT, module.io.UNDERFLOW, module.io.OVERFLOW, module.io.INVALID_OP, module.io.DIVIDE_BY_ZERO)
  }
}
