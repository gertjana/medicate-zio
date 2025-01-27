package dev.gertjanassies

import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
// import zio.http.Header.Origin
// import zio.http.Middleware.CorsConfig
import zio.test._
import zio.json.EncoderOps
import zio.json.DecoderOps
import dev.gertjanassies.medicate._
import zio.http.Header.Origin

object TestMedicineScheduleApi extends ZIOSpecDefault {
  val prefix = "test:api:schedule:"
  val medicine_prefix = "test:api:medicine:"
  def spec = {
    val testMedicine1 = Medicine.create(
      id = "1",
      name = "Test",
      dose = 1.0,
      unit = "mg",
      amount = Some(2.0),
      stock = 10
    )
    val testMedicine2 = testMedicine1.copy(id = "2", name = "Test2")

    val testSchedule1 = MedicineSchedule(
      id = "1",
      medicineId = "1",
      time = "12:00",
      amount = 1.0
    )
    val testSchedule2 = testSchedule1.copy(id = "2", medicineId = "2")
    val testSchedule3 = testSchedule1.copy(id = "3", medicineId = "1", time = "09:00")

    val testSuite = suite("Medicate Medicine Schedule API should ")(
      test("respond correctly to getting a list of medications") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(Request.get(testRequest.url / "schedules"))
          body <- response.body.asString
          medicines = body.fromJson[List[medicate.MedicineSchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicines.isRight)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"${medicine_prefix}${testMedicine1.id}", testMedicine1.toJson)
          _ <- redis.set(s"${medicine_prefix}${testMedicine2.id}", testMedicine2.toJson)
          _ <- redis.set(s"$prefix${testSchedule1.id}", testSchedule1.toJson)
          _ <- redis.set(s"$prefix${testSchedule2.id}", testSchedule2.toJson)
          _ <- redis.set(s"$prefix${testSchedule3.id}", testSchedule3.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
          _ <- redis.del(s"$prefix${testSchedule2.id}")
          _ <- redis.del(s"$prefix${testSchedule3.id}")
          _ <- redis.del(s"${medicine_prefix}${testMedicine1.id}")
          _ <- redis.del(s"${medicine_prefix}${testMedicine2.id}")
        } yield ()
      ),
      test("respond correctly to getting a single medication") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(Request.get(testRequest.url / "schedules" / testSchedule1.id))
          body <- response.body.asString
          medicine = body.fromJson[medicate.MedicineSchedule]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicine.isRight) &&
          assertTrue(medicine.toOption.get == testSchedule1)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testSchedule1.id}", testSchedule1.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
        } yield ()
      ),
      test("respond correctly to creating a medication") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.post(
              URL.root.port(port) / "schedules",
              Body.fromString(testSchedule1.toJson)
            )
          )
          body <- response.body.asString
          medicine = body.fromJson[medicate.MedicineSchedule]
        } yield assertTrue(response.status == Status.Created) &&
          assertTrue(medicine.isRight) &&
          assertTrue(medicine.toOption.get == testSchedule1)
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
        } yield ()
      ),
      test("respond correctly to updating a medication") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.put(
              URL.root.port(port) / "schedules" / testSchedule1.id,
              Body.fromString(testSchedule2.toJson)
            )
          )
          body <- response.body.asString
          medicine = body.fromJson[medicate.MedicineSchedule]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicine.isRight) &&
          assertTrue(medicine.toOption.get == testSchedule2)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testSchedule1.id}", testSchedule1.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
        } yield ()
      ),
      test("be able to delete a schedule") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.delete(
              URL.root.port(port) / "schedules" / testSchedule1.id
            )
          )
        } yield assertTrue(response.status == Status.NoContent)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testSchedule1.id}", testSchedule1.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
        } yield ()
      ),
      test("be able to get a combined schedule") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(Request.get(URL.root.port(port) / "schedules" / "combined"))
          body <- response.body.asString
          combined = body.fromJson[List[medicate.CombinedSchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(combined.isRight)
      } @@ TestAspect.before(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.set(s"$prefix${testSchedule1.id}", testSchedule1.toJson)
          _ <- redis.set(s"$prefix${testSchedule2.id}", testSchedule2.toJson)
        } yield ()
      ) @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"$prefix${testSchedule1.id}")
          _ <- redis.del(s"$prefix${testSchedule2.id}")
        } yield ()
      ),
      test("CORS Config should allow for localhost") {
        val cors_config = MedicineApi.config
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
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineScheduleRepository.layer(prefix),
        MedicineRepository.layer(medicine_prefix),
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
        MedicineScheduleRepository.layer(prefix),
        MedicineRepository.layer(medicine_prefix),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    }

  }
}
