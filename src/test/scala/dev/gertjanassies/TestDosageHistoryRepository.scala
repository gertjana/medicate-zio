package dev.gertjanassies

import zio._
import zio.test._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.schema.{Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import medicate._

object TestDosageHistoryRepository extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  val dosage_prefix = "test:dosage:tdhr:"
  val today = java.time.LocalDate.now().toString

  val dosage_history = DosageHistory(
    id = "",
    date = "2025-01-01",
    time = "12:00",
    medicineId = "test1",
    amount = 1.0
  )
  val dosage_history2 =
    dosage_history.copy(medicineId = "test2", time = "09:00")
  val dosage_history3 = dosage_history.copy(medicineId = "test3", date = today)

  def spec = {
    val testSuite = suite("DosageHistoryRepository should")(
      test("be able to get all history") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[DosageHistoryRepository]
          id1 <- repo.create(dosage_history)
          id2 <- repo.create(dosage_history2)
          id3 <- repo.create(dosage_history3)
          history <- ZIO.serviceWithZIO[DosageHistoryRepository](_.getAll)
        } yield assertTrue(
          history.length == 3 &&
            history.map(_.id).contains(id1) &&
            history.map(_.id).contains(id2) &&
            history.map(_.id).contains(id3) &&
            history.head.medicineId == dosage_history3.medicineId &&
            history.last.medicineId == dosage_history2.medicineId
        )
      },
      test("be able to get today's history") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[DosageHistoryRepository]
          id1 <- repo.create(dosage_history)
          id2 <- repo.create(dosage_history2)
          id3 <- repo.create(dosage_history3)
          history <- ZIO.serviceWithZIO[DosageHistoryRepository](_.getToday)
        } yield assertTrue(
          history.length == 1 &&
            history.head.medicineId == dosage_history3.medicineId &&
            history.head.date == today
        )
      }
    ) @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"${dosage_prefix}*").returning[String]
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
        DosageHistoryRepository.layer(dosage_prefix)
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        DosageHistoryRepository.layer(dosage_prefix)
      )
    }
  }
}
