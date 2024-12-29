package dev.gertjanassies.infra

import zio.http.*

object InfraApp {
  def routes: Routes[Any, Response] = Routes(
    // Heartbeat for ready probes
    Method.GET / "ready" -> handler {
      Response.status(Status.Ok)
    },
  )
}
