name := "ergoscript-compiler-lsp"
version := "0.1.0"
scalaVersion := "2.13.16"

// Compile for Java 17 compatibility (required for LSP4J)
javacOptions ++= Seq("-source", "17", "-target", "17")
scalacOptions ++= Seq("-release", "17")

// Dependency eviction strategy - allow newer versions of circe
ThisBuild / libraryDependencySchemes ++= Seq(
  "io.circe" %% "circe-core" % VersionScheme.Always,
  "io.circe" %% "circe-generic" % VersionScheme.Always,
  "io.circe" %% "circe-parser" % VersionScheme.Always
)

// Main dependency - sigmastate-interpreter (sc module contains the compiler)
libraryDependencies ++= Seq(
  // Sigma State - ErgoScript compiler and interpreter (includes SigmaCompiler)
  "org.scorexfoundation" %% "sigma-state" % "6.0.2",

  // NOTE: We implement LSP protocol directly using Circe instead of lsp4j
  // to avoid Scala/Java annotation compatibility issues

  // CLI argument parsing
  "com.github.scopt" %% "scopt" % "4.1.0",

  // JSON handling (circe - match sigma-state version to avoid conflicts)
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

// Assembly plugin for creating fat JAR
assembly / mainClass := Some("org.ergoplatform.ergoscript.Main")
assembly / assemblyJarName := "ergoscript-compiler-lsp.jar"

// Merge strategy for assembly
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) =>
    MergeStrategy.concat // Keep SLF4J service providers
  case PathList("META-INF", "maven", xs @ _*)    => MergeStrategy.discard
  case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*)
      if xs.lastOption.exists(_.endsWith(".SF")) =>
    MergeStrategy.discard // Signature files
  case PathList("META-INF", xs @ _*)
      if xs.lastOption.exists(_.endsWith(".DSA")) =>
    MergeStrategy.discard
  case PathList("META-INF", xs @ _*)
      if xs.lastOption.exists(_.endsWith(".RSA")) =>
    MergeStrategy.discard
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) =>
    MergeStrategy.first // Other META-INF files
  case "reference.conf"   => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case x                  => MergeStrategy.first
}
