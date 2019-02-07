import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._

object Tool {
  lazy val ammoniteVersion = settingKey[String](
    "Version of Ammonite used in projects"
  )

  lazy val toolName = settingKey[String]("Name of the tool when building")

  /** Tool-specific project settings. */
  val settings = Seq(
    // NOTE: Fork needed to avoid mixing in sbt classloader, which is causing
    //       LinkageError to be thrown for JDI-based classes
    fork in Test := true,
    fork in IntegrationTest := true,

    // Contains the version of Ammonite used
    ammoniteVersion := "1.6.3-0-c77705a",

    libraryDependencies ++= Seq(
      "com.lihaoyi" % "ammonite" % ammoniteVersion.value cross CrossVersion.full,
      "com.lihaoyi" %% "ammonite-util" % ammoniteVersion.value,
      "com.lihaoyi" %% "ammonite-terminal" % ammoniteVersion.value,
      "org.rogach" %% "scallop" % "2.0.5",
      "org.slf4j" % "slf4j-api" % "1.7.5",
      "org.slf4j" % "slf4j-log4j12" % "1.7.5",
      "log4j" % "log4j" % "1.2.17" % "test,it",
      "org.scalatest" %% "scalatest" % "3.0.0" % "test,it",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.4.2" % "test,it"
    ),

    // Give our tool a shorter name of "sdb"
    toolName := "sdb",

    // Assembly name following our tool name
    assemblyJarName in assembly := {
      // Either -2.10, -2.11, -2.12, or empty string
      val postfix = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((major, minor)) => "-" + major + "." + minor
        case None => ""
      }
      toolName.value + "-" + version.value + postfix + ".jar"
    },

    // Exclude tools.jar (JDI) since not allowed to ship without JDK
    assemblyExcludedJars in assembly := {
      val cp = (fullClasspath in assembly).value
      cp filter {_.data.getName == "tools.jar"}
    }
  )
}
