package dev.gertjanassies

import zio._
import zio.redis._
import zio.schema.{Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test._
import zio.test.Assertion._
import zio.redis.embedded.EmbeddedRedis
import medicate._

object TestMedicineRepository extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  val medicine_prefix = "test:repo:medicine:tmr"

  val testMedicine = ApiMedicine(
    name = "Test",
    dose = 1.0,
    unit = "mg",
    stock = 10
  )

  def spec = {
    val testSuite = suite("MedicineRepository should")(
      test("be able to get an empty list") {
        for {
          repo <- ZIO.service[MedicineRepository]
          medicines <- repo.getAll
        } yield assert(medicines)(isEmpty)
      },
      test("be able set and get a medication") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          gotten <- repo.getById(id)
        } yield assertTrue(
          gotten.isDefined && gotten.get.name == testMedicine.name
        )
      },
      test("be able to get multiple medications") {
        val m1 = testMedicine.copy(name = "test_get_all1")
        val m2 = testMedicine.copy(name = "test_get_all2")
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineRepository]
          id1 <- repo.create(m1)
          id2 <- repo.create(m2)
          gotten <- repo.getAll
        } yield assertTrue(gotten.length == 2) &&
          assertTrue(gotten.map(_.id).contains(id1)) &&
          assertTrue(gotten.map(_.id).contains(id2))
      },
      test("be able to update a Medication") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          updated = testMedicine.copy(stock = 20)
          _ <- repo.update(id, updated)
          gotten <- repo.getById(id)
        } yield assertTrue(
          gotten.isDefined && gotten.get.stock == updated.stock
        )
      },
      test("be able to delete a Medication") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          _ <- repo.delete(id)
          gotten <- repo.getById(id)
        } yield assert(gotten)(isNone)
      },
      test("be able to add stock to a Medication") {
        for {
          redis <- ZIO.service[Redis]
          repo <- ZIO.service[MedicineRepository]
          id <- repo.create(testMedicine)
          _ <- repo.addStock(id, 10)
          gotten <- repo.getById(id)
        } yield assertTrue(gotten.isDefined && gotten.get.stock == 20)
      }
    ) @@ TestAspect.sequential
      @@ TestAspect.after(
        for {
          redis <- ZIO.service[Redis]
          keys <- redis.keys(s"${medicine_prefix}*").returning[String]
          _ <-
            if (keys.nonEmpty) ZIO.foreach(keys)(key => redis.del(key))
            else ZIO.unit
        } yield ()
      )

    // EmbeddedRedis does not work on MacOS Sonoma/Seqoia due to tightened security
    if (scala.sys.env.contains("EMBEDDED_REDIS")) {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        EmbeddedRedis.layer,
        Redis.singleNode,
        MedicineRepository.layer(medicine_prefix)
      )
    } else {
      testSuite.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineRepository.layer(medicine_prefix)
      )
    }
  }
}
