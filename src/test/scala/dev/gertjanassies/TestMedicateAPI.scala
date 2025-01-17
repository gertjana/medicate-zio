package dev.gertjanassies

import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.test._
import dev.gertjanassies.medicate.MedicineRepository
import dev.gertjanassies.medicate.Medicine
import Medicine._
import zio.json.EncoderOps
import zio.json.DecoderOps

object TestMedicateAPI extends ZIOSpecDefault {
  val prefix = "test:api:medicine:"

  val testMedicine = Medicine.create(
    id = "test1",
    name = "Test",
    amount = 2.0,
    dose = 1.0,
    stock = 10
  )
  val testMedicine2 = testMedicine.copy(id = "test2")
  val testMedicine3 = testMedicine.copy(id = "test3")

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
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.set(s"$prefix${testMedicine.id}", testMedicine.toJson)
          _ <- redis.set(s"$prefix${testMedicine2.id}", testMedicine2.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.del(s"$prefix${testMedicine.id}")
          _     <- redis.del(s"$prefix${testMedicine2.id}")
        } yield ()
      ),
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
      },
      test("be able to create a single medication") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApp.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines",
              Body.fromString(testMedicine.toJson)
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Created && body == testMedicine.toJson
        )
      },
      test("be able to update a single medication") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApp.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / testMedicine.id,
              Body.fromString(testMedicine2.toJson)
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok && body == testMedicine2.toJson
        )
      },
      test("be able to delete a single medication") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApp.routes)
          response <- client.batched(
            Request.delete(testRequest.url / "medicines" / testMedicine3.id)
          )
        } yield assertTrue(response.status == Status.NoContent)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testMedicine3.id}", testMedicine3.toJson)
        } yield ()
      )
    )
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineRepository.layer(prefix),
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
        MedicineRepository.layer(prefix),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    }
  }
}
