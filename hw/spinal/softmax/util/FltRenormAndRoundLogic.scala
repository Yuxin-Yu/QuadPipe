// SPDX-License-Identifier: MIT

package softmax.util

import spinal.core._
import spinal.lib._

case class FltRenormAndRoundLogicConfig(
  FW: Int = 24,
  EW: Int = 8,
  CONFIG_IMP_TYPE: Int = 0,
  CONFIG_LEGACY: Int = 1,
  HAS_ADD: Int = 0,
  FIX_SUPPORT: Int = 0,
  EXP_DELAY: Int = 1,
  NO_SHIFT_INC: Int = 0,
  NORM_BITS: Int = 1,
  EXP_ADDER: Int = 0,
  EXP_INC: Int = 0,
  SPEED: Int = 2
)

class FltRenormAndRoundLogic(config: FltRenormAndRoundLogicConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val MANT_IN = in UInt(FW + 2 bits)
    val FIX_MANT_SIGN = in Bool()
    val SIGN = in Bool()
    val ZERO_LSBS = in Bool()
    val EXTRA_LSB = in Bool()
    val EXTRA_LSBS = in Bits(2 bits)
    val NORMALIZE = in Bool()
    val NORMALIZE2 = in Bool()
    val EXP_INC_IN = in Bool()
    val EXP_IN = in UInt(EW bits)
    val EXP_OFF = in UInt(EW bits)
    val FIXED_POINT = in Bool()

    val MANT_OUT = out UInt(FW - 1 bits)
    val EXP_OUT = out UInt(EW bits)
    val EXP_INC_OUT = out Bool()
  }

  // One-bit normalize and round-to-nearest-even compatible approximation.
  val mantPre = UInt((FW + 1) bits)
  when(io.NORMALIZE) {
    mantPre := io.MANT_IN(FW + 1 downto 1)
  } otherwise {
    mantPre := io.MANT_IN(FW downto 0)
  }

  val roundSticky = io.MANT_IN(0) || io.EXTRA_LSB || io.EXTRA_LSBS.orR || !io.ZERO_LSBS
  val mantRounded = (mantPre + roundSticky.asUInt.resize(FW + 1)).resized
  val mantCarry = mantRounded(FW)

  val mantOutComb = UInt(FW - 1 bits)
  when(mantCarry) {
    mantOutComb := mantRounded(FW downto 2)
  } otherwise {
    mantOutComb := mantRounded(FW - 1 downto 1)
  }

  val expComb = (io.EXP_IN + io.EXP_OFF + io.EXP_INC_IN.asUInt.resize(EW) + mantCarry.asUInt.resize(EW)).resized

  private val rrClockDomain = ClockDomain(
    clock = io.clk,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  private val logic = new ClockingArea(rrClockDomain) {
    val mantOutReg = Reg(UInt((FW - 1) bits)) init (0)
    val expOutReg = Reg(UInt(EW bits)) init (0)
    val expIncOutReg = Reg(Bool()) init (False)

    when(io.ce) {
      mantOutReg := mantOutComb
      expOutReg := expComb
      expIncOutReg := mantCarry
    }

    io.MANT_OUT := mantOutReg
    io.EXP_OUT := expOutReg
    io.EXP_INC_OUT := expIncOutReg
  }
}

object FltRenormAndRoundLogic {
  def apply(
    clk: Bool,
    ce: Bool,
    MANT_IN: UInt,
    FIX_MANT_SIGN: Bool,
    SIGN: Bool,
    ZERO_LSBS: Bool,
    EXTRA_LSB: Bool,
    EXTRA_LSBS: Bits,
    NORMALIZE: Bool,
    NORMALIZE2: Bool,
    EXP_INC_IN: Bool,
    EXP_IN: UInt,
    EXP_OFF: UInt,
    FIXED_POINT: Bool,
    config: FltRenormAndRoundLogicConfig
  ): (UInt, UInt, Bool) = {
    val module = new FltRenormAndRoundLogic(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.MANT_IN := MANT_IN
    module.io.FIX_MANT_SIGN := FIX_MANT_SIGN
    module.io.SIGN := SIGN
    module.io.ZERO_LSBS := ZERO_LSBS
    module.io.EXTRA_LSB := EXTRA_LSB
    module.io.EXTRA_LSBS := EXTRA_LSBS
    module.io.NORMALIZE := NORMALIZE
    module.io.NORMALIZE2 := NORMALIZE2
    module.io.EXP_INC_IN := EXP_INC_IN
    module.io.EXP_IN := EXP_IN
    module.io.EXP_OFF := EXP_OFF
    module.io.FIXED_POINT := FIXED_POINT
    (module.io.MANT_OUT, module.io.EXP_OUT, module.io.EXP_INC_OUT)
  }
}
