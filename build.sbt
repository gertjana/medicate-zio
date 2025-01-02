val scala3Version = "3.3.1"

val zioVersion               = "2.0.19"
val zioHttpVersion           = "3.0.1"
val zioRedisVersion          = "1.0.0"
val zioSchemsProtobufVersion = "1.5.0"

val zioTestVersion            = "2.1.14"
val zioTestHttpTestKitVersion = "3.0.1"

lazy val root = project
  .in(file("."))
  .settings(
    name              := "medicate",
    version           := "0.1.0",
    organization      := "dev.gertjanassies",
    scalaVersion      := scala3Version,
    semanticdbEnabled := true,
    scalacOptions += { "-Wunused:imports" },
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"                 % zioVersion,
      "dev.zio" %% "zio-http"            % zioHttpVersion,
      "dev.zio" %% "zio-redis"           % zioRedisVersion,
      "dev.zio" %% "zio-schema-protobuf" % zioSchemsProtobufVersion,
      "dev.zio" %% "zio-test"            % zioTestVersion            % Test,
      "dev.zio" %% "zio-test-sbt"        % zioTestVersion            % Test,
      "dev.zio" %% "zio-http-testkit"    % zioTestHttpTestKitVersion % Test,
      "dev.zio" %% "zio-redis-embedded"  % "1.0.0"                   % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
