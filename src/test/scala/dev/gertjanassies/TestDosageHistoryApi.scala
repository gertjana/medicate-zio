package dev.gertjanassies

import zio.test._
import zio._
import zio.redis._
import zio.redis.CodecSupplier
import dev.gertjanassies.medicate.{DosageHistory, DosageHistoryRepository}
import java.time.LocalDate
import zio.redis.embedded.EmbeddedRedis
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.json.DecoderOps
import DosageHistory._
import dev.gertjanassies.medicate.DosageHistoryApi
import zio.http.Header.Origin

object TestDosageHistoryApi extends ZIOSpecDefault {
  val prefix = "test:repo:dosagehistory:tdha"

  val today = LocalDate.now().toString
  var dosageHistory = DosageHistory(
      id = "",
      date = "2024-01-01",
      time = "10:00",
      medicineId = "test1",
      amount = 10
  )
  var dosageHistory2 = dosageHistory.copy(medicineId = "test2")
  var dosageHistory3 = dosageHistory.copy(medicineId = "test3", date = today)
  def spec = {
    val testSuite = suite("Dosage History API should ")(
      test("respond correctly to getting all dosage histories") {
        for {
            repo <- ZIO.service[DosageHistoryRepository]
            _ <- repo.create(dosageHistory)
            _ <- repo.create(dosageHistory2)
            client <- ZIO.service[Client]
              port <- ZIO.serviceWithZIO[Server](_.port)
              testRequest = Request.get(url = URL.root.port(port))
              _ <- TestServer.addRoutes(medicate.DosageHistoryApi.routes)
            response <- client.batched(Request.get(testRequest.url / "dosagehistory"))
            body <- response.body.asString
            histories = body.fromJson[List[medicate.DosageHistory]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(histories.isRight) &&
          assertTrue(histories.right.get.length == 2) &&
          assertTrue(histories.right.get.map(_.date).contains(dosageHistory.date))&&
          assertTrue(histories.right.get.map(_.date).contains(dosageHistory2.date))
      },
      test("respond correctly to getting today's dosage histories") {
        for {
          repo <- ZIO.service[DosageHistoryRepository]
          _ <- repo.create(dosageHistory3)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.DosageHistoryApi.routes)
          response <- client.batched(Request.get(testRequest.url / "dosagehistory" / "today"))
          body <- response.body.asString
          histories = body.fromJson[List[medicate.DosageHistory]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(histories.isRight) &&
          assertTrue(histories.right.get.length == 1) &&
          assertTrue(histories.right.get.map(_.date).contains(today))
      },
            test("CORS Config should allow for localhost") {
        val cors_config = DosageHistoryApi.config
        assertTrue(
          cors_config.allowedOrigin(Origin("http", "localhost", None)).isDefined
        )
        assertTrue(
          cors_config
            .allowedOrigin(Origin("http", "localhost", None))
            .get
            .headerName == "Access-Control-Allow-Origin"
        )
        assertTrue(
          cors_config
            .allowedOrigin(Origin("http", "localhost", None))
            .get
            .renderedValue == "http://localhost"
        )

        assertTrue(
          cors_config.allowedOrigin(Origin("http", "example.com", None)).isEmpty
        )
      }
    ) @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"${prefix}*").returning[String]
          _ <- if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
              else ZIO.unit
        } yield ()
      ) 
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        DosageHistoryRepository.layer(prefix),
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
        DosageHistoryRepository.layer(prefix),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    }
  }
}
