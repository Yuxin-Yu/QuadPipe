









package softmax.fp

import spinal.core._

object FltExpCcmTables {

  val recipLn2_0: IndexedSeq[Long] = IndexedSeq(
    0x04, 0x05, 0x07, 0x08, 0x0a, 0x0b, 0x0d, 0x0e,
    0x10, 0x11, 0x12, 0x14, 0x15, 0x17, 0x18, 0x1a,
    0x1b, 0x1d, 0x1e, 0x1f, 0x21, 0x22, 0x24, 0x25,
    0x27, 0x28, 0x2a, 0x2b, 0x2c, 0x2e, 0x2f, 0x31
  ).map(_.toLong)


  val recipLn2_1: IndexedSeq[Long] = IndexedSeq(
    0x000, 0x02e, 0x05c, 0x08b, 0x0b9, 0x0e7, 0x115, 0x143,
    0x171, 0x1a0, 0x1ce, 0x1fc, 0x22a, 0x258, 0x286, 0x2b5,
    0x2e3, 0x311, 0x33f, 0x36d, 0x39b, 0x3ca, 0x3f8, 0x426,
    0x454, 0x482, 0x4b0, 0x4df, 0x50d, 0x53b, 0x569, 0x597
  ).map(_.toLong)


  val ln2_0: IndexedSeq[Long] = IndexedSeq(
    0x00000002L, 0x0b172182L, 0x162e4301L, 0x21456480L,
    0x2c5c8600L, 0x3773a780L, 0x428ac8ffL, 0x4da1ea7eL,
    0x58b90bfeL, 0x63d02d7eL, 0x6ee74efdL, 0x79fe707cL,
    0x851591fcL, 0x902cb37cL, 0x9b43d4fbL, 0xa65af67aL
  )


  val ln2_1: IndexedSeq[Long] = IndexedSeq(
    0x000000000L, 0x0b17217f8L, 0x162e42ff0L, 0x2145647e8L,
    0x2c5c85fe0L, 0x3773a77d8L, 0x428ac8fd0L, 0x4da1ea7c8L,
    0x58b90bfc0L, 0x63d02d7b8L, 0x6ee74efb0L, 0x79fe707a8L,
    0x851591fa0L, 0x902cb3798L, 0x9b43d4f90L, 0xa65af6788L
  )
}

case class FltExpCcmConfig(
  C_WF: Int = 23,
  C_X_WIDTH: Int = 10,
  C_RESULT_WIDTH: Int = 8,
  C_TABLE_USAGE: Int = 0
) {
  require(C_TABLE_USAGE == 0 || C_TABLE_USAGE == 1, s"unsupported C_TABLE_USAGE=$C_TABLE_USAGE")

  val FULL_TABLE_WIDTH = if (C_TABLE_USAGE == 0) 11 else 36
  val ADDR_WIDTH       = if (C_TABLE_USAGE == 0) 5 else 4
  val TABLE_WIDTH_0    = if (C_TABLE_USAGE == 0) 6 else 32
  val TABLE_WIDTH_1    = if (C_TABLE_USAGE == 0) 11 else 36
  val HAS_2S_COMP_OP   = C_TABLE_USAGE != 0
  val NEGATE_OP        = C_TABLE_USAGE != 0

  require(C_X_WIDTH >= 2 * ADDR_WIDTH,
    s"C_X_WIDTH=$C_X_WIDTH must be >= 2*ADDR_WIDTH=${2 * ADDR_WIDTH} for usage $C_TABLE_USAGE")
  require(C_RESULT_WIDTH <= FULL_TABLE_WIDTH,
    s"C_RESULT_WIDTH=$C_RESULT_WIDTH exceeds FULL_TABLE_WIDTH=$FULL_TABLE_WIDTH")

  val table0: IndexedSeq[Long] = if (C_TABLE_USAGE == 0) FltExpCcmTables.recipLn2_0 else FltExpCcmTables.ln2_0
  val table1: IndexedSeq[Long] = if (C_TABLE_USAGE == 0) FltExpCcmTables.recipLn2_1 else FltExpCcmTables.ln2_1
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
    val rom0 = Vec(table0.map(v => U(v, TABLE_WIDTH_0 bits)))
    val rom1 = Vec(table1.map(v => U(v, TABLE_WIDTH_1 bits)))


    val addr1 = io.x(ADDR_WIDTH - 1 downto 0)
    val mem1 = rom0(addr1)
    val memPad1 = mem1.resize(FULL_TABLE_WIDTH bits)
    val sign1 =
      if (!HAS_2S_COMP_OP) False
      else if (!NEGATE_OP) io.x_sign
      else !io.x_sign
    val psumNeg1 = U(0, FULL_TABLE_WIDTH bits)
    val psum0Reg = sign1 ? (psumNeg1 - memPad1) | (psumNeg1 + memPad1)
    val psum0 = RegNext(psum0Reg) init(0)


    val addrW = io.x(2 * ADDR_WIDTH - 1 downto ADDR_WIDTH)
    val addr2 = RegNext(addrW) init(0)
    val xSign2 = RegNext(io.x_sign) init(False)
    val mem2 = rom1(addr2)
    val memPad2 = mem2.resize(FULL_TABLE_WIDTH bits)
    val sign2 =
      if (!HAS_2S_COMP_OP) False
      else if (!NEGATE_OP) xSign2
      else !xSign2
    val psum1 = sign2 ? (psum0 - memPad2) | (psum0 + memPad2)

    io.result := psum1(FULL_TABLE_WIDTH - 1 downto FULL_TABLE_WIDTH - C_RESULT_WIDTH)
  }
}

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
