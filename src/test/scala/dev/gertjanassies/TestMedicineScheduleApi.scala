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

object TestMedicineScheduleApi extends ZIOSpecDefault {
  val prefix = "test:api:schedule:"
  val medicine_prefix = "test:api:medicine:"
  def spec = {
    val testSchedule1 = MedicineSchedule(
      id = "1",
      medicineId = "1",
      time = "12:00",
      amount = 1.0
    )
    val testSchedule2 = testSchedule1.copy(id = "2", medicineId = "2")

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
