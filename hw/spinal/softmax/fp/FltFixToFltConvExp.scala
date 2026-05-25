// SPDX-License-Identifier: MIT
// 定点数到浮点数转换的指数计算模块
// 实现定点数到浮点数转换过程中的指数计算和调整

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltFixToFltConvExpConfig(
  A_W: Int = 32,
  A_FW: Int = 24,
  R_W: Int = 32,
  R_FW: Int = 24,
  R_EW: Int = 8,
  A_UNSIGNED: Int = 0,
  NORM_W: Int = 8,
  NORM_STAGES: Int = 3
) {
  val EXP_BIAS_I = (1 << (R_EW-1)) - 1
  val ADJ_BIAS_I = EXP_BIAS_I + (A_W - A_FW) - 1
  val IP_STAGE = 0
  val Z_DET_STAGE = 1 - A_UNSIGNED
  val NORM_STAGE = 2 - A_UNSIGNED
  val RND_STAGE = Z_DET_STAGE + NORM_STAGES
  val OP_STAGE = RND_STAGE + 1
}

class FltFixToFltConvExp(config: FltFixToFltConvExpConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(A_W bits)
    val ROUND_EXP_INC = in Bool()
    val ALL_ZERO = in Bool()
    val NORM_SHIFT = in UInt(NORM_W bits)
    
    val EXP_OUT = out UInt(R_EW bits)
    val OP_STATE = out Bits(12 bits)
    val SIGN_OUT = out Bool()
  }
  
  private val convClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  // 内部信号定义
  val ADJ_BIAS = U(ADJ_BIAS_I, R_EW bits)
  val a_sign = io.A(A_W-1)
  val exp_norm = ADJ_BIAS - io.NORM_SHIFT(R_EW-1 downto 0)

  // 延迟信号（显式绑定到 io.clk，并由 io.ce 控制推进）
  val sign_out_w = Bool()
  val flt_all_zero_del = Bool()
  val exp_norm_del = UInt(R_EW bits)

  private val logic = new ClockingArea(convClockDomain) {
    val signPipe0 = Reg(Bool()) init(False)
    val signPipe1 = Reg(Bool()) init(False)
    val signPipe2 = Reg(Bool()) init(False)
    val signPipe3 = Reg(Bool()) init(False)
    val allZeroPipe0 = Reg(Bool()) init(False)
    val allZeroPipe1 = Reg(Bool()) init(False)
    val expNormPipe = Reg(UInt(R_EW bits)) init(0)

    when(io.ce) {
      signPipe0 := a_sign
      signPipe1 := signPipe0
      signPipe2 := signPipe1
      signPipe3 := signPipe2
      allZeroPipe0 := io.ALL_ZERO
      allZeroPipe1 := allZeroPipe0
      expNormPipe := exp_norm
    }

    sign_out_w := signPipe3
    flt_all_zero_del := allZeroPipe1
    exp_norm_del := expNormPipe
  }
  
  val exp_norm_loc = UInt(R_EW bits)
  val set_exp_zero = flt_all_zero_del
  val set_exp_one = False
  
  // 计算exp_norm_loc
  exp_norm_loc := Mux(set_exp_zero, U(0, R_EW bits), 
                 Mux(set_exp_one, U(1, R_EW bits), exp_norm_del))
  
  val round_exp_inc_loc = Mux(set_exp_zero || set_exp_one, False, io.ROUND_EXP_INC)
  
  // 输出赋值
  io.EXP_OUT := exp_norm_loc + round_exp_inc_loc.asUInt.resize(R_EW)
  io.OP_STATE := Mux(flt_all_zero_del, B"000000101000", B"000000000000")
  io.SIGN_OUT := (if (A_UNSIGNED == 0) sign_out_w else False)
}

// 伴生对象，用于简化实例化
object FltFixToFltConvExp {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    ROUND_EXP_INC: Bool,
    ALL_ZERO: Bool,
    NORM_SHIFT: UInt,
    config: FltFixToFltConvExpConfig
  ): (UInt, Bits, Bool) = {
    val module = new FltFixToFltConvExp(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.ROUND_EXP_INC := ROUND_EXP_INC
    module.io.ALL_ZERO := ALL_ZERO
    module.io.NORM_SHIFT := NORM_SHIFT
    (module.io.EXP_OUT, module.io.OP_STATE, module.io.SIGN_OUT)
  }
}
