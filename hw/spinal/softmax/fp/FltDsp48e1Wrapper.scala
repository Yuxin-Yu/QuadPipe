












package softmax.fp

import spinal.core._

case class FltDsp48e1WrapperConfig(
  A_WIDTH: Int = 2,
  B_WIDTH: Int = 16,
  C_WIDTH: Int = 16,
  D_WIDTH: Int = 27,
  P_WIDTH: Int = 16,
  A_SIGNED: Int = 0,
  B_SIGNED: Int = 0,
  C_SIGNED: Int = 0,
  D_SIGNED: Int = 0,
  CASCADE_A: Int = 0,
  CASCADE_B: Int = 0,
  A_REG: Int = 0,
  AD_REG: Int = 0,
  B_REG: Int = 0,
  C_REG: Int = 0,
  D_REG: Int = 0,
  M_REG: Int = 0,
  P_REG: Int = 0,
  OP_REG: Int = 0,
  INMODE_REG: Int = 0,
  A_CASCADE_REG: Int = -1,
  B_CASCADE_REG: Int = -1,
  USE_DPORT: Int = 0,
  USE_MULTIPLY: Int = 0,
  USE_PATTERN_DETECT: Int = 0,
  MASK: BigInt = BigInt("3fffffffffff", 16),
  MASK_FROM_C: Int = 0,
  USE_SIMD: String = "ONE48"
) {
  val USE_MULT = if (USE_MULTIPLY == 1) "MULTIPLY" else "NONE"

  val D_PORT_WIDTH = 25
}

class FltDsp48e1Wrapper(config: FltDsp48e1WrapperConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val A_IN = in UInt(A_WIDTH bits)
    val B_IN = in UInt(B_WIDTH bits)
    val C_IN = in UInt(C_WIDTH bits)
    val D_IN = in UInt(D_WIDTH bits)
    val CARRY_IN = in Bool()
    val OP_MODE = in Bits(9 bits)
    val ALU_MODE = in Bits(4 bits)
    val IN_MODE = in Bits(5 bits)

    val CARRY_OUT = out Bits(4 bits)
    val P_OUT = out UInt(P_WIDTH bits)
  }

  private val cd = ClockDomain(clock = io.clk, config = ClockDomainConfig(resetKind = BOOT))

  private val logic = new ClockingArea(cd) {
    def dlyN[T <: Data](init0: T, src: T, n: Int): T = {
      var cur = src
      for (_ <- 0 until n) {
        val r = RegNextWhen(cur, io.ce) init (init0)
        cur = r
      }
      cur
    }


    val aReg = dlyN(U(0, A_WIDTH bits), io.A_IN, A_REG)
    val dReg = dlyN(U(0, D_WIDTH bits), io.D_IN, D_REG)
    val bReg = dlyN(U(0, B_WIDTH bits), io.B_IN, B_REG)
    val cReg = dlyN(U(0, C_WIDTH bits), io.C_IN, C_REG)


    val dPort: UInt = if (D_WIDTH > D_PORT_WIDTH) dReg(D_PORT_WIDTH - 1 downto 0) else dReg

    def ext(v: UInt, w: Int, signed: Int): SInt =
      if (signed == 1) v.asSInt.resize(w) else v.resize(w).asSInt

    val aS = ext(aReg, 30, A_SIGNED)
    val dS = ext(dPort, 25, D_SIGNED)
    val bS = ext(bReg, 18, B_SIGNED)
    val cS = ext(cReg, 48, C_SIGNED)


    val preadd = (aS + dS)
    val preaddReg = dlyN(S(0, preadd.getWidth bits), preadd, AD_REG)


    val mult = preaddReg * bS
    val multReg = dlyN(S(0, mult.getWidth bits), mult, M_REG)


    val carryDel = dlyN(False, io.CARRY_IN, if (C_REG < 1) C_REG else 1)
    val sum = (multReg + cS + carryDel.asUInt(1 bits).asSInt.resize(48)).resize(48)
    val pReg = dlyN(S(0, 48 bits), sum.resize(48), P_REG)

    io.P_OUT := pReg.asUInt(P_WIDTH - 1 downto 0)
    io.CARRY_OUT := B"0000"
  }
}

object FltDsp48e1Wrapper {
  def apply(
    clk: Bool,
    ce: Bool,
    A_IN: UInt,
    B_IN: UInt,
    C_IN: UInt,
    D_IN: UInt,
    CARRY_IN: Bool,
    OP_MODE: Bits,
    ALU_MODE: Bits,
    IN_MODE: Bits,
    config: FltDsp48e1WrapperConfig
  ): (Bits, UInt) = {
    val module = new FltDsp48e1Wrapper(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.A_IN := A_IN
    module.io.B_IN := B_IN
    module.io.C_IN := C_IN
    module.io.D_IN := D_IN
    module.io.CARRY_IN := CARRY_IN
    module.io.OP_MODE := OP_MODE
    module.io.ALU_MODE := ALU_MODE
    module.io.IN_MODE := IN_MODE
    (module.io.CARRY_OUT, module.io.P_OUT)
  }
}
