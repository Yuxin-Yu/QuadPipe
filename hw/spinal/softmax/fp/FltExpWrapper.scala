// SPDX-License-Identifier: MIT
// 指数计算的顶层包装模块
// 集成所有指数计算的子模块，实现完整的指数运算

package softmax.fp

import spinal.core._
import spinal.lib._

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
  val Z_WIDTH = 10
  val E2A_RESULT_WIDTH = 27
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
  
  // 内部信号定义
  val is_nan = Bool()
  val is_inf = Bool()
  val is_zero = Bool()
  val sign = Bool()
  val overflow = Bool()
  val underflow = Bool()
  
  val result_e2a = UInt(E2A_RESULT_WIDTH bits)
  val result_e2zmzm1 = UInt(E2A_RESULT_WIDTH bits)
  val result_ccm = UInt(8 bits)
  val result_recomb = UInt(C_RESULT_WIDTH bits)
  val zeroFp = U(BigInt("00000000", 16), C_RESULT_WIDTH bits)
  val oneFp = U(BigInt("3f800000", 16), C_RESULT_WIDTH bits)
  val infFp = U(BigInt("7f800000", 16), C_RESULT_WIDTH bits)
  val qnanFp = U(BigInt("7fc00000", 16), C_RESULT_WIDTH bits)
  
  // 实例化特殊情况检测模块
  val specialcase = new FltExpSpecialcase(FltExpSpecialcaseConfig(
    C_WF = FW,
    C_RESULT_WIDTH = C_A_WIDTH
  ))
  
  specialcase.io.clk := io.clk
  specialcase.io.ce := io.ce
  specialcase.io.A := io.A
  
  is_nan := specialcase.io.is_nan
  is_inf := specialcase.io.is_inf
  is_zero := specialcase.io.is_zero
  sign := specialcase.io.sign
  overflow := specialcase.io.overflow
  underflow := specialcase.io.underflow
  
  // 提取Z值（简化实现）
  val z = io.A(Z_WIDTH-1 downto 0)
  
  // 实例化系数计算模块
  val ccm = new FltExpCcm(FltExpCcmConfig(
    C_WF = FW,
    C_X_WIDTH = Z_WIDTH,
    C_RESULT_WIDTH = 8
  ))
  
  ccm.io.clk := io.clk
  ccm.io.ce := io.ce
  ccm.io.x_sign := sign
  ccm.io.x := z
  
  result_ccm := ccm.io.result
  
  // 实例化e^a查找表模块
  val e2a = new FltExpE2a(FltExpE2aConfig(
    C_WF = FW,
    C_A_WIDTH = Z_WIDTH,
    C_RESULT_WIDTH = E2A_RESULT_WIDTH
  ))
  
  e2a.io.clk := io.clk
  e2a.io.ce := io.ce
  e2a.io.A := z
  
  result_e2a := e2a.io.result
  
  // 实例化e^(2*z) - 2*z - 1计算模块
  val e2zmzm1 = new FltExpE2zmzm1(FltExpE2zmzm1Config(
    C_WF = FW,
    C_Z_WIDTH = Z_WIDTH,
    C_RESULT_WIDTH = E2A_RESULT_WIDTH,
    SFM_DSP48_VER = config.SFM_DSP48_VER
  ))

  e2zmzm1.io.clk := io.clk
  e2zmzm1.io.ce := io.ce
  e2zmzm1.io.Z := z
  e2zmzm1.io.RESULT_E2A := result_e2a

  result_e2zmzm1 := e2zmzm1.io.result

  // DSP48 primitive wrapper instantiation based on SFM_DSP48_VER
  if (config.SFM_DSP48_VER == "DSP48E1") {
    val dsp48e1 = new FltDsp48e1Wrapper(FltDsp48e1WrapperConfig(
      A_WIDTH = 2, B_WIDTH = 16, C_WIDTH = 16,
      D_WIDTH = 27, P_WIDTH = 27
    ))
    dsp48e1.io.clk := io.clk
    dsp48e1.io.ce := io.ce
    dsp48e1.io.A_IN := U(0, 2 bits)
    dsp48e1.io.B_IN := U(0, 16 bits)
    dsp48e1.io.C_IN := U(0, 16 bits)
    dsp48e1.io.D_IN := U(0, 27 bits)
    dsp48e1.io.CARRY_IN := False
    dsp48e1.io.OP_MODE := B"000000000"
    dsp48e1.io.ALU_MODE := B"0000"
    dsp48e1.io.IN_MODE := B"00000"
  } else {
    val dsp48e2 = new FltDsp48e2Wrapper(FltDsp48e2WrapperConfig(
      A_WIDTH = 2, B_WIDTH = 16, C_WIDTH = 16,
      D_WIDTH = 27, P_WIDTH = 27
    ))
    dsp48e2.io.clk := io.clk
    dsp48e2.io.ce := io.ce
    dsp48e2.io.A_IN := U(0, 2 bits)
    dsp48e2.io.B_IN := U(0, 16 bits)
    dsp48e2.io.C_IN := U(0, 16 bits)
    dsp48e2.io.D_IN := U(0, 27 bits)
    dsp48e2.io.CARRY_IN := False
    dsp48e2.io.OP_MODE := B"000000000"
    dsp48e2.io.ALU_MODE := B"0000"
    dsp48e2.io.IN_MODE := B"00000"
  }
  
  // 实例化结果重组模块
  val recomb = new FltExpRecomb(FltExpRecombConfig(
    C_WF = RESULT_FW,
    C_RESULT_WIDTH = RESULT_W
  ))
  
  recomb.io.clk := io.clk
  recomb.io.ce := io.ce
  recomb.io.MANT_E2Z := result_e2zmzm1
  recomb.io.EXP_INT := result_ccm
  recomb.io.SIGN := False
  recomb.io.OVERFLOW := overflow
  recomb.io.UNDERFLOW := underflow
  
  result_recomb := recomb.io.result
  
  // 组合逻辑直接检测特殊情况（避开 FltExpSpecialcase 内部的深层流水线延迟）
  val expField = io.A(C_RESULT_WIDTH - 2 downto FW - 1)
  val mantField = io.A(FW - 2 downto 0)
  val isZeroComb = expField === 0
  val ew = C_RESULT_WIDTH - FW
  val isInfComb = expField === U((1 << ew) - 1, ew bits) && !mantField.orR
  val isNanComb = expField === U((1 << ew) - 1, ew bits) && mantField.orR
  
  // 处理特殊情况
  val final_result = UInt(C_RESULT_WIDTH bits)
  final_result := result_recomb

  when(isNanComb) {
    final_result := qnanFp
  } elsewhen(isInfComb && io.A(C_RESULT_WIDTH - 1)) {
    final_result := zeroFp
  } elsewhen(isInfComb) {
    final_result := infFp
  } elsewhen(isZeroComb) {
    final_result := oneFp
  } elsewhen(overflow && sign) {
    final_result := zeroFp
  } elsewhen(overflow) {
    final_result := infFp
  } elsewhen(underflow) {
    final_result := oneFp
  }
  
  // 输出结果
  io.RESULT := final_result
  io.OVERFLOW := overflow
  io.UNDERFLOW := underflow
  io.INVALID_OP := is_nan
}

// 伴生对象，用于简化实例化
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
