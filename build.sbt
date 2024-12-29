val scala3Version = "3.3.1"
val zioVersion = "2.0.19"
val zioHttpVersion = "3.0.1"
val zioRedisVersion = "1.0.0"
val zioSchemsProtobufVersion = "1.5.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "medicate",
    version := "0.1.0",
    organization := "dev.gertjanassies",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-redis" % zioRedisVersion,
      "dev.zio" %% "zio-schema-protobuf" % zioSchemsProtobufVersion
    )
  ) 