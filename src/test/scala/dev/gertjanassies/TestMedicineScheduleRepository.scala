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
  val prefix = "test:repo:schedule:"
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
          _ <- redis.del(s"${prefix}test_set_get")
        } yield ()
      ),
    )
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineScheduleRepository.layer("test:repo:schedule:")
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineScheduleRepository.layer("test:repo:schedule:")
      )
    }
  }
}