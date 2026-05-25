// Copyright (c) 2023-present, Guolin Wang (wangguolin@bit.edu.cn)
// All rights reserved.
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

package softmax.fp

import spinal.core._
import softmax.util.FltDelay

/**
  * 浮点解码操作延迟模块，用于处理浮点运算的解码操作和结果生成
  *
  * @param rW               结果宽度
  * @param rFw              结果小数宽度
  * @param registered       是否注册输出
  * @param speed            速度配置
  * @param reducedRange     减少范围
  * @param expAdder         指数加法器配置
  * @param updateFlagsLate  延迟更新标志
  * @param noSr             无移位寄存器
  * @param hasDivideByZero  是否有除零检测
  */
class FltDecOpLat(
    val rW: Int = 32,
    val rFw: Int = 24,
    val registered: Int = 1,
    val speed: Int = 1,
    val reducedRange: Int = 0,
    val expAdder: Int = 1,
    val updateFlagsLate: Int = 0,
    val noSr: Int = 0,
    val hasDivideByZero: Int = 0
) extends Component {
  val io = new Bundle {
    val clk                = in Bool()
    val ce                 = in Bool()
    val decOpState         = in Bits(14 bits)
    val flow               = in Bits(4 bits)
    val invalidOpIn        = in Bool()
    val divideByZeroIn     = in Bool()
    val mant               = in Bits(rFw - 1 bits)
    val exp                = in Bits(rW - rFw bits)
    val sign               = in Bool()
    val expInc             = in Bool()

    val result             = out Bits(rW bits)
    val underflow          = out Bool()
    val overflow           = out Bool()
    val divideByZero       = out Bool()
    val invalidOp          = out Bool()
  }

  private val opClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(opClockDomain) {
    // 计算局部参数
    val rEw = rW - rFw

    // 定义常量
    val fltFlowOver         = 0
    val fltFlowUnder        = 1
    val fltFlowAlmostOver   = 2
    val fltFlowJustUnder    = 3

    val fltDecOpStateExpOne        = 0
    val fltDecOpStateExpZero       = 1
    val fltDecOpStateMantMsbOne    = 2
    val fltDecOpStateMantMsbZero   = 3
    val fltDecOpStateMantLsbsOne   = 4
    val fltDecOpStateMantLsbsZero  = 5
    val fltDecOpStateSignOne       = 6
    val fltDecOpStateSignZero      = 7
    val fltDecOpStateMidBitOne     = 8
    val fltDecOpStateMidBitZero    = 9
    val fltDecOpStateMantMsbsOne   = 10
    val fltDecOpStateMantMsbsZero  = 11
    val fltDecOpStateExpLsbOne     = 12
    val fltDecOpStateExpLsbZero    = 13

    // 内部信号定义
    val expPreOp = Bits(rEw bits)
    val expOp = Reg(Bits(rEw bits)) init(0)
    val mantOp = Reg(Bits(rFw - 1 bits)) init(0)
    val signOp = Reg(Bool()) init(False)
    val underflowQ = Reg(Bool()) init(False)
    val overflowQ = Reg(Bool()) init(False)
    val invalidOpQ = Reg(Bool()) init(False)

    // 赋值
    expPreOp := io.exp

    // 处理除零延迟
    val delayDivideByZero = new FltDelay(
      width = 1,
      length = registered
    )
    delayDivideByZero.io.clk := io.clk
    delayDivideByZero.io.ce := io.ce
    delayDivideByZero.io.D := io.divideByZeroIn.asBits
    io.divideByZero := delayDivideByZero.io.Q.asBool

    // 更新标志
    when(io.ce) {
      invalidOpQ := io.invalidOpIn
      overflowQ := (io.flow(fltFlowAlmostOver) && io.expInc) || io.flow(fltFlowOver)
      underflowQ := (io.flow(fltFlowJustUnder) && !io.expInc) || io.flow(fltFlowUnder)
    }

    io.underflow := underflowQ
    io.overflow := overflowQ
    io.invalidOp := invalidOpQ

    // 更新符号
    when(io.ce) {
      when(io.decOpState(fltDecOpStateSignZero)) {
        signOp := False
      } elsewhen(io.decOpState(fltDecOpStateSignOne)) {
        signOp := True
      } otherwise {
        signOp := io.sign
      }
    }

    // 更新指数高位
    when(io.ce) {
      when(io.decOpState(fltDecOpStateExpZero)) {
        expOp(rEw - 1 downto 1) := B(0, rEw - 1 bits)
      } elsewhen(io.decOpState(fltDecOpStateExpOne)) {
        expOp(rEw - 1 downto 1) := B((BigInt(1) << (rEw - 1)) - 1, rEw - 1 bits)
      } otherwise {
        expOp(rEw - 1 downto 1) := expPreOp(rEw - 1 downto 1)
      }
    }

    // 更新指数低位
    when(io.ce) {
      when(io.decOpState(fltDecOpStateExpLsbZero)) {
        expOp(0) := False
      } elsewhen(io.decOpState(fltDecOpStateExpLsbOne)) {
        expOp(0) := True
      } otherwise {
        expOp(0) := expPreOp(0)
      }
    }

    // 更新尾数高位
    when(io.ce) {
      when(io.decOpState(fltDecOpStateMantMsbZero)) {
        mantOp(rFw - 2) := False
      } elsewhen(io.decOpState(fltDecOpStateMantMsbOne)) {
        mantOp(rFw - 2) := True
      } otherwise {
        mantOp(rFw - 2) := io.mant(rFw - 2)
      }
    }

    // 更新尾数低位
    when(io.ce) {
      when(io.decOpState(fltDecOpStateMantLsbsZero)) {
        mantOp(rFw - 3 downto 0) := B(0, rFw - 2 bits)
      } elsewhen(io.decOpState(fltDecOpStateMantLsbsOne)) {
        mantOp(rFw - 3 downto 0) := B((BigInt(1) << (rFw - 2)) - 1, rFw - 2 bits)
      } otherwise {
        mantOp(rFw - 3 downto 0) := io.mant(rFw - 3 downto 0)
      }
    }

    // 组合结果输出
    io.result := signOp ## expOp ## mantOp
  }
}
