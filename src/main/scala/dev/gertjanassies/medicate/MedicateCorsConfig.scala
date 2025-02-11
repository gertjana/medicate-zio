package dev.gertjanassies.medicate

import zio.http.Middleware.CorsConfig
import zio.http.Header.AccessControlAllowOrigin

object MedicateCorsConfig {
  val localhost: CorsConfig = // remove in production
    CorsConfig(
      allowedOrigin = {
        case origin if origin.renderedValue.contains("localhost") =>
          Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      }
    )

  val allAllowed: CorsConfig =
    CorsConfig(allowedOrigin = _ => Some(AccessControlAllowOrigin.All))
}
