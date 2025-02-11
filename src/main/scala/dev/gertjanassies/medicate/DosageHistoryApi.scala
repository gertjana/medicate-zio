package dev.gertjanassies.medicate

import zio.http.Middleware.cors
import zio.http._
import zio._
import zio.json._

object DosageHistoryApi {
  def routes: Routes[DosageHistoryRepository, Response] = Routes(
    Method.GET / "dosagehistory" -> handler { (request: Request) =>
      ZIO.logInfo("GET /dosagehistory")
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
      ZIO.logInfo("GET /dosagehistory/today")
      ZIO
        .serviceWithZIO[DosageHistoryRepository](_.getToday)
        .map(dosages => Response.json(dosages.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    }
  ) @@ cors(MedicateCorsConfig.allAllowed)
}
