package dev.gertjanassies

import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.test._
import dev.gertjanassies.medicate.MedicineRepository
import dev.gertjanassies.medicate.Medicine._
import zio.json.DecoderOps

object TestMedicateAPI extends ZIOSpecDefault {
  def spec = {
    val testSuite = suite("Medicate API should ")(
      test("respond correctly to getting a list of medications") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _        <- TestServer.addRoutes(medicate.MedicateApp.routes)
          response <- client.batched(Request.get(testRequest.url / "medicines"))
          body     <- response.body.asString
          medicines = body.fromJson[List[medicate.Medicine]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicines.isRight)
      } @@ TestAspect.ignore,
      test("respond correctly to getting a single non existing medication") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApp.routes)
          response <- client.batched(
            Request.get(testRequest.url / "medicines" / "non-existing-id")
          )
        } yield assertTrue(response.status == Status.NotFound)
      }
    )
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineRepository.layer("test:api:medicine:"),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineRepository.layer("test:api:medicine:"),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    }
  }
}
