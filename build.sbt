val scala3Version = "3.9.0"

val zioHttpVersion = "3.11.6"
val zioVersion = "2.1.26"
val zioSchemsProtobufVersion = "1.8.7"
val zioRedisVersion = "1.3.0"
val zioLoggingVersion = "2.1.15"
val zioLoggingSlf4jVersion = "2.1.15"

val zioTestHttpTestKitVersion = "3.11.6"
val zioTestVersion = "2.1.26"

lazy val root = project
  .in(file("."))
  .settings(
    name := "medicate",
    version := "0.1.0",
    organization := "dev.gertjanassies",
    scalaVersion := scala3Version,
    semanticdbEnabled := true,
    scalacOptions += { "-Wunused:imports" },
    // Scala 3 built-in coverage: always instrument main sources so the CAS-backed
    // incremental compiler doesn't skip recompilation. sbt 2.x stores outputs in a
    // content-addressable store; without a constant flag, a changed env var won't
    // trigger recompilation. The instrumentation overhead is negligible in production.
    Compile / scalacOptions += {
      val dir = baseDirectory.value / "target" / "coverage" / "scoverage-data"
      dir.mkdirs()
      s"-coverage-out:${dir.getAbsolutePath}"
    },
    // Prevent Test sources from also being instrumented (test paths confuse the reporter).
    Test / scalacOptions ~= (_.filterNot(_.startsWith("-coverage-out"))),
    // Point sbt-scoverage's report task at the native coverage output
    coverageDataDir := baseDirectory.value / "target" / "coverage",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-redis" % zioRedisVersion,
      "dev.zio" %% "zio-schema-protobuf" % zioSchemsProtobufVersion,
      "dev.zio" %% "zio-test" % zioTestVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioTestVersion % Test,
      "dev.zio" %% "zio-http-testkit" % zioTestHttpTestKitVersion % Test,
      "dev.zio" %% "zio-redis-embedded" % "1.3.0" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}
