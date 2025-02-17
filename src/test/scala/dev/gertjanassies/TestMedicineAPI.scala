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
import dev.gertjanassies.medicate.ApiMedicine

object TestMedicineApi extends ZIOSpecDefault {
  val prefix = "test:api:medicine:tma"

  val testMedicine = ApiMedicine(
    name = "Test",
    dose = 1.0,
    unit = "mg",
    stock = 10
  )
  val testMedicine2 = testMedicine.copy(name = "test2")
  val testMedicine3 = testMedicine.copy(name = "test3")
  val testMedicine4 = testMedicine.copy(name = "test4")

  def spec = {
    val testSuite = suite("Medicate Medicine API should ")(
      test("respond correctly to getting a list of medications") {
        for {
          repo <- ZIO.service[MedicineRepository]
          id1 <- repo.create(testMedicine)
          id2 <- repo.create(testMedicine2)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(Request.get(testRequest.url / "medicines"))
          body <- response.body.asString
          medicines = body.fromJson[List[medicate.Medicine]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicines.isRight) &&
          assertTrue(medicines.right.get.length == 2) &&
          assertTrue(medicines.right.get.map(_.id).contains(id1)) &&
          assertTrue(medicines.right.get.map(_.id).contains(id2))
      },
      test("respond correctly to getting a single non existing medication") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.get(testRequest.url / "medicines" / "non-existing-id")
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("be able to create a single medication") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              URL.root.port(port) / "medicines",
              Body.fromString(testMedicine.toJson)
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Created && body.fromJson[Medicine].isRight
        )
      },
      test("be able to update a single medication") {
        for {
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / id,
              Body.fromString(testMedicine2.toJson)
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok &&
            body.fromJson[Medicine].isRight &&
            body.fromJson[Medicine].right.get.id == id &&
            body.fromJson[Medicine].right.get.stock == testMedicine2.stock
        )
      },
      test("not be able to update a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / "non-existing-id",
              Body.fromString(testMedicine2.toJson)
            )
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("be able to delete a single medication") {
        for {
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.delete(testRequest.url / "medicines" / id)
          )
        } yield assertTrue(response.status == Status.NoContent)
      },
      test("not be able to create a medicine with invalid json body") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines",
              Body.fromString("invalid json")
            )
          )
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("not be able to update a medicine with invalid json body") {
        for {
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / id,
              Body.fromString("invalid json")
            )
          )
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("be able to respond correctly to an invalid path") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.get(testRequest.url / "non-existing-path")
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("be able to not delete a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.delete(testRequest.url / "medicines" / "non-existing-id")
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("not be able to update a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / "non-existing-id",
              Body.fromString(testMedicine2.toJson)
            )
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("not able to take a dose for a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines" / "non-existing-id" / "takeDose",
              Body.empty
            )
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("be able to add stock to a medication") {
        for {
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              (testRequest.url / "medicines" / id / "addStock").setQueryParams(
                Map(
                  "amount" -> Chunk("10")
                )
              ),
              Body.fromString("")
            )
          )
          body <- response.body.asString
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(body.fromJson[Medicine].isRight) &&
          assertTrue(body.fromJson[Medicine].right.get.stock == 20)
      },
      test("not be able to add stock to a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              (testRequest.url / "medicines" / "non-existing-id" / "addStock")
                .setQueryParams(
                  Map(
                    "amount" -> Chunk("10")
                  )
                ),
              Body.fromString("")
            )
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("not be able to add stock to a medicine with invalid amount") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines" / "non-existing-id" / "addStock",
              Body.fromString("")
            )
          )
        } yield assertTrue(response.status == Status.BadRequest)
      }
    ) @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"${prefix}*").returning[String]
          _ <-
            if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
            else ZIO.unit
        } yield ()
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
