// SPDX-License-Identifier: MIT

package softmax.fp

import spinal.core._
import softmax.util.{FltDelay, FltLeadZeroEncode, FltRenormAndRoundLogic, FltRenormAndRoundLogicConfig, FltShiftMsbFirst}

case class FltNormAndRoundLogicConfig(
  C_MULT_USAGE: Int = 0,
  AB_FW: Int = 24,
  AB_EW: Int = 8,
  EXP_ADDER: Int = 1,
  SPEED: Int = 0,
  REGISTERS: String = "0000_0000_0010_1010"
)

class FltNormAndRoundLogic(config: FltNormAndRoundLogicConfig) extends Component {
  import config._

  val io = new Bundle {
    val clk = in Bool()
    val ce = in Bool()
    val MANT_IN = in UInt(AB_FW + 3 bits)
    val ZEROS = in Bool()

    val NORM_DIST = out UInt(AB_EW bits)
    val MANT_OUT = out UInt(AB_FW - 1 bits)
    val CANCELLATION = out Bool()
    val EXP_OUT = out UInt(AB_EW bits)
    val ROUND_EXP_INC = out Bool()
  }

  val mantDel = FltDelay(io.clk, io.ce, io.MANT_IN.asBits, AB_FW + 3, 0)

  val leadZero = new FltLeadZeroEncode(
    useRtl = false,
    dataWidth = AB_FW + 3,
    distWidth = AB_EW,
    useDistIn = false,
    noLastTwoStages = false,
    hasAdd = true
  )
  leadZero.io.clk := io.clk
  leadZero.io.ce := io.ce
  leadZero.io.DATA := io.MANT_IN.asBits
  leadZero.io.DATA_DEL := mantDel
  leadZero.io.DIST_IN := B(0, AB_EW bits)

  val shift = new FltShiftMsbFirst(
    aWidth = AB_FW + 3,
    resultWidth = AB_FW + 5,
    distanceWidth = AB_EW,
    shiftLeft = 1,
    lastStagesToOmit = 1,
    skewedDist = 1
  )
  shift.io.clk := io.clk
  shift.io.ce := io.ce
  shift.io.A := mantDel
  shift.io.distance := leadZero.io.DIST_SKEW.resized

  val zerosDel = FltDelay(io.clk, io.ce, io.ZEROS.asBits, 1, 1).asBool
  val normalizeRnd0 = leadZero.io.DIST_SKEW(0)

  val renorm = new FltRenormAndRoundLogic(
    FltRenormAndRoundLogicConfig(
      FW = AB_FW,
      EW = AB_EW,
      CONFIG_IMP_TYPE = 0,
      CONFIG_LEGACY = 0,
      HAS_ADD = 1,
      EXP_DELAY = 0,
      NO_SHIFT_INC = 1,
      NORM_BITS = 1,
      EXP_ADDER = EXP_ADDER,
      EXP_INC = 0,
      SPEED = SPEED
    )
  )
  renorm.io.clk := io.clk
  renorm.io.ce := io.ce
  renorm.io.MANT_IN := shift.io.result(AB_FW + 4 downto 3).asUInt
  renorm.io.FIX_MANT_SIGN := False
  renorm.io.SIGN := False
  renorm.io.ZERO_LSBS := zerosDel
  renorm.io.EXTRA_LSB := shift.io.result(2)
  renorm.io.EXTRA_LSBS := shift.io.result(1 downto 0)
  renorm.io.NORMALIZE := normalizeRnd0
  renorm.io.NORMALIZE2 := False
  renorm.io.EXP_INC_IN := False
  renorm.io.EXP_IN := U(0, AB_EW bits)
  renorm.io.EXP_OFF := U(0, AB_EW bits)
  renorm.io.FIXED_POINT := False

  io.NORM_DIST := leadZero.io.DIST.asUInt
  io.MANT_OUT := renorm.io.MANT_OUT
  io.CANCELLATION := leadZero.io.ALL_BITS_ZERO
  io.EXP_OUT := renorm.io.EXP_OUT
  io.ROUND_EXP_INC := renorm.io.EXP_INC_OUT
}

object FltNormAndRoundLogic {
  def apply(
    clk: Bool,
    ce: Bool,
    MANT_IN: UInt,
    ZEROS: Bool,
    config: FltNormAndRoundLogicConfig
  ): (UInt, UInt, Bool, UInt, Bool) = {
    val module = new FltNormAndRoundLogic(config)
    module.io.clk := clk
    module.io.ce := ce
    module.io.MANT_IN := MANT_IN
    module.io.ZEROS := ZEROS
    (module.io.NORM_DIST, module.io.MANT_OUT, module.io.CANCELLATION, module.io.EXP_OUT, module.io.ROUND_EXP_INC)
  }
}
