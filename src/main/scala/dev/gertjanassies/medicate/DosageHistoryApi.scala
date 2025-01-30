package dev.gertjanassies.medicate

import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Header.AccessControlAllowOrigin
import zio.http._
import zio._
import zio.json._

object DosageHistoryApi {
  val config: CorsConfig = // remove in production
    CorsConfig(
      allowedOrigin = {
        case origin if origin.renderedValue.contains("localhost") =>
          Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      }
    )
  def routes: Routes[DosageHistoryRepository, Response] = Routes(
    Method.GET / "dosagehistory" -> handler { (request: Request) =>
      ZIO
        .serviceWithZIO[DosageHistoryRepository](_.getAll)
        .map(dosages => Response.json(dosages.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.GET / "dosagehistory" / "today" -> handler { (request: Request) =>
      ZIO
        .serviceWithZIO[DosageHistoryRepository](_.getToday)
        .map(dosages => Response.json(dosages.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    }
  ) @@ cors(config)
}
