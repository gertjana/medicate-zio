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
  val schedule_prefix = "test:api:schedule:tmsa"
  val medicine_prefix = "test:api:medicine:tmsa"
  val dosage_prefix = "test:api:dosage:tmsa"
  def spec = {
    val testMedicine1 = Medicine(
      id = "1",
      name = "Test",
      dose = 1.0,
      unit = "mg",
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
    val testSchedule3 =
      testSchedule1.copy(id = "3", medicineId = "1", time = "09:00")

    val testSuite = suite("Medicate Medicine Schedule API should ")(
      test("respond correctly to getting a list of schedules") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          m_id2 <- m_repo.create(testMedicine2)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          s_id2 <- s_repo.create(testSchedule2.copy(medicineId = m_id2))
          s_id3 <- s_repo.create(testSchedule3.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(Request.get(testRequest.url / "schedules"))
          body <- response.body.asString
          medicines = body.fromJson[List[medicate.MedicineSchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicines.isRight) &&
          assertTrue(medicines.toOption.get.length == 3) &&
          assertTrue(medicines.toOption.get.map(_.id).contains(s_id1)) &&
          assertTrue(medicines.toOption.get.map(_.id).contains(s_id2)) &&
          assertTrue(medicines.toOption.get.map(_.id).contains(s_id3))
      },
      test("respond correctly to getting a single schedule") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          testRequest = Request.get(url = URL.root.port(port))
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.get(testRequest.url / "schedules" / s_id1)
          )
          body <- response.body.asString
          medicine_schedule = body.fromJson[medicate.MedicineSchedule]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicine_schedule.isRight) &&
          assertTrue(medicine_schedule.toOption.get.id == s_id1) &&
          assertTrue(medicine_schedule.toOption.get.medicineId == m_id1) &&
          assertTrue(
            medicine_schedule.toOption.get.time == testSchedule1.time
          ) &&
          assertTrue(
            medicine_schedule.toOption.get.amount == testSchedule1.amount
          )
      },
      test("respond correctly to creating a schedule") {
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
      },
      test("respond correctly to updating a schedule") {
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
      },
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
      },
      test("be able to get a daily schedule") {
        for {
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.get(URL.root.port(port) / "schedules" / "daily")
          )
          body <- response.body.asString
          daily = body.fromJson[List[medicate.DailySchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(daily.isRight)
      },
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
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"$medicine_prefix*").returning[String]
          _ <-
            if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
            else ZIO.unit
          keys <- redis.keys(s"$dosage_prefix*").returning[String]
          _ <-
            if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
            else ZIO.unit
          keys <- redis.keys(s"$schedule_prefix*").returning[String]
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
        MedicineScheduleRepository.layer(schedule_prefix),
        MedicineRepository.layer(medicine_prefix),
        DosageHistoryRepository.layer(schedule_prefix),
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
        MedicineScheduleRepository.layer(schedule_prefix),
        MedicineRepository.layer(medicine_prefix),
        DosageHistoryRepository.layer(dosage_prefix),
        TestServer.layer,
        Client.default,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        NettyDriver.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
      )
    }

  }
}
