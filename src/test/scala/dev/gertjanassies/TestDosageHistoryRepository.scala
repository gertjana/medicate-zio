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

  val dosage_history = DosageHistory(
    date = "2025-01-01",
    time = "12:00",
    medicineId = "test1",
    amount = 1.0
  )
  val dosage_history2 =
    dosage_history.copy(medicineId = "test2", time = "09:00")
  val dosage_history3 = dosage_history.copy(date = "2025-01-02")

  def spec = {
    val testSuite = suite("DosageHistoryRepository should") {
      test("be able to get all history") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[DosageHistoryRepository]
          _ <- repo.create(dosage_history)
          _ <- repo.create(dosage_history2)
          _ <- repo.create(dosage_history3)
          history <- ZIO.serviceWithZIO[DosageHistoryRepository](_.getAll)
        } yield assertTrue(
            history.length == 3 &&
            history.contains(dosage_history) && 
            history.contains(dosage_history2) && 
            history.contains(dosage_history3) &&
            history.head == dosage_history3 && history.last == dosage_history2
        )
      }
    } @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"${dosage_prefix}*").returning[String]
          _ <- if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
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
