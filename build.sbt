ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "org.example"

val spinalVersion = "1.12.0"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalSim = "com.github.spinalhdl" %% "spinalhdl-sim" % spinalVersion % Test
val spinalIdslPlugin = compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion)
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19" % Test

lazy val projectname = (project in file("."))
  .settings(
    name := "spinalhdl-softmax",
    Compile / scalaSource := baseDirectory.value / "hw" / "spinal",
    Compile / unmanagedSources := {
      val base = (Compile / scalaSource).value
      val softmaxBase = base / "softmax"
      ((softmaxBase) ** "*.scala").get.distinct
    },
    Test / scalaSource := baseDirectory.value / "hw" / "spinal_test",
    Test / unmanagedSources := ((Test / scalaSource).value ** "*.scala").get,
    libraryDependencies ++= Seq(spinalCore, spinalLib, spinalIdslPlugin, spinalSim, scalaTest)
  )

fork := true
