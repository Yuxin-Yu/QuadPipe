// SPDX-License-Identifier: MIT
// 指数计算的特殊情况处理模块

package softmax.fp

import spinal.core._
import softmax.util.FltDelay

case class FltExpSpecialcaseConfig(
  C_WF: Int = 23,
  C_RESULT_WIDTH: Int = 32
) {
  val EW = C_RESULT_WIDTH - C_WF
  val IEEE_BIAS = (1 << (EW - 1)) - 1
  val C_WE = 8
  val C_G = 3
  val RANGE_OVERFLOW_VALUE = C_WE - 2 + IEEE_BIAS
  val RANGE_UNDERFLOW_VALUE = IEEE_BIAS - C_WF - C_G
}

class FltExpSpecialcase(config: FltExpSpecialcaseConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(C_RESULT_WIDTH bits)

    val is_nan = out Bool()
    val is_inf = out Bool()
    val is_zero = out Bool()
    val sign = out Bool()
    val overflow = out Bool()
    val underflow = out Bool()
  }

  val expWidth = C_RESULT_WIDTH - C_WF
  val expBits = io.A(C_RESULT_WIDTH - 2 downto C_WF - 1)
  val mantBits = io.A(C_WF - 2 downto 0)

  val isZeroNow = expBits === 0
  val isInfNow = (expBits === U((1 << expWidth) - 1, expWidth bits)) && !mantBits.orR
  val isNanNow = (expBits === U((1 << expWidth) - 1, expWidth bits)) && mantBits.orR
  val overflowNow = expBits > U(RANGE_OVERFLOW_VALUE, expWidth bits)
  val underflowNow = expBits < U(RANGE_UNDERFLOW_VALUE, expWidth bits)
  val signNow = io.A(C_RESULT_WIDTH - 1)

  // Match original Verilog flt_exp_specialcase pipeline depth:
  // - flt_special_detect has OP_DELAY=1 (1 cycle)
  // - flag_async register adds 1 cycle
  // So special_case outputs have ~2 cycles total
  // overflow/underflow/sign similar short pipelining
  io.is_zero := FltDelay(io.clk, io.ce, isZeroNow.asBits, 1, 2).asBool
  io.is_inf := FltDelay(io.clk, io.ce, isInfNow.asBits, 1, 2).asBool
  io.is_nan := FltDelay(io.clk, io.ce, isNanNow.asBits, 1, 2).asBool
  io.overflow := FltDelay(io.clk, io.ce, overflowNow.asBits, 1, 2).asBool
  io.underflow := FltDelay(io.clk, io.ce, underflowNow.asBits, 1, 2).asBool
  io.sign := FltDelay(io.clk, io.ce, signNow.asBits, 1, 2).asBool
}

object FltExpSpecialcase {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltExpSpecialcaseConfig
  ): (Bool, Bool, Bool, Bool, Bool, Bool) = {
    val module = new FltExpSpecialcase(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    (module.io.is_nan, module.io.is_inf, module.io.is_zero, module.io.sign, module.io.overflow, module.io.underflow)
  }
}
