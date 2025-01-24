package dev.gertjanassies

import zio._
import zio.http._
import zio.redis.Redis
import zio.redis.CodecSupplier
import zio.schema._
import zio.schema.codec._

object ProtobufCodecSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}

object Main extends ZIOAppDefault {

  def port = 8080
  override def run = Server
    .serve(medicate.MedicateApi.routes ++ infra.InfraApp.routes)
    .provide(
      ZLayer.succeed(Server.Config.default.port(port)),
      Server.live,
      Redis.local,
      ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
      medicate.MedicineRepository.layer("prod:medicine:")
    )
}
