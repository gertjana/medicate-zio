package dev.gertjanassies

import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.test._


object TestINfraAPI extends ZIOSpecDefault {
  def spec = suite("test medicate api ") {
    test("Test readiness probe") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)
        testRequest = Request.get(url=URL.root.port(port))
        _ <- TestServer.addRoutes(infra.InfraApp.routes)
        response <- client.batched(Request.get(testRequest.url / "ready"))
      } yield assertTrue(response.status == Status.Ok)
    }.provideSome[Client with Driver](TestServer.layer) 
  }.provide(
    ZLayer.succeed(Server.Config.default.onAnyOpenPort),
    Client.default,
    NettyDriver.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
  )
}

