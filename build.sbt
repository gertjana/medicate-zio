val scala3Version = "3.3.6"

val zioHttpVersion = "3.3.3"
val zioVersion = "2.1.20"
val zioSchemsProtobufVersion = "1.7.3"
val zioRedisVersion = "1.1.5"
val zioLoggingVersion = "2.1.15"
val zioLoggingSlf4jVersion = "2.1.15"

val zioTestHttpTestKitVersion = "3.3.3"
val zioTestVersion = "2.1.20"

lazy val root = project
  .in(file("."))
  .settings(
    name := "medicate",
    version := "0.1.0",
    organization := "dev.gertjanassies",
    scalaVersion := scala3Version,
    semanticdbEnabled := true,
    scalacOptions += { "-Wunused:imports" },
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-redis" % zioRedisVersion,
      "dev.zio" %% "zio-schema-protobuf" % zioSchemsProtobufVersion,
      "dev.zio" %% "zio-test" % zioTestVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioTestVersion % Test,
      "dev.zio" %% "zio-http-testkit" % zioTestHttpTestKitVersion % Test,
      "dev.zio" %% "zio-redis-embedded" % "1.1.5" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}
