// SPDX-License-Identifier: MIT
// DSP48E2 包装模块
// 对Xilinx DSP48E2原语的包装，用于浮点运算

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltDsp48e2WrapperConfig(
  A_WIDTH: Int = 2,
  B_WIDTH: Int = 16,
  C_WIDTH: Int = 16,
  D_WIDTH: Int = 27,
  P_WIDTH: Int = 16,
  A_SIGNED: Int = 0,
  B_SIGNED: Int = 0,
  C_SIGNED: Int = 0,
  D_SIGNED: Int = 0,
  CASCADE_A: Int = 0,
  CASCADE_B: Int = 0,
  A_REG: Int = 0,
  AD_REG: Int = 0,
  B_REG: Int = 0,
  C_REG: Int = 0,
  D_REG: Int = 0,
  M_REG: Int = 0,
  P_REG: Int = 0,
  OP_REG: Int = 0,
  INMODE_REG: Int = 0,
  A_CASCADE_REG: Int = -1,
  B_CASCADE_REG: Int = -1,
  USE_DPORT: Int = 0,
  USE_MULTIPLY: Int = 0,
  USE_PATTERN_DETECT: Int = 0,
  MASK: BigInt = BigInt("3fffffffffff", 16),
  MASK_FROM_C: Int = 0,
  USE_SIMD: String = "ONE48"
) {
  // 内部常量定义
  val USE_MULT = if (USE_MULTIPLY == 1) "MULTIPLY" else "NONE"
  val AMULTSEL = if (USE_DPORT == 1) "AD" else "A"
  
  // 延迟配置
  val delay_C_REG = if (C_REG < 1) C_REG else 1
  val delay_A_REG = if (A_REG < 2) A_REG else 2
  val delay_B_REG = if (B_REG < 2) B_REG else 2
  val delay_D_REG = if (D_REG < 1) D_REG else 1
}

class FltDsp48e2Wrapper(config: FltDsp48e2WrapperConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A_IN = in UInt(A_WIDTH bits)
    val B_IN = in UInt(B_WIDTH bits)
    val C_IN = in UInt(C_WIDTH bits)
    val D_IN = in UInt(D_WIDTH bits)
    val CARRY_IN = in Bool()
    val OP_MODE = in Bits(9 bits)
    val ALU_MODE = in Bits(4 bits)
    val IN_MODE = in Bits(5 bits)
    
    val CARRY_OUT = out Bits(4 bits)
    val P_OUT = out UInt(P_WIDTH bits)
  }
  
  // 内部信号定义
  val a_int = io.A_IN.resize(30).asSInt
  val b_int = io.B_IN.resize(18).asSInt
  val c_int = io.C_IN.resize(48).asSInt
  val d_int = io.D_IN.resize(27).asSInt
  val p_out_int = UInt(48 bits)
  
  // 延迟处理
  val carry_in_del = RegNext(io.CARRY_IN)
  val c_in_del = RegNext(io.C_IN)
  val a_in_del = RegNext(io.A_IN)
  val b_in_del = RegNext(io.B_IN)
  val d_in_del = RegNext(io.D_IN)
  
  // DSP控制信号
  val cea1 = io.ce
  val cea2 = io.ce
  val cealumode = io.ce
  val cead = io.ce
  val ceinmode = io.ce
  val ceb1 = io.ce
  val ceb2 = io.ce
  val cec = io.ce
  val ced = io.ce
  val cecarryin = io.ce
  val cectrl = io.ce
  val cem = io.ce
  val cep = io.ce
  
  // 使用SpinalHDL的DSP库实现（简化版本）
  // 注意：实际实现需要使用SpinalHDL的DSP原语或自定义RTL
  val dsp = new Area {
    val p = Reg(UInt(48 bits))
    
    // 简化的DSP行为模型
    when(io.ce) {
      p := (a_int.asUInt * b_int.asUInt) + c_int.asUInt + d_int.asUInt
    }
  }
  
  // 输出赋值
  io.CARRY_OUT := "0000"  // 简化实现，实际需要根据DSP输出设置
  io.P_OUT := dsp.p.resize(P_WIDTH)
}

// 伴生对象，用于简化实例化
object FltDsp48e2Wrapper {
  def apply(
    clk: Bool,
    ce: Bool,
    A_IN: UInt,
    B_IN: UInt,
    C_IN: UInt,
    D_IN: UInt,
    CARRY_IN: Bool,
    OP_MODE: Bits,
    ALU_MODE: Bits,
    IN_MODE: Bits,
    config: FltDsp48e2WrapperConfig
  ): (Bits, UInt) = {
    val module = new FltDsp48e2Wrapper(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A_IN := A_IN
    module.io.B_IN := B_IN
    module.io.C_IN := C_IN
    module.io.D_IN := D_IN
    module.io.CARRY_IN := CARRY_IN
    module.io.OP_MODE := OP_MODE
    module.io.ALU_MODE := ALU_MODE
    module.io.IN_MODE := IN_MODE
    (module.io.CARRY_OUT, module.io.P_OUT)
  }
}
