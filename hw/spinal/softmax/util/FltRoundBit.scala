// Copyright (c) 2023-present, Guolin Wang (wangguolin@bit.edu.cn)
// All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package softmax.util

import spinal.core._

/**
  * 浮点舍入位计算模块，用于决定浮点运算中是否需要进行进位
  *
  * @param fixSupport   是否支持定点运算
  * @param useRtl       是否使用RTL实现
  * @param normBits     归一化位数
  * @param registers    寄存器配置
  */
class FltRoundBit(
    val fixSupport: Boolean = false,
    val useRtl: Boolean = false,
    val normBits: Int = 1,
    val registers: Bits = B"00000"
) extends Component {
  val io = new Bundle {
    val clk            = in Bool()
    val ce             = in Bool()
    val mantLsbs       = in Bits(3 bits)
    val zero           = in Bool()
    val extraLsb       = in Bool()
    val extraLsbs      = in Bits(2 bits)
    val normalize      = in Bits(2 bits)
    val fixedPoint     = in Bool()
    val fixMantSign    = in Bool()
    val sign           = in Bool()

    val roundCarry     = out Bool()
  }

  // 内部信号定义
  val truncate              = False
  val rndInf                = False
  val infNeg                = False

  // 兼容参数保留给后续接口统一使用，本模块当前走纯逻辑路径。
  val localUseRtl = useRtl

  val nonZeroTruncatedPart  = Bool()
  val fixNegTruncate        = Bool()
  val zeroBit               = Bool()
  val lsb                   = Bool()
  val round                 = Bool()
  val roundBit              = Bool()
  val aIp                   = Bits(3 bits)
  val bIp                   = Bits(3 bits)
  val aBSum                 = UInt(4 bits)

  // 计算非零截断部分
  nonZeroTruncatedPart := io.mantLsbs(0) || io.mantLsbs(1) || !io.zero || io.extraLsb

  // 计算定点负数截断
  fixNegTruncate := (if (fixSupport) (io.fixedPoint && truncate && io.fixMantSign) else False)

  // 计算零位
  when(io.normalize(0)) {
    zeroBit := io.zero && !io.extraLsb && !io.extraLsbs(0) && !io.extraLsbs(1)
  } otherwise {
    zeroBit := io.zero && !io.extraLsb && !io.extraLsbs(0) && !io.extraLsbs(1) && !io.mantLsbs(0)
  }

  // 计算最低有效位
  when(io.normalize(0)) {
    lsb := io.mantLsbs(1)
  } otherwise {
    lsb := io.mantLsbs(2)
  }

  // 计算舍入标志
  when(io.normalize(0)) {
    round := !truncate && io.mantLsbs(0)
  } otherwise {
    round := !truncate && io.mantLsbs(1)
  }

  // 计算舍入位
  roundBit := (round && !zeroBit) || (round && zeroBit && lsb)

  // 计算进位
  bIp := B"100"
  aIp := Cat(!roundBit, fixNegTruncate, nonZeroTruncatedPart)
  aBSum := ((bIp ^ aIp).asUInt.resize(4) +^ bIp.asUInt.resize(4) +^ U(1, 4 bits)).resized
  io.roundCarry := aBSum(3)
}
