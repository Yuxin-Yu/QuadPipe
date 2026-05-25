package softmax

import spinal.core._

object QuadPipeCocotbGen {
  def main(args: Array[String]): Unit = {
    val targetDir = if (args.nonEmpty) args(0) else "./hw/cocotb/softmax_refactor/build"
    val netlistName = if (args.length > 1) args(1) else "QuadPipe.v"

    SpinalConfig(
      targetDirectory = targetDir,
      oneFilePerComponent = false,
      netlistFileName = netlistName
    ).generateVerilog(new QuadPipe)
  }
}
