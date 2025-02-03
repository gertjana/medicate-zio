package dev.gertjanassies

import zio._
import zio.redis._
import zio.schema.{Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test._
import zio.test.Assertion._
import zio.redis.embedded.EmbeddedRedis
import medicate._

object TestMedicineScheduleRepository extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  val schedule_prefix = "test:repo:schedule:tmsr:"
  val medicine_prefix = "test:repo:medicine:tmsr:"
  val dosage_prefix = "test:repo:dosage:tmsr:"
  def spec = {
    val testSuite = suite("MedicineRepository should")(
      test("be able to get an empty schedule") {
        for {
          repo <- ZIO.service[MedicineScheduleRepository]
          schedule <- repo.getSchedule()
        } yield assert(schedule)(isEmpty)
      },
      test("be able to set and get a schedule") {
        val medicine = Medicine(
          id = "1",
          name = "test_medicine1",
          dose = 1.0,
          unit = "mg",
          stock = 10
        )
        val s = MedicineSchedule.create(
          id = "test_set_get",
          medicineId = "",
          time = "12:00",
          amount = 1.0
        )
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineScheduleRepository]
          m_repo <- ZIO.service[MedicineRepository]
          id <- m_repo.create(medicine)
          _ <- repo.create(s.copy(medicineId = id))
          gotten <- repo.getById(s.id)
        } yield assert(gotten)(isSome[medicate.MedicineSchedule](equalTo(s.copy(medicineId = id))))
      },
      test("be able to delete a schedule") {
        val s = MedicineSchedule.create(
          id = "test_delete",
          medicineId = "",
          time = "12:00",
          amount = 1.0
        )
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineScheduleRepository]
          _ <- repo.create(s)
          _ <- repo.delete(s.id)
          gotten <- repo.getById(s.id)
        } yield assert(gotten)(isNone)
      },
      test("to return a daily schedule") {
        val medicine1 = Medicine(
          id = "",
          name = "test_medicine1",
          dose = 1.0,
          unit = "mg",
          stock = 10
        )
        val medicine2 = medicine1.copy(id = "", name = "test_medicine2")
        val schedule1 = MedicineSchedule.create(
          id = "1",
          medicineId = "",
          time = "12:00",
          amount = 1.0
        )
        val schedule2 = schedule1.copy(id = "2")
        val schedule3 = schedule1.copy(id = "3", time = "09:00")
        
        for {
          redis <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          id1 <- m_repo.create(medicine1)
          id2 <- m_repo.create(medicine2)
          ms_repo <- ZIO.service[MedicineScheduleRepository]
          _ <- ms_repo.create(schedule1.copy(medicineId = id1))
          _ <- ms_repo.create(schedule2.copy(medicineId = id2))
          _ <- ms_repo.create(schedule3.copy(medicineId = id1))
          actual <- ms_repo.getSchedule()
        } yield (assertTrue(actual.length == 2) && 
          assertTrue(actual.head.time == "09:00") &&
          assertTrue(actual.head.medicines.length == 1) &&
          assertTrue(actual.last.time == "12:00") &&
          assertTrue(actual.last.medicines.length == 2)
        )
      }
    ) @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          d_keys <- redis.keys(s"${dosage_prefix}*").returning[String]
          _ <- if (d_keys.nonEmpty) ZIO.foreach(d_keys)(key => redis.del(key))
               else ZIO.unit
          s_keys <- redis.keys(s"${schedule_prefix}*").returning[String]
          _ <- if (s_keys.nonEmpty) ZIO.foreach(s_keys)(key => redis.del(key))
               else ZIO.unit
          m_keys <- redis.keys(s"${medicine_prefix}*").returning[String]
          _ <- if (m_keys.nonEmpty) ZIO.foreach(m_keys)(key => redis.del(key))
               else ZIO.unit
        } yield ()
    )
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineRepository.layer(medicine_prefix),
        MedicineScheduleRepository.layer(schedule_prefix),
        DosageHistoryRepository.layer(dosage_prefix)
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineRepository.layer(medicine_prefix),
        MedicineScheduleRepository.layer(schedule_prefix),
        DosageHistoryRepository.layer(dosage_prefix)
      )
    }
  }
}
