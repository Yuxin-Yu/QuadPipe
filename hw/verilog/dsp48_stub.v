module DSP48E2 #(
    parameter AMULTSEL = "A",
    parameter A_INPUT = "DIRECT",
    parameter BMULTSEL = "B",
    parameter B_INPUT = "DIRECT",
    parameter PREADDINSEL = "A",
    parameter [47:0] RND = 48'h0,
    parameter USE_MULT = "MULTIPLY",
    parameter USE_SIMD = "ONE48",
    parameter USE_WIDEXOR = "FALSE",
    parameter XORSIMD = "XOR24_48_96",
    parameter AUTORESET_PATDET = "NO_RESET",
    parameter AUTORESET_PRIORITY = "RESET",
    parameter [47:0] MASK = 48'h0,
    parameter [47:0] PATTERN = 48'h0,
    parameter SEL_MASK = "MASK",
    parameter SEL_PATTERN = "PATTERN",
    parameter USE_PATTERN_DETECT = "NO_PATDET",
    parameter [3:0] IS_ALUMODE_INVERTED = 4'b0,
    parameter IS_CARRYIN_INVERTED = 1'b0,
    parameter IS_CLK_INVERTED = 1'b0,
    parameter [4:0] IS_INMODE_INVERTED = 5'b0,
    parameter [8:0] IS_OPMODE_INVERTED = 9'b0,
    parameter IS_RSTALLCARRYIN_INVERTED = 1'b0,
    parameter IS_RSTALUMODE_INVERTED = 1'b0,
    parameter IS_RSTA_INVERTED = 1'b0,
    parameter IS_RSTB_INVERTED = 1'b0,
    parameter IS_RSTCTRL_INVERTED = 1'b0,
    parameter IS_RSTC_INVERTED = 1'b0,
    parameter IS_RSTD_INVERTED = 1'b0,
    parameter IS_RSTINMODE_INVERTED = 1'b0,
    parameter IS_RSTM_INVERTED = 1'b0,
    parameter IS_RSTP_INVERTED = 1'b0,
    parameter ACASCREG = 1,
    parameter ADREG = 1,
    parameter ALUMODEREG = 1,
    parameter AREG = 1,
    parameter BCASCREG = 1,
    parameter BREG = 1,
    parameter CARRYINREG = 1,
    parameter CARRYINSELREG = 1,
    parameter CREG = 1,
    parameter DREG = 1,
    parameter INMODEREG = 1,
    parameter MREG = 1,
    parameter OPMODEREG = 1,
    parameter PREG = 1
) (
    output [29:0] ACOUT,
    output [17:0] BCOUT,
    output CARRYCASCOUT,
    output MULTSIGNOUT,
    output [47:0] PCOUT,
    output OVERFLOW,
    output PATTERNBDETECT,
    output PATTERNDETECT,
    output UNDERFLOW,
    output [3:0] CARRYOUT,
    output [47:0] P,
    output [7:0] XOROUT,
    input [29:0] ACIN,
    input [17:0] BCIN,
    input CARRYCASCIN,
    input MULTSIGNIN,
    input [47:0] PCIN,
    input [3:0] ALUMODE,
    input [2:0] CARRYINSEL,
    input CLK,
    input [4:0] INMODE,
    input [8:0] OPMODE,
    input [29:0] A,
    input [17:0] B,
    input [47:0] C,
    input CARRYIN,
    input [26:0] D,
    input CEA1,
    input CEA2,
    input CEAD,
    input CEALUMODE,
    input CEB1,
    input CEB2,
    input CEC,
    input CECARRYIN,
    input CECTRL,
    input CED,
    input CEINMODE,
    input CEM,
    input CEP,
    input RSTA,
    input RSTALLCARRYIN,
    input RSTALUMODE,
    input RSTB,
    input RSTC,
    input RSTCTRL,
    input RSTD,
    input RSTINMODE,
    input RSTM,
    input RSTP
);
  reg [47:0] pReg;
  wire signed [47:0] mulTerm = $signed({{18{A[29]}}, A}) * $signed({{30{B[17]}}, B});
  wire signed [47:0] dTerm = {{21{D[26]}}, D};
  wire signed [47:0] multP = mulTerm + $signed(C) + dTerm + $signed(PCIN) + CARRYIN;

  // OPMODE/ALUMODE decode for accumulator support
  wire isLoad  = (OPMODE[6:0] == 7'b0110000);
  wire isAccum = (OPMODE[6:0] == 7'b0110010);
  wire isHold  = (OPMODE[6:0] == 7'b0000010);
  wire isSub   = (ALUMODE == 4'b0001);
  wire signed [47:0] accumP = isSub ? (pReg - $signed(C) - CARRYIN) : (pReg + $signed(C) + CARRYIN);

  wire signed [47:0] nextP =
    isLoad  ? ($signed(C) + CARRYIN) :
    isAccum ? accumP :
    isHold  ? pReg :
    /* multiply-accumulate by default */ multP;

  always @(posedge CLK) begin
    if (RSTA || RSTALLCARRYIN || RSTALUMODE || RSTB || RSTC || RSTCTRL || RSTD || RSTINMODE || RSTM || RSTP)
      pReg <= 48'd0;
    else if (CEP)
      pReg <= nextP;
  end

  assign ACOUT = A;
  assign BCOUT = B;
  assign CARRYCASCOUT = 1'b0;
  assign MULTSIGNOUT = mulTerm[47];
  assign PCOUT = pReg;
  assign OVERFLOW = 1'b0;
  assign PATTERNBDETECT = 1'b0;
  assign PATTERNDETECT = 1'b0;
  assign UNDERFLOW = 1'b0;
  assign CARRYOUT = 4'b0;
  assign P = pReg;
  assign XOROUT = 8'b0;
endmodule

module DSP48E1 #(
    parameter A_INPUT = "DIRECT",
    parameter B_INPUT = "DIRECT",
    parameter USE_DPORT = "FALSE",
    parameter USE_MULT = "MULTIPLY",
    parameter USE_SIMD = "ONE48",
    parameter AUTORESET_PATDET = "NO_RESET",
    parameter [47:0] MASK = 48'h0,
    parameter [47:0] PATTERN = 48'h0,
    parameter SEL_MASK = "MASK",
    parameter SEL_PATTERN = "PATTERN",
    parameter USE_PATTERN_DETECT = "NO_PATDET",
    parameter ACASCREG = 1,
    parameter ADREG = 1,
    parameter ALUMODEREG = 1,
    parameter AREG = 1,
    parameter BCASCREG = 1,
    parameter BREG = 1,
    parameter CARRYINREG = 1,
    parameter CARRYINSELREG = 1,
    parameter CREG = 1,
    parameter DREG = 1,
    parameter INMODEREG = 1,
    parameter MREG = 1,
    parameter OPMODEREG = 1,
    parameter PREG = 1
) (
    output [29:0] ACOUT,
    output [17:0] BCOUT,
    output CARRYCASCOUT,
    output MULTSIGNOUT,
    output [47:0] PCOUT,
    output OVERFLOW,
    output PATTERNBDETECT,
    output PATTERNDETECT,
    output UNDERFLOW,
    output [3:0] CARRYOUT,
    output [47:0] P,
    input [29:0] ACIN,
    input [17:0] BCIN,
    input CARRYCASCIN,
    input MULTSIGNIN,
    input [47:0] PCIN,
    input [3:0] ALUMODE,
    input [2:0] CARRYINSEL,
    input CLK,
    input [4:0] INMODE,
    input [6:0] OPMODE,
    input [29:0] A,
    input [17:0] B,
    input [47:0] C,
    input CARRYIN,
    input [24:0] D,
    input CEA1,
    input CEA2,
    input CEAD,
    input CEALUMODE,
    input CEB1,
    input CEB2,
    input CEC,
    input CECARRYIN,
    input CECTRL,
    input CED,
    input CEINMODE,
    input CEM,
    input CEP,
    input RSTA,
    input RSTALLCARRYIN,
    input RSTALUMODE,
    input RSTB,
    input RSTC,
    input RSTCTRL,
    input RSTD,
    input RSTINMODE,
    input RSTM,
    input RSTP
);
  reg [47:0] pReg;
  wire signed [47:0] mulTerm = $signed({{18{A[29]}}, A}) * $signed({{30{B[17]}}, B});
  wire signed [47:0] dTerm = {{23{D[24]}}, D};
  wire signed [47:0] nextP = mulTerm + $signed(C) + dTerm + $signed(PCIN) + CARRYIN;

  always @(posedge CLK) begin
    if (RSTA || RSTALLCARRYIN || RSTALUMODE || RSTB || RSTC || RSTCTRL || RSTD || RSTINMODE || RSTM || RSTP)
      pReg <= 48'd0;
    else if (CEP)
      pReg <= nextP;
  end

  assign ACOUT = A;
  assign BCOUT = B;
  assign CARRYCASCOUT = 1'b0;
  assign MULTSIGNOUT = mulTerm[47];
  assign PCOUT = pReg;
  assign OVERFLOW = 1'b0;
  assign PATTERNBDETECT = 1'b0;
  assign PATTERNDETECT = 1'b0;
  assign UNDERFLOW = 1'b0;
  assign CARRYOUT = 4'b0;
  assign P = pReg;
endmodule
