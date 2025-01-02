package dev.gertjanassies

import zio._
import zio.redis._
// import zio.redis.embedded.EmbeddedRedis
import zio.schema.{Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test._
import zio.test.Assertion._
import dev.gertjanassies.medicate.MedicineRepository
import zio.redis.embedded.EmbeddedRedis

object TestMedicineRepository extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  val prefix = "test:repo:medicine:"

  val medicine =
    medicate.Medicine.create(
      id = "test_template",
      name = "Test",
      amount = 2.0,
      dose = 1.0,
      stock = 10
    )

  def spec = {
    val testSuite = suite("MedicineRepository should")(
      test("be able set and get a medication") {
        val m = medicine.copy(id = "test_set_get")
        for {
          redis  <- ZIO.service[Redis]
          repo   <- ZIO.service[MedicineRepository]
          _      <- repo.create(m)
          gotten <- repo.getById(m.id)
        } yield assert(gotten)(isSome[medicate.Medicine](equalTo(m)))
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.del(s"${prefix}test_set_get")
        } yield ()
      ),
      test("be able to get multiple medications") {
        val m1 = medicine.copy(id = "test_get_all1")
        val m2 = medicine.copy(id = "test_get_all2")
        for {
          redis  <- ZIO.service[Redis]
          repo   <- ZIO.service[MedicineRepository]
          _      <- repo.create(m1)
          _      <- repo.create(m2)
          gotten <- repo.getAll
        } yield assert(gotten)(
          hasSameElements(List(m1,m2))
        )
      } @@ TestAspect.after (
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.del(s"${prefix}test_get_all1")
          _     <- redis.del(s"${prefix}test_get_all2")
        } yield ()
      ),
      test("be able to update a Medication") {
        val m = medicine.copy(id = "test_update")
        for {
          redis  <- ZIO.service[Redis]
          repo   <- ZIO.service[MedicineRepository]
          _      <- repo.create(m)
          updated = medicine.copy(amount = 1.0)
          _      <- repo.update(m.id, updated)
          gotten <- repo.getById(m.id)
        } yield assert(gotten)(isSome[medicate.Medicine](equalTo(updated)))
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.del(s"${prefix}test_update")
        } yield ()
      ),
      test("be able to delete a Medication") {
        val m = medicine.copy(id = "test_delete")
        for {
          redis <- ZIO.service[Redis]
          repo  <- ZIO.service[MedicineRepository]
          _     <- repo.create(m)
          _     <- repo.delete(m.id)
          gotten <- repo.getById(m.id)
        } yield assert(gotten)(isNone)
      } @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.del(s"${prefix}test_delete")
        } yield ()
      )
    ) @@ TestAspect.sequential

    // EmbeddedRedis does not work on MacOS Sonoma/Seqoia due to tightened security
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineRepository.layer("test:repo:medicine:")
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineRepository.layer("test:repo:medicine:")
      )
    }
  }
}
