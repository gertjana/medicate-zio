package dev.gertjanassies

import zio._
import zio.test._

object TestMainAPI extends ZIOSpecDefault {
  def spec = suite("The main class should ")(
    test("start a server") {
      for {
        _ <- Main.run.fork
      } yield assertCompletes
    },
    test("start a server on a different port") {
      for {
        - <- TestSystem.putEnv("PORT", "8081")
        _ <- Main.run.fork
      } yield assertCompletes
    },
    test("set the default port") {
      for {
        _ <- TestSystem.clearEnv("PORT")
        serverConfig <- Main.serverConfig
      } yield assertTrue(serverConfig.address.getPort() == 8080)
    },
    test("set the port according to the environment") {
      for {
        _ <- TestSystem.putEnv("PORT", "8082")
        serverConfig <- Main.serverConfig
      } yield assertTrue(serverConfig.address.getPort() == 8082)
    },
    test("set the redis host and port according to the environment") {
      for {
        _ <- TestSystem.putEnv("REDIS_HOST", "redis.host")
        _ <- TestSystem.putEnv("REDIS_PORT", "6380")
        redisConfig <- Main.redis_config
      } yield assertTrue(
        redisConfig.host == "redis.host" && redisConfig.port == 6380
      )
    }
  ).provideShared(
    ZIOAppArgs.empty,
    ZLayer.succeed(Scope.global)
  )
}
