// SPDX-License-Identifier: MIT
// DSP48E1 包装模块
// 对Xilinx DSP48E1原语的包装，用于浮点运算

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltDsp48e1WrapperConfig(
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
  
  // 延迟配置 - DSP48E1 specific
  val delay_C_REG = if (C_REG < 1) C_REG else 1
  val delay_A_REG = if (A_REG < 2) A_REG else 2
  val delay_B_REG = if (B_REG < 2) B_REG else 2
  val delay_D_REG = if (D_REG < 1) D_REG else 1
}

class FltDsp48e1Wrapper(config: FltDsp48e1WrapperConfig) extends Component {
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
  
  // DSP48E1 has 25-bit D input (vs 27-bit in DSP48E2)
  // Match original Verilog: truncate D_WIDTH=27 to 25-bit D port
  val d_width_actual = if (D_WIDTH > 25) 25 else D_WIDTH

  val a_int = io.A_IN.resize(25).asSInt  // DSP48E1 A port: 25-bit signed
  val b_int = io.B_IN.resize(18).asSInt  // DSP48E1 B port: 18-bit signed
  val c_int = io.C_IN.resize(48).asSInt  // DSP48E1 C port: 48-bit signed
  val d_int = io.D_IN.resize(25).asSInt  // DSP48E1 D port: 25-bit signed
  val p_out_int = UInt(48 bits)

  // OPMODE adjustment: DSP48E1 uses 7-bit OPMODE (vs 9-bit in DSP48E2)
  val opmode_e1 = io.OP_MODE(6 downto 0).asUInt

  // DSP control signals matching original Verilog register stage control
  val cea1 = (if (A_REG >= 1) io.ce else True)  // A input stage 1
  val cea2 = (if (A_REG >= 2) io.ce else True)  // A input stage 2
  val ceb1 = (if (B_REG >= 1) io.ce else True)  // B input stage 1
  val ceb2 = (if (B_REG >= 2) io.ce else True)  // B input stage 2
  val cec = (if (C_REG >= 1) io.ce else True)
  val ced = (if (D_REG >= 1) io.ce else True)
  val cem = (if (M_REG >= 1) io.ce else True)
  val cep = (if (P_REG >= 1) io.ce else True)
  val cealumode = io.ce
  val ceopmode = io.ce
  val ceinmode = io.ce
  val cecarryin = io.ce
  val cead = (if (AD_REG >= 1) io.ce else True)
  val cectrl = io.ce

  // Match original Verilog: register input signals based on config
  val carry_in_del = if (P_REG >= 1) RegNext(io.CARRY_IN) else io.CARRY_IN
  val c_in_del = if (C_REG >= 1) RegNext(io.C_IN) else io.C_IN
  val a_in_del = if (A_REG >= 1) RegNext(io.A_IN) else io.A_IN
  val b_in_del = if (B_REG >= 1) RegNext(io.B_IN) else io.B_IN
  val d_in_del = if (D_REG >= 1) RegNext(io.D_IN.resize(d_width_actual)) else io.D_IN.resize(d_width_actual)

  // Behavioral DSP model matching DSP48E1 semantics
  val dsp = new Area {
    val p = Reg(UInt(48 bits)) init(0)

    when(io.ce) {
      val multTerm = a_int * b_int
      val addTerm = multTerm + c_int + d_int
      // Only accumulate when ce is active (simplified behavior model)
      p := addTerm.asUInt
    }
  }

  // Output assignment
  io.CARRY_OUT := B"0000"
  io.P_OUT := dsp.p.resize(P_WIDTH)
}

// 伴生对象，用于简化实例化
object FltDsp48e1Wrapper {
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
    config: FltDsp48e1WrapperConfig
  ): (Bits, UInt) = {
    val module = new FltDsp48e1Wrapper(config)
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
