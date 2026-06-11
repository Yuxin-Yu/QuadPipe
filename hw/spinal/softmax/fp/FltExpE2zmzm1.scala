




package softmax.fp

import spinal.core._

case class FltExpE2zmzm1Config(
  C_WF: Int = 23,
  C_Z_WIDTH: Int = 6,
  C_RESULT_WIDTH: Int = 6
) {
  val FULL_TABLE_WIDTH = 6
  val TABLE_WIDTH = 6
  val TABLE_LAT = 2
  val ROM_ADDR_WIDTH = C_Z_WIDTH
  val ROM_DEPTH = 1 << ROM_ADDR_WIDTH

  require(C_Z_WIDTH == 6, s"FltExpE2zmzm1 expects 6-bit Z address, got $C_Z_WIDTH")
  require(C_RESULT_WIDTH <= TABLE_WIDTH, s"FltExpE2zmzm1 result width $C_RESULT_WIDTH exceeds table width $TABLE_WIDTH")


  val tableData: IndexedSeq[Int] = IndexedSeq(
    0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
    0x01, 0x01, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02,
    0x03, 0x03, 0x03, 0x03, 0x04, 0x04, 0x04, 0x05,
    0x05, 0x05, 0x06, 0x06, 0x07, 0x07, 0x08, 0x08,
    0x09, 0x09, 0x0a, 0x0a, 0x0b, 0x0b, 0x0c, 0x0c,
    0x0d, 0x0e, 0x0e, 0x0f, 0x10, 0x10, 0x11, 0x12,
    0x13, 0x13, 0x14, 0x15, 0x16, 0x16, 0x17, 0x18,
    0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20
  )
}

class FltExpE2zmzm1(config: FltExpE2zmzm1Config) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val Z = in UInt(C_Z_WIDTH bits)
    val result = out UInt(C_RESULT_WIDTH bits)
  }

  private val expClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(expClockDomain) {
    val romVec = Vec(tableData.map(value => U(value, TABLE_WIDTH bits)))

    val mem = RegNext(romVec(io.Z)) init(0)

    io.result := mem(FULL_TABLE_WIDTH - 1 downto FULL_TABLE_WIDTH - C_RESULT_WIDTH)
  }
}

object FltExpE2zmzm1 {
  def apply(
    clk: Bool,
    ce: Bool,
    Z: UInt,
    config: FltExpE2zmzm1Config
  ): UInt = {
    val module = new FltExpE2zmzm1(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.Z := Z
    module.io.result
  }
}
