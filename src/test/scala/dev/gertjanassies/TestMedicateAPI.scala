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
import dev.gertjanassies.medicate.MedicateApi
import zio.http.Header.Origin
import zio.http.Middleware.CorsConfig

object TestMedicateAPI extends ZIOSpecDefault {
  val prefix = "test:api:medicine:"

  val testMedicine = Medicine.create(
    id = "test1",
    name = "Test",
    dose = 1.0,
    unit = "mg",
    amount = 2.0,
    stock = 10
  )
  val testMedicine2 = testMedicine.copy(id = "test2")
  val testMedicine3 = testMedicine.copy(id = "test3")
  val testMedicine4 = testMedicine.copy(id = "test4")

  def spec = {
    val testSuite = suite("Medicate API should ")(
      test("respond correctly to getting a list of medications") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _        <- TestServer.addRoutes(medicate.MedicateApi.routes)
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
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
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
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
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
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
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
      test("not be able to update a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.put(
              testRequest.url / "medicines" / "non-existing-id",
              Body.fromString(testMedicine2.toJson)
            )
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("be able to have stock added") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request
            .get(url = URL.root.port(port))
            .addQueryParam("amount", "5")
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines" / testMedicine.id / "addStock",
              Body.empty
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok && body
            .fromJson[Medicine]
            .map(_.stock) == Right(15.0)
        )
      },
      test("be able to take a dose") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines" / testMedicine.id / "takeDose",
              Body.empty
            )
          )
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok && body
            .fromJson[Medicine]
            .map(_.stock) == Right(13.0)
        )
      },
      test("not be able to add stock when the amount queryparam is absent") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines" / testMedicine.id / "addStock",
              Body.empty
            )
          )
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("be able to delete a single medication") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.delete(testRequest.url / "medicines" / testMedicine3.id)
          )
        } yield assertTrue(response.status == Status.NoContent)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testMedicine3.id}", testMedicine3.toJson)
        } yield ()
      ),
      test("not be able to create a medicine with invalid json body") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.post(
              testRequest.url / "medicines",
              Body.fromString("invalid json")
            )
          )
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("be able to respond correctly to an invalid path") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.get(testRequest.url / "non-existing-path")
          )
        } yield assertTrue(response.status == Status.NotFound)
      }, 
      test("be able to not delete a non-existing medicine") {
        for {
          client <- ZIO.service[Client]
          port   <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicateApi.routes)
          response <- client.batched(
            Request.delete(testRequest.url / "medicines" / "non-existing-id")
          )
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("CORS Config should allow for localhost") {
        val cors_config = MedicateApi.config
        assertTrue(cors_config.allowedOrigin(Origin("http", "localhost", None)).isDefined)
        assertTrue(cors_config.allowedOrigin(Origin("http", "localhost", None)).get.headerName == "Access-Control-Allow-Origin")
        assertTrue(cors_config.allowedOrigin(Origin("http", "localhost", None)).get.renderedValue == "http://localhost")

        assertTrue(cors_config.allowedOrigin(Origin("http", "example.com", None)).isEmpty)
      }
    ) @@ TestAspect.sequential
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
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
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
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      )
    }
  }
}
