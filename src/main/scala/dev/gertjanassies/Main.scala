package dev.gertjanassies

import zio._
import zio.http._
import zio.redis.Redis
import zio.redis.CodecSupplier
import zio.schema._
import zio.schema.codec._
import zio.redis.RedisConfig
import medicate._
import infra._

object ProtobufCodecSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}

object Main extends ZIOAppDefault {
  def serverConfig = for {
    serverPort <- System.env("PORT").map(_.getOrElse("8080").toInt)
    serverConfig = Server.Config.default.port(serverPort).acceptContinue(true)
    _ <- ZIO.logInfo(s"Server running on port: $serverPort")
  } yield serverConfig

  def redis_config = for {
    redisHost <- System.env("REDIS_HOST").map(_.getOrElse("localhost"))
    redisPort <- System.env("REDIS_PORT").map(_.getOrElse("6379").toInt)
    redisConfig <- ZIO.succeed(RedisConfig(host = redisHost, port = redisPort))
    _ <- ZIO.logInfo(s"Redis connection: $redisHost:$redisPort")
  } yield redisConfig

  override def run =

    Server
      .serve(
        MedicineApi.routes ++
          MedicineScheduleApi.routes ++
          DosageHistoryApi.routes ++
          InfraApp.routes
      )
      .provide(
        ZLayer.fromZIO(serverConfig),
        Server.live,
        ZLayer.fromZIO(redis_config),
        Redis.singleNode,
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        MedicineRepository.layer("prod:medicine:"),
        MedicineScheduleRepository.layer("prod:schedule:"),
        DosageHistoryRepository.layer("prod:dosage:")
      )
}
