package dev.gertjanassies

import zio._
import zio.http._
import zio.Exit.Success
import zio.redis.Redis
import zio.redis.CodecSupplier
import zio.schema._
import zio.schema.codec._

object ProtobufCodecSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}

object Main extends ZIOAppDefault:

  def port: Int = System.env("PORT") match {
    case Success(Some(value)) => value.toInt
    case _                    => 8080
  }

  override def run = Server
    .serve(medicate.MedicateApp.routes ++ infra.InfraApp.routes)
    .provide(
      ZLayer.succeed(Server.Config.default.port(port)),
      Server.live,
      Redis.local,
      ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
      medicate.MedicineRepository.layer
    )
