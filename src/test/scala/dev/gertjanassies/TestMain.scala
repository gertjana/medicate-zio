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
        port = Main.port
      } yield assertTrue(port == 8080)
    },
    test("set the port according to the environment") {
      for {
        _ <- TestSystem.putEnv("PORT", "8082")
        port = Main.port
      } yield assertTrue(port == 8082)
    } @@ TestAspect.ignore
  ).provideShared(
    ZIOAppArgs.empty,
    ZLayer.succeed(Scope.global)
  )
}
