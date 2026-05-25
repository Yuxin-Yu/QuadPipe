// SPDX-License-Identifier: MIT
// 指数计算的 e^a 查找表模块
// 该 ROM 在原 RTL 中由组合 case 函数实现，这里保持接口兼容但直接用常量 Vec 查表

package softmax.fp

import spinal.core._

case class FltExpE2aConfig(
  C_WF: Int = 23,
  C_A_WIDTH: Int = 10,
  C_RESULT_WIDTH: Int = 27
) {
  val FULL_TABLE_WIDTH = 27
  val TABLE_WIDTH = 27
  val TABLE_LAT = 2
  val ROM_ADDR_WIDTH = C_A_WIDTH
  val ROM_DEPTH = 1 << ROM_ADDR_WIDTH

  require(C_RESULT_WIDTH == TABLE_WIDTH, s"FltExpE2a expects $TABLE_WIDTH-bit results, got $C_RESULT_WIDTH")
  require(ROM_DEPTH <= FltExpE2aTable.values.length, s"FltExpE2a table only contains ${FltExpE2aTable.values.length} entries")

  val tableData: IndexedSeq[Int] = FltExpE2aTable.values.take(ROM_DEPTH)
}

class FltExpE2a(config: FltExpE2aConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A = in UInt(C_A_WIDTH bits)
    val result = out UInt(C_RESULT_WIDTH bits)
  }

  private val tableVec = Vec(tableData.map(value => U(value, TABLE_WIDTH bits)))

  io.result := tableVec(io.A)
}

object FltExpE2a {
  def apply(
    clk: Bool,
    ce: Bool,
    A: UInt,
    config: FltExpE2aConfig
  ): UInt = {
    val module = new FltExpE2a(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A := A
    module.io.result
  }
}
