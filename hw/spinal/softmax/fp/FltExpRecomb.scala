// SPDX-License-Identifier: MIT
// 指数计算的结果重组模块
// 保留原 RTL 的输出边界，当前先实现最小可验证的重组与饱和路径

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltExpRecombConfig(
  C_WF: Int = 24,
  C_RESULT_WIDTH: Int = 32
) {
  val EW = C_RESULT_WIDTH - C_WF
}

class FltExpRecomb(config: FltExpRecombConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val MANT_E2Z = in UInt(C_WF + 3 bits)
    val EXP_INT = in UInt(EW bits)
    val SIGN = in Bool()
    val OVERFLOW = in Bool()
    val UNDERFLOW = in Bool()

    val result = out UInt(C_RESULT_WIDTH bits)
  }

  private val expClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(expClockDomain) {
    val mantissa = io.MANT_E2Z(C_WF - 2 downto 0).asBits
    val normalResult = (io.SIGN ## io.EXP_INT.asBits ## mantissa).asUInt
    val infResult = U(BigInt("7f800000", 16), C_RESULT_WIDTH bits)
    val zeroResult = U(0, C_RESULT_WIDTH bits)
    val resultNext = UInt(C_RESULT_WIDTH bits)

    resultNext := normalResult
    when(io.OVERFLOW) {
      resultNext := infResult
    } elsewhen(io.UNDERFLOW) {
      resultNext := zeroResult
    }

    io.result := RegNextWhen(resultNext, io.ce) init(0)
  }
}

object FltExpRecomb {
  def apply(
    clk: Bool,
    ce: Bool,
    MANT_E2Z: UInt,
    EXP_INT: UInt,
    SIGN: Bool,
    OVERFLOW: Bool,
    UNDERFLOW: Bool,
    config: FltExpRecombConfig
  ): UInt = {
    val module = new FltExpRecomb(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.MANT_E2Z := MANT_E2Z
    module.io.EXP_INT := EXP_INT
    module.io.SIGN := SIGN
    module.io.OVERFLOW := OVERFLOW
    module.io.UNDERFLOW := UNDERFLOW
    module.io.result
  }
}
