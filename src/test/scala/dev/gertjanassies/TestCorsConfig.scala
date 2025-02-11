package dev.gertjanassies

import dev.gertjanassies.medicate.MedicateCorsConfig
import zio.test._
import zio.http.Middleware.CorsConfig
import zio.http.Header.Origin

object TestCorsConfig extends ZIOSpecDefault {
  def spec = suite("CorsConfig should ")(
    test("allow all origins") {
      val config: CorsConfig = MedicateCorsConfig.allAllowed
      assertTrue(
        config.allowedOrigin(Origin("http", "localhost", Some(8080))).isDefined
      )
      assertTrue(
        config.allowedOrigin(Origin("http", "whatever.com", Some(80))).isDefined
      )
    },
    test("allow only localhost") {
      val config: CorsConfig = MedicateCorsConfig.localhost
      assertTrue(
        config.allowedOrigin(Origin("http", "localhost", Some(8080))).isDefined
      )
      assertTrue(
        config.allowedOrigin(Origin("http", "whatever.com", Some(80))).isEmpty
      )
    }
  )
}
