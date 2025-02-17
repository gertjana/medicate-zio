package dev.gertjanassies

import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.test._
import zio.json.EncoderOps
import zio.json.DecoderOps
import dev.gertjanassies.medicate._
import java.time.LocalDate

object TestMedicineScheduleApi extends ZIOSpecDefault {
  val schedule_prefix = "test:api:schedule:tmsa"
  val medicine_prefix = "test:api:medicine:tmsa"
  val dosage_prefix = "test:api:dosage:tmsa"

  def spec = {
    val testMedicine1 = ApiMedicine(
      name = "Test",
      dose = 1.0,
      unit = "mg",
      stock = 10
    )
    val testMedicine2 = testMedicine1.copy(name = "Test2")

    val testSchedule1 = ApiMedicineSchedule(
      medicineId = "1",
      time = "12:00",
      amount = 1.0
    )
    val testSchedule2 = testSchedule1.copy(medicineId = "2")
    val testSchedule3 = testSchedule1.copy(medicineId = "1", time = "09:00")

    val testDosageHistory = ApiDosageHistory(
      date = LocalDate.now().toString,
      time = "12:00",
      medicineId = "?",
      amount = 1.0
    )
    val testDosageHistory2 =
      testDosageHistory.copy(date = LocalDate.now().minusDays(1).toString)

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
          medicine = body.fromJson[medicate.ApiMedicineSchedule]
        } yield assertTrue(response.status == Status.Created) &&
          assertTrue(medicine.isRight) &&
          assertTrue(medicine.toOption.get == testSchedule1)
      },
      test("respond correctly to updating a schedule") {
        for {
          repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- repo.create(testSchedule1)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.put(
              URL.root.port(port) / "schedules" / s_id1,
              Body.fromString(testSchedule2.toJson)
            )
          )
          body <- response.body.asString
          medicine = body.fromJson[medicate.ApiMedicineSchedule]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(medicine.isRight) &&
          assertTrue(medicine.toOption.get == testSchedule2)
      },
      test("be able to delete a schedule") {
        for {
          repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- repo.create(testSchedule1)
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.delete(
              URL.root.port(port) / "schedules" / s_id1
            )
          )
        } yield assertTrue(response.status == Status.NoContent)
      },
      test("be able to get a daily schedule") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.get(URL.root.port(port) / "schedules" / "daily")
          )
          body <- response.body.asString
          daily = body.fromJson[List[medicate.DailySchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(daily.isRight) &&
          assertTrue(daily.toOption.get.length == 1) &&
          assertTrue(daily.toOption.get.head.time == "12:00") &&
          assertTrue(
            daily.toOption.get.head.medicines.length == 1
          ) &&
          assertTrue(
            daily.toOption.get.head.medicines.head._1.isDefined
          )
      },
      test("be able to get a past daily schedules") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          d_repo <- ZIO.service[DosageHistoryRepository]
          d_id1 <- d_repo.create(testDosageHistory.copy(medicineId = m_id1))
          d_id2 <- d_repo.create(testDosageHistory2.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.get(URL.root.port(port) / "schedules" / "past")
          )
          body <- response.body.asString
          daily = body.fromJson[List[medicate.DailyScheduleWithDate]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(daily.isRight) &&
          assertTrue(daily.toOption.get.length == 1) &&
          assertTrue(daily.toOption.get.head.schedules.head.time == "12:00")
      },
      test("be able to get a past daily schedules with no schedules") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.get(URL.root.port(port) / "schedules" / "past")
          )
          body <- response.body.asString
          daily = body.fromJson[List[medicate.DailyScheduleWithDate]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(daily.isRight) &&
          assertTrue(daily.toOption.get.length == 0)
      },
      test("be able to take a dosage today") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.post(
              (URL.root.port(port) / "schedules" / "takedose")
                .setQueryParams(Map("time" -> Chunk("12:00"))),
              Body.fromString("")
            )
          )
          body <- response.body.asString
          dosage = body.fromJson[List[medicate.DailySchedule]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(dosage.isRight) &&
          assertTrue(dosage.toOption.get.length == 1) &&
          assertTrue(dosage.toOption.get.head.time == "12:00") &&
          assertTrue(dosage.toOption.get.head.medicines.length == 1) &&
          assertTrue(dosage.toOption.get.head.medicines.head._1.isDefined) &&
          assertTrue(dosage.toOption.get.head.medicines.head._1.get.stock == 9)
      },
      test("be able to take a dosage in the past") {
        for {
          _ <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          m_id1 <- m_repo.create(testMedicine1)
          s_repo <- ZIO.service[MedicineScheduleRepository]
          s_id1 <- s_repo.create(testSchedule1.copy(medicineId = m_id1))
          client <- ZIO.service[Client]
          port <- ZIO.serviceWithZIO[Server](_.port)
          _ <- TestServer.addRoutes(medicate.MedicineScheduleApi.routes)
          response <- client.batched(
            Request.post(
              (URL.root.port(port) / "schedules" / "takedose").setQueryParams(
                Map(
                  "time" -> Chunk("12:00"),
                  "date" -> Chunk(LocalDate.now().minusDays(1).toString)
                )
              ),
              Body.fromString("")
            )
          )
          body <- response.body.asString
          dosage = body.fromJson[List[medicate.DailyScheduleWithDate]]
        } yield assertTrue(response.status == Status.Ok) &&
          assertTrue(dosage.isRight) &&
          assertTrue(dosage.toOption.get.length == 1) &&
          assertTrue(
            dosage.toOption.get.head.date == LocalDate
              .now()
              .minusDays(1)
              .toString
          ) &&
          assertTrue(dosage.toOption.get.head.schedules.head.time == "12:00") &&
          assertTrue(
            dosage.toOption.get.head.schedules.head.medicines.length == 1
          ) &&
          assertTrue(
            dosage.toOption.get.head.schedules.head.medicines.head._1.isDefined
          ) &&
          assertTrue(
            dosage.toOption.get.head.schedules.head.medicines.head._1.get.stock == 9
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
        DosageHistoryRepository.layer(dosage_prefix),
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
