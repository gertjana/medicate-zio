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

  val medicine =
    medicate.Medicine(
      id = "test1",
      name = "Test",
      amount = 2.0,
      dose = 1.0,
      stock = 10
    )
    
  def spec = {
    val useEmbedded = scala.sys.env.contains("EMBEDDED_REDIS")
    val suit = suite("MedicineRepository should")(
    test("set and get values") {
        for {
          redis  <- ZIO.service[Redis]
          repo   <- ZIO.service[MedicineRepository]
          _      <- repo.create(medicine)
          gotten <- repo.getById(medicine.id)
        } yield assert(gotten)(isSome[medicate.Medicine](equalTo(medicine)))
      }
    )
    
    if (useEmbedded) {
      suit.provideShared(
        EmbeddedRedis.layer, 
        Redis.singleNode,
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        MedicineRepository.layer("test:medicine:")
      )
    } else {
      suit.provideShared(
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Redis.local,
        MedicineRepository.layer("test:medicine:")
      )
    }
  }
    // TestAspect.ignore
}
