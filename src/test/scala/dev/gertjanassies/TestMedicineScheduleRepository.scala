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
  val schedule_prefix = "test:repo:schedule:"
  val medicine_prefix = "test:repo:medicine:"
  val dosage_prefix = "test:repo:dosage:"
  def spec = {
    val testSuite = suite("MedicineRepository should")(
      test("be able to set and get a schedule") {
        val s = MedicineSchedule.create(
          id = "test_set_get",
          medicineId = "1",
          time = "12:00",
          amount = 1.0
        )
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineScheduleRepository]
          _ <- repo.create(s)
          gotten <- repo.getById(s.id)
        } yield assert(gotten)(isSome[medicate.MedicineSchedule](equalTo(s)))
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"${schedule_prefix}test_set_get")
        } yield ()
      ),
      test("be able to delete a schedule") {
        val s = MedicineSchedule.create(
          id = "test_delete",
          medicineId = "1",
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
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"${schedule_prefix}test_delete")
        } yield ()
      ),
      test("to return a daily schedule") {
        val medicine1 = Medicine.create(
          id = "1",
          name = "test_medicine1",
          dose = 1.0,
          unit = "mg",
          stock = 10
        )
        val medicine2 = medicine1.copy(id = "2", name = "test_medicine2")
        val schedule1 = MedicineSchedule.create(
          id = "1",
          medicineId = "1",
          time = "12:00",
          amount = 1.0
        )
        val schedule2 = schedule1.copy(id = "2", medicineId = "2")
        val schedule3 = schedule1.copy(id = "3", time = "09:00")
        val expected = List(
          DailySchedule(
            time = "09:00",
            medicines = List(
              (Some(medicine1), 1.0)
            )
          ),
          DailySchedule(
            time = "12:00",
            medicines = List(
              (Some(medicine1), 1.0),
              (Some(medicine2), 1.0)
            )
          )
        )
        for {
          redis <- ZIO.service[Redis]
          m_repo <- ZIO.service[MedicineRepository]
          _ <- m_repo.create(medicine1)
          _ <- m_repo.create(medicine2)
          ms_repo <- ZIO.service[MedicineScheduleRepository]
          _ <- ms_repo.create(schedule1)
          _ <- ms_repo.create(schedule2)
          _ <- ms_repo.create(schedule3)
          actual <- ms_repo.getSchedule()
        } yield (assert(actual)(equalTo(expected)))
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _ <- redis.del(s"${schedule_prefix}1")
          _ <- redis.del(s"${schedule_prefix}2")
          _ <- redis.del(s"${schedule_prefix}3")
          _ <- redis.del(s"${medicine_prefix}1")
          _ <- redis.del(s"${medicine_prefix}2")
        } yield ()
      )
    ) @@ TestAspect.sequential
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
