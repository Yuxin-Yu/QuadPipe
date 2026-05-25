// SPDX-License-Identifier: MIT
// 浮点除法尾数计算模块
// 实现浮点除法的尾数计算核心算法

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltDivMantConfig(
  LATENCY_MANT: Int = 14,
  FW: Int = 24,
  RATE: Int = 1
) {
  // 计算函数
  def getLastStage(bitsLeft: Int, ch: Int): Int = {
    var bits = bitsLeft
    while (bits > ch) {
      bits -= ch
    }
    bits
  }
  
  def getChunks(lFw: Int, lLatency: Int): Int = {
    if (lLatency == 0) {
      10000
    } else {
      (lFw + lLatency - 1) / lLatency
    }
  }
  
  def getStages(lFw: Int, lRate: Int, lLatency: Int): Int = {
    if (lLatency == 0) {
      lFw
    } else {
      (lFw + lRate - 1) / lRate
    }
  }
  
  def getNStages(lFw: Int, lStages: Int, lChunks: Int, lLatency: Int): Int = {
    if (lLatency == 0) {
      lFw
    } else {
      val temp = ((lStages + lChunks - 1) / lChunks) * lChunks
      if (temp > lFw) {
        lFw
      } else {
        temp
      }
    }
  }
  
  val latency = LATENCY_MANT
  val chunks = getChunks(FW + 2, latency)
  val stages = getStages(FW + 2, RATE, latency)
  val nStages = getNStages(FW + 2, stages, chunks, latency)
  val lastStage = getLastStage(nStages, chunks)
}

class FltDivMant(config: FltDivMantConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val ND = in Bool()
    val N_MANT = in UInt(FW bits)
    val D_MANT = in UInt(FW bits)
    
    val Q_MSB = out Bool()
    val Q_MANT = out UInt(FW+3 bits)
  }

  private val mantClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(mantClockDomain) {
    val dividend = Cat(U(1), io.N_MANT(FW - 2 downto 0))
    val divisor = Cat(U(1), io.D_MANT(FW - 2 downto 0))

    val quotient = Reg(UInt(FW + 3 bits)) init(0)
    val divisorZero = divisor === 0
    val divisionResult = UInt(FW + 3 bits)
    divisionResult := Mux(divisorZero, U(0, FW + 3 bits), (dividend.asUInt / divisor.asUInt).resized)

    when(io.ce && io.ND) {
      quotient := divisionResult
    }

    io.Q_MSB := quotient(FW + 2)
    io.Q_MANT := quotient
  }
}

// 伴生对象，用于简化实例化
object FltDivMant {
  def apply(
    clk: Bool,
    ce: Bool,
    ND: Bool,
    N_MANT: UInt,
    D_MANT: UInt,
    config: FltDivMantConfig
  ): (Bool, UInt) = {
    val module = new FltDivMant(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.ND := ND
    module.io.N_MANT := N_MANT
    module.io.D_MANT := D_MANT
    (module.io.Q_MSB, module.io.Q_MANT)
  }
}
