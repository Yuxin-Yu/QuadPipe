// Copyright (c) 2023-present, Guolin Wang (wangguolin@bit.edu.cn)
// All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package softmax.fp

import spinal.core._
import softmax.util.FltDelay

/**
  * 浮点加减逻辑模块，用于处理两个尾数的加减操作
  *
  * @param useRtl        是否使用RTL实现
  * @param cMultUsage    乘法使用配置
  * @param alignDist     对齐距离
  * @param legacy        是否使用旧版设计
  * @param lrgDelay      大尾数延迟
  * @param fw            小数宽度
  * @param speed         速度配置
  * @param registers     寄存器配置
  */
class FltAddSubLogic(
    val useRtl: Int = 0,
    val cMultUsage: Int = 0,
    val alignDist: Int = 2,
    val legacy: Int = 0,
    val lrgDelay: Int = 2,
    val fw: Int = 24,
    val speed: Int = 0,
    val registers: Bits = null
) extends Component {
  private val localRegisters = if (registers != null) registers else B"0000_0000_1010_1010"

  val io = new Bundle {
    val clk        = in Bool()
    val ce         = in Bool()
    val lrgMant    = in Bits(fw bits)
    val smlMant    = in Bits(fw + 2 bits)
    val zeroSml    = in Bool()
    val subtract   = in Bool()
    val zeros      = in Bool()
    val dist       = in Bits(2 bits)

    val sum        = out Bits(fw + 3 bits)
  }

  private val addClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  // 阶段定义
  val rnd1Stage = 0
  val rnd2Stage = 1
  val opStage = 2

  // 宽度计算
  val rnd1Width = (fw + 4) / 2
  val rnd2Width = fw + 4 - rnd1Width

  // 快速模式
  val fast = if (speed > 1) 1 else 0

  // 内部信号定义
  val smlShRnd1 = Bits(rnd1Width - 1 bits)
  val smlZRnd1 = Bits(rnd1Width - 1 bits)
  val smlSRnd1 = Bits(rnd1Width bits)
  val lrgRnd1 = Bits(rnd1Width - 3 bits)
  val lrgExtRnd1 = Bits(rnd1Width bits)

  val distRnd2 = Bits(2 bits)
  val smlShRnd2 = Bits(rnd2Width - 1 bits)
  val smlZRnd2 = Bits(rnd2Width bits)
  val smlSRnd2 = Bits(rnd2Width bits)
  val lrgRnd2 = Bits(rnd2Width - 1 bits)
  val lrgExtRnd2 = Bits(rnd2Width bits)
  val sumIntRnd2 = Bits(rnd2Width bits)
  val zeroSmlRnd2 = Bool()
  val subtractRnd2 = Bool()
  val carryRnd2 = Bool()

  // 大尾数延迟
  val delayLrgRnd1 = new FltDelay(
    width = rnd1Width - 3,
    length = lrgDelay
  )
  delayLrgRnd1.io.clk := io.clk
  delayLrgRnd1.io.ce := io.ce
  delayLrgRnd1.io.D := io.lrgMant(rnd1Width - 4 downto 0)
  lrgRnd1 := delayLrgRnd1.io.Q

  // 小尾数移位处理
  smlShRnd1 := Mux(!io.dist(1), io.smlMant(rnd1Width - 2 downto 0), io.smlMant(rnd1Width downto 2))
  smlZRnd1 := Mux(io.zeroSml, B(0, rnd1Width - 1 bits), smlShRnd1)

  // 处理减法的补码转换
  val sumTemp = Bits(rnd1Width bits)
  val carryIn1Rnd1 = Bool()
  val carryIn2Rnd1 = Bool()

  smlSRnd1 := Mux(io.subtract, ~smlZRnd1 ## io.zeros, smlZRnd1 ## io.zeros)
  lrgExtRnd1 := lrgRnd1 ## B(0, 3 bits)

  carryIn1Rnd1 := io.subtract
  carryIn2Rnd1 := Mux(io.zeros, carryIn1Rnd1, False)

  // 第一阶段加法
  val sumTempWide = UInt((rnd1Width + 1) bits)
  sumTempWide := smlSRnd1(rnd1Width - 1 downto 1).asUInt.resize(rnd1Width + 1) +
    lrgExtRnd1(rnd1Width - 1 downto 1).asUInt.resize(rnd1Width + 1) +
    carryIn2Rnd1.asUInt.resize(rnd1Width + 1)
  sumTemp := sumTempWide(rnd1Width - 1 downto 0).asBits
  carryRnd2 := sumTemp(rnd1Width - 1)

  // 延迟信号到第二阶段
  val delayLrgRnd2 = new FltDelay(
    width = rnd2Width - 1,
    length = lrgDelay
  )
  delayLrgRnd2.io.clk := io.clk
  delayLrgRnd2.io.ce := io.ce
  delayLrgRnd2.io.D := io.lrgMant(rnd2Width + rnd1Width - 5 downto rnd1Width - 3)
  lrgRnd2 := delayLrgRnd2.io.Q

  val delaySmlRnd2 = new FltDelay(
    width = rnd2Width - 1,
    length = 0
  )
  delaySmlRnd2.io.clk := io.clk
  delaySmlRnd2.io.ce := io.ce
  delaySmlRnd2.io.D := io.smlMant(rnd2Width + rnd1Width - 3 downto rnd1Width - 1)
  val smlRnd2 = delaySmlRnd2.io.Q

  val delaySubtractRnd2 = new FltDelay(
    width = 1,
    length = 0
  )
  delaySubtractRnd2.io.clk := io.clk
  delaySubtractRnd2.io.ce := io.ce
  delaySubtractRnd2.io.D := io.subtract.asBits
  subtractRnd2 := delaySubtractRnd2.io.Q.asBool

  val delayZeroSmlRnd2 = new FltDelay(
    width = 1,
    length = 0
  )
  delayZeroSmlRnd2.io.clk := io.clk
  delayZeroSmlRnd2.io.ce := io.ce
  delayZeroSmlRnd2.io.D := io.zeroSml.asBits
  zeroSmlRnd2 := delayZeroSmlRnd2.io.Q.asBool

  val delayDistRnd2 = new FltDelay(
    width = 2,
    length = 0
  )
  delayDistRnd2.io.clk := io.clk
  delayDistRnd2.io.ce := io.ce
  delayDistRnd2.io.D := io.dist
  distRnd2 := delayDistRnd2.io.Q

  // 第二阶段小尾数处理
  smlShRnd2 := Mux(!distRnd2(1), smlRnd2, B(0, 2 bits) ## smlRnd2(rnd2Width - 2 downto 2))
  smlZRnd2 := Mux(zeroSmlRnd2, B(0, rnd2Width bits), B(0, 1 bit) ## smlShRnd2)
  smlSRnd2 := Mux(subtractRnd2, ~smlZRnd2, smlZRnd2)
  lrgExtRnd2 := B(0, 1 bit) ## lrgRnd2

  // 第二阶段加法
  sumIntRnd2 := (smlSRnd2.asUInt + lrgExtRnd2.asUInt + carryRnd2.asUInt).asBits

  private val logic = new ClockingArea(addClockDomain) {
    val sumReg = Reg(Bits(fw + 3 bits)) init(0)

    when(io.ce) {
      sumReg(rnd1Width - 2 downto 0) := sumTemp(rnd1Width - 2 downto 0)
      sumReg(rnd2Width + rnd1Width - 2 downto rnd1Width - 1) := sumIntRnd2
    }
  }

  // 输出结果
  io.sum := logic.sumReg
}
