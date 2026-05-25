// SPDX-License-Identifier: MIT
// 指数计算的系数计算模块
// 使用查找表来获取预计算的系数值

package softmax.fp

import spinal.core._
import spinal.lib._

case class FltExpCcmConfig(
  C_WF: Int = 23,
  C_X_WIDTH: Int = 10,
  C_RESULT_WIDTH: Int = 8,
  C_TABLE_USAGE: Int = 0
) {
  // 计算函数
  def fltPtExpGetCcmOriginalDataWidth(tableUsage: Int): Int = {
    if (tableUsage == 0) {
      11
    } else {
      36
    }
  }
  
  def fltPtExpGetCcmAddressWidth(tableUsage: Int): Int = {
    if (tableUsage == 0) {
      5
    } else {
      4
    }
  }
  
  def fltPtExpGetCcmCompressedDataWidth(tableUsage: Int, tableValue: Int): Int = {
    (tableUsage, tableValue) match {
      case (0, 0) => 6
      case (0, 1) => 11
      case (1, 0) => 32
      case (1, 1) => 36
      case _ => 0
    }
  }
  
  // 内部常量定义
  val NUM_TABLES = 2
  val FULL_TABLE_WIDTH = fltPtExpGetCcmOriginalDataWidth(C_TABLE_USAGE)
  val ADDSUB_MAX_DELAY = 2
  val MAX_ADDR_WIDTH = 5
  val MAX_ROM_DEPTH = 1 << MAX_ADDR_WIDTH
  val ADDR_WIDTH = fltPtExpGetCcmAddressWidth(C_TABLE_USAGE)
  val ROM_DEPTH = 1 << ADDR_WIDTH
  val TABLE_WIDTH_0 = fltPtExpGetCcmCompressedDataWidth(C_TABLE_USAGE, 0)
  val TABLE_WIDTH_1 = fltPtExpGetCcmCompressedDataWidth(C_TABLE_USAGE, 1)
  val C_HAS_2s_COMP_OP = if (C_TABLE_USAGE == 0) false else true
  val C_NEGATE_OP = if (C_TABLE_USAGE == 0) false else true
  
  // 查找表数据（简化实现，实际需要完整的表格数据）
  val tableData = Array(
    // 表格0的数据（C_TABLE_USAGE=0）
    Array(0x20, 0x24, 0x2c, 0x30, 0x38, 0x3c, 0x44, 0x48, 0x50, 0x54, 0x58, 0x60, 0x64, 0x6c, 0x70, 0x78,
          0x7c, 0x84, 0x88, 0x8c, 0x94, 0x98, 0xa0, 0xa8, 0xac, 0xb4, 0xb8, 0xc0, 0xc4, 0xcc, 0xd0, 0xd8),
    // 表格1的数据（C_TABLE_USAGE=0）
    Array(0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
          0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0)
  )
}

class FltExpCcm(config: FltExpCcmConfig) extends Component {
  import config._
  
  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val x_sign = in Bool()
    val x = in UInt(C_X_WIDTH bits)
    val result = out UInt(C_RESULT_WIDTH bits)
  }

  private val ccmClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(ccmClockDomain) {
    val addr = io.x(ADDR_WIDTH - 1 downto 0)
    val rom0Mask = (1 << TABLE_WIDTH_0) - 1
    val rom1Mask = (1 << TABLE_WIDTH_1) - 1
    val rom0 = Mem(Bits(TABLE_WIDTH_0 bits), initialContent = tableData(0).map(value => B(value & rom0Mask, TABLE_WIDTH_0 bits)))
    val rom1 = Mem(Bits(TABLE_WIDTH_1 bits), initialContent = tableData(1).map(value => B(value & rom1Mask, TABLE_WIDTH_1 bits)))
    val table0Data = rom0.readSync(address = addr, enable = io.ce).asUInt
    val table1Data = rom1.readSync(address = addr, enable = io.ce).asUInt
    val combinedData = Cat(table1Data, table0Data)

    io.result := combinedData(C_RESULT_WIDTH - 1 downto 0).asUInt
  }
}

// 伴生对象，用于简化实例化
object FltExpCcm {
  def apply(
    clk: Bool,
    ce: Bool,
    x_sign: Bool,
    x: UInt,
    config: FltExpCcmConfig
  ): UInt = {
    val module = new FltExpCcm(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.x_sign := x_sign
    module.io.x := x
    module.io.result
  }
}
