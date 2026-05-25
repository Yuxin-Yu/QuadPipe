// SPDX-License-Identifier: MIT
// 指数计算的e^(2*z) - 2*z - 1模块
// 实现e^(2*z) - 2*z - 1的计算

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltExpE2zmzm1Config(
  C_WF: Int = 23,
  C_Z_WIDTH: Int = 10,
  C_RESULT_WIDTH: Int = 27,
  SFM_DSP48_VER: String = "DSP48E2"
) {
  // 内部常量定义
  val MULT_STAGES = 2
  val ADD_STAGES = 2
  val PIPELINE_STAGES = MULT_STAGES + ADD_STAGES
}

class FltExpE2zmzm1(config: FltExpE2zmzm1Config) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val Z = in UInt(C_Z_WIDTH bits)
    val RESULT_E2A = in UInt(C_RESULT_WIDTH bits)
    
    val result = out UInt(C_RESULT_WIDTH bits)
  }

  private val expClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(expClockDomain) {
    val twoZ = io.Z << 1
    val twoZExt = twoZ.resize(C_RESULT_WIDTH)
    val resultTemp = io.RESULT_E2A - twoZExt - U(1, C_RESULT_WIDTH bits)
    val stage0 = RegNextWhen(resultTemp, io.ce) init(0)
    val stage1 = RegNextWhen(stage0, io.ce) init(0)

    io.result := stage1
  }
}

// 伴生对象，用于简化实例化
object FltExpE2zmzm1 {
  def apply(
    clk: Bool,
    ce: Bool,
    Z: UInt,
    RESULT_E2A: UInt,
    config: FltExpE2zmzm1Config
  ): UInt = {
    val module = new FltExpE2zmzm1(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.Z := Z
    module.io.RESULT_E2A := RESULT_E2A
    module.io.result
  }
}
