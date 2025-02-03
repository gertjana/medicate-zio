package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Header.AccessControlAllowOrigin

import zio.Tag
import java.time.LocalDate

object MedicineScheduleApi {
  val config: CorsConfig = // remove in production
    CorsConfig(
      allowedOrigin = {
        case origin if origin.renderedValue.contains("localhost") =>
          Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      }
    )
  def routes: Routes[
    MedicineRepository & MedicineScheduleRepository & DosageHistoryRepository,
    Nothing
  ] = Routes(
    // create
    Method.POST / "schedules" -> handler { (request: Request) =>
      request.body.asString
        .map(_.fromJson[ApiMedicineSchedule])
        .flatMap {
          case Left(error: String) =>
            ZIO.succeed(Response.text(error).status(Status.BadRequest))
          case Right(schedule: ApiMedicineSchedule) =>
            ZIO.serviceWithZIO[MedicineScheduleRepository] { repo =>
              for {
                result <- repo.create(schedule)
              } yield Response.json(schedule.toJson).status(Status.Created)
            }
        }
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    // Read (all)
    Method.GET / "schedules" -> handler {
      ZIO
        .serviceWithZIO[MedicineScheduleRepository](_.getAll)
        .map(schedules => Response.json(schedules.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    // Read (single)
    Method.GET / "schedules" / string("id") -> handler {
      (id: String, request: Request) =>
        ZIO
          .serviceWithZIO[MedicineScheduleRepository](_.getById(id))
          .map(optSchedule =>
            optSchedule match {
              case Some(schedule) => Response.json(schedule.toJson)
              case None           => Response.status(Status.NotFound)
            }
          )
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    // Update
    Method.PUT / "schedules" / string("id") -> handler {
      (id: ScheduleId, request: Request) =>
        request.body.asString
          .map(_.fromJson[ApiMedicineSchedule])
          .flatMap {
            case Left(error) =>
              ZIO.succeed(Response.text(error).status(Status.BadRequest))
            case Right(schedule) =>
              ZIO.serviceWithZIO[MedicineScheduleRepository] { repo =>
                for {
                  result <- repo.update(id, schedule)
                } yield Response.json(schedule.toJson)
              }
          }
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    // Delete
    Method.DELETE / "schedules" / string("id") -> handler {
      (id: ScheduleId, request: Request) =>
        ZIO
          .serviceWithZIO[MedicineScheduleRepository] { repo =>
            for {
              result <- repo.delete(id)
            } yield Response.status(Status.NoContent)
          }
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    // Daily Schedule
    Method.GET / "schedules" / "daily" -> handler {
      ZIO
        .serviceWithZIO[MedicineScheduleRepository](_.getDailySchedule())
        .map(daily => Response.json(daily.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.POST / "schedules" / "takedose" -> handler { (request: Request) =>
      request.queryParams("time").headOption match {
        case Some(time) =>
          val date = LocalDate.now().toString
          ZIO
            .serviceWithZIO[MedicineScheduleRepository] { repo =>
              for {
                _ <- repo.addtakendosages(time, date)
                daily <- repo.getDailySchedule()
              } yield Response.json(daily.toJson)
            }
            .catchAll(error =>
              ZIO.succeed(
                Response
                  .text(error.getMessage)
                  .status(Status.InternalServerError)
              )
            )
        case None =>
          ZIO.succeed(
            Response.text("Time is required").status(Status.BadRequest)
          )
      }
    }
  ) @@ cors(config)
}
