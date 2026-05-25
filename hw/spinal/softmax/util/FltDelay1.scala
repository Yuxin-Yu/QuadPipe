// Copyright (c) 2023-present, Guolin Wang (wangguolin@bit.edu.cn)
// All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package softmax.util

import spinal.core._

/**
  * 固定延迟为1个时钟周期的浮点延迟模块
  * 当LENGTH=0时，直接输出输入信号；否则延迟1个时钟周期
  *
  * @param width  数据宽度
  * @param length 延迟长度
  */
class FltDelay1(val width: Int = 1, val length: Int = 1) extends Component {
  val io = new Bundle {
    val clk = in Bool()
    val ce  = in Bool()
    val D   = in Bits(width bits)
    val Q   = out Bits(width bits)
  }

  private val delayClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(delayClockDomain) {
    val delayReg = Reg(Bits(width bits)) init(0)

    when(io.ce) {
      delayReg := io.D
    }

    io.Q := (if (length == 0) io.D else delayReg)
  }
}
