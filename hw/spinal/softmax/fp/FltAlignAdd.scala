// Copyright (c) 2023-present, Guolin Wang (wangguolin@bit.edu.cn)
// All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package softmax.fp

import spinal.core._
import softmax.util.FltDelay

/**
  * 浮点对齐加法模块，用于将较小的尾数对齐并与较大的尾数相加
  *
  * @param cMultUsage    乘法使用配置
  * @param abFw         小数宽度
  * @param distWidth     距离宽度
  * @param zDetWidth     零检测宽度
  * @param speed         速度配置
  * @param registers     寄存器配置
  */
class FltAlignAdd(
    val cMultUsage: Int = 0,
    val abFw: Int = 24,
    val distWidth: Int = 9,
    val zDetWidth: Int = 7,
    val speed: Int = 0,
    val registers: Bits = null
) extends Component {
  private val localRegisters = if (registers != null) registers else B"0000_0101_0101_0101"

  val io = new Bundle {
    val clk            = in Bool()
    val ce             = in Bool()
    val aFrac          = in Bits(abFw - 1 bits)
    val bFrac          = in Bits(abFw - 1 bits)

    val zeroLargest     = in Bool()
    val zeroSmallest    = in Bool()
    val bLargest        = in Bool()
    val distBit0        = in Bool()
    val dist           = in Bits(distWidth bits)

    val subtract        = in Bool()

    val sum             = out Bits(abFw + 3 bits)
    val zeros          = out Bool()
  }

  // 实现类型定义
  val fltPtImpLogic = 0
  val fltPtImpDsp48 = 1
  val fltPtImpPrim = 2

  // 添加子类型和阶段定义
  val addsubType = fltPtImpLogic
  val addsubAlignBits = 1
  val addmuxAlignBits = 1
  val possLastBits = addsubAlignBits + addmuxAlignBits

  val useMuxForAlign = 1
  val useAddsubForAlign = 0
  val addsubStages = 2

  val muxStage = 0
  val alignStage = 1
  val addStage = 3
  val preaddStage = 2
  val zDetStage = 5

  val mantWidth = abFw
  val shiftedSmallWidth = abFw + 1
  val alignedWidth = abFw + 2

  val aMantMux = Bits(mantWidth bits)
  val bMantMux = Bits(mantWidth bits)
  val smlShiftMux = Bits(shiftedSmallWidth bits)
  val lrgMantMux = Bits(mantWidth bits)
  val smlMantMux = Bits(mantWidth bits)
  val lrgMantZMux = Bits(mantWidth bits)
  val absDistIntMux = Bits(distWidth bits)
  val absDistMux = Bits(distWidth bits)
  val zerosDetShMux = Bits(zDetWidth bits)

  val zeroAlignAlign = Bool()
  val absDistAlign = Bits(distWidth - 1 bits)
  val smlMantAlign = Bits(shiftedSmallWidth bits)
  val zerosDetAlign = Bits(zDetWidth bits)

  val zeroSmlPreadd = Bool()
  val zeroSmlUpPreadd = Bool()
  val zeroBPreadd = Bool()
  val subtractAdd = Bool()
  val zeroSmlAdd = Bool()
  val zerosAdd = Bool()
  val alignedMantAdd = Bits(alignedWidth bits)

  aMantMux := B"1" ## io.aFrac
  bMantMux := B"1" ## io.bFrac

  lrgMantMux := Mux(io.bLargest, bMantMux, aMantMux)
  lrgMantZMux := Mux(io.zeroLargest, B(0, mantWidth bits), lrgMantMux)
  smlMantMux := Mux(io.bLargest, aMantMux, bMantMux)
  smlShiftMux := Mux(io.distBit0, B"0" ## smlMantMux, smlMantMux ## B"0")

  val delaySmlMantAlign = new FltDelay(width = shiftedSmallWidth, length = 1)
  delaySmlMantAlign.io.clk := io.clk
  delaySmlMantAlign.io.ce := io.ce
  delaySmlMantAlign.io.D := smlShiftMux
  smlMantAlign := delaySmlMantAlign.io.Q

  zerosDetShMux := B(0, zDetWidth bits)
  val delayZerosDetAlign = new FltDelay(width = zDetWidth, length = 1)
  delayZerosDetAlign.io.clk := io.clk
  delayZerosDetAlign.io.ce := io.ce
  delayZerosDetAlign.io.D := zerosDetShMux
  zerosDetAlign := delayZerosDetAlign.io.Q

  absDistIntMux := Mux(io.dist(distWidth - 1), ~io.dist, io.dist)
  absDistMux := (absDistIntMux.asUInt + io.dist(distWidth - 1).asUInt).asBits

  val delayAbsDistAlign = new FltDelay(width = distWidth - 1, length = 1)
  delayAbsDistAlign.io.clk := io.clk
  delayAbsDistAlign.io.ce := io.ce
  delayAbsDistAlign.io.D := absDistMux(distWidth - 2 downto 0)
  absDistAlign := delayAbsDistAlign.io.Q

  zeroAlignAlign := absDistAlign.asUInt > U(abFw + 1, distWidth - 1 bits)

  val delayZeroBPreadd = new FltDelay(width = 1, length = 0)
  delayZeroBPreadd.io.clk := io.clk
  delayZeroBPreadd.io.ce := io.ce
  delayZeroBPreadd.io.D := zeroAlignAlign.asBits
  zeroBPreadd := delayZeroBPreadd.io.Q.asBool

  val alignment = new FltAlignment(
    abFw = abFw,
    ipWidth = shiftedSmallWidth,
    opWidth = alignedWidth,
    distWidth = distWidth - 1,
    zDetWidth = zDetWidth,
    possLastBits = possLastBits
  )
  alignment.io.clk := io.clk
  alignment.io.ce := io.ce
  alignment.io.dataIp := smlMantAlign
  alignment.io.dist := absDistAlign
  alignment.io.zerosDetIp := zerosDetAlign
  alignedMantAdd := alignment.io.dataOp
  zerosAdd := alignment.io.zeros

  val delayZeros = new FltDelay(width = 1, length = 1)
  delayZeros.io.clk := io.clk
  delayZeros.io.ce := io.ce
  delayZeros.io.D := zerosAdd.asBits
  io.zeros := delayZeros.io.Q.asBool

  val delaySubtractAdd = new FltDelay(width = 1, length = 2)
  delaySubtractAdd.io.clk := io.clk
  delaySubtractAdd.io.ce := io.ce
  delaySubtractAdd.io.D := io.subtract.asBits
  subtractAdd := delaySubtractAdd.io.Q.asBool

  val delayZeroSmlPreadd = new FltDelay(width = 1, length = 1)
  delayZeroSmlPreadd.io.clk := io.clk
  delayZeroSmlPreadd.io.ce := io.ce
  delayZeroSmlPreadd.io.D := io.zeroSmallest.asBits
  zeroSmlPreadd := delayZeroSmlPreadd.io.Q.asBool

  zeroSmlUpPreadd := zeroBPreadd || zeroSmlPreadd

  val delayZeroSmlAdd = new FltDelay(width = 1, length = 1)
  delayZeroSmlAdd.io.clk := io.clk
  delayZeroSmlAdd.io.ce := io.ce
  delayZeroSmlAdd.io.D := zeroSmlUpPreadd.asBits
  zeroSmlAdd := delayZeroSmlAdd.io.Q.asBool

  val addSub = new FltAddSubLogic(
    cMultUsage = cMultUsage,
    alignDist = 2,
    legacy = 0,
    lrgDelay = 2,
    fw = abFw,
    speed = speed
  )
  addSub.io.clk := io.clk
  addSub.io.ce := io.ce
  addSub.io.lrgMant := lrgMantZMux
  addSub.io.smlMant := alignedMantAdd
  addSub.io.zeroSml := zeroSmlAdd
  addSub.io.subtract := subtractAdd
  addSub.io.zeros := zerosAdd
  addSub.io.dist := B"00"

  io.sum := addSub.io.sum
}
