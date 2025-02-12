package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*
import zio.http.Middleware.cors

import zio.Tag
import java.time.LocalDate

object MedicineScheduleApi {
  def routes: Routes[
    MedicineRepository & MedicineScheduleRepository & DosageHistoryRepository,
    Nothing
  ] = Routes(
    // create
    Method.POST / "schedules" -> handler { (request: Request) =>
      ZIO.logInfo("POST /schedules")
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
      ZIO.logInfo("GET /schedules")
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
        ZIO.logInfo(s"GET /schedules/$id")
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
        ZIO.logInfo(s"PUT /schedules/$id")
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
        ZIO.logInfo(s"DELETE /schedules/$id")
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
      ZIO.logInfo("GET /schedules/daily")
      ZIO
        .serviceWithZIO[MedicineScheduleRepository](_.getDailySchedule())
        .map(daily => Response.json(daily.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.GET / "schedules" / "past" -> handler {
      ZIO.logInfo("GET /schedules/past")
      ZIO
        .serviceWithZIO[MedicineScheduleRepository](
          _.getPastDailySchedules()
        )
        .map(daily => Response.json(daily.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.POST / "schedules" / "takedose" -> handler { (request: Request) =>
      ZIO.logInfo("POST /schedules/takedose")
      val today = LocalDate.now().toString
      request.queryParams("time").headOption match {
        case Some(time) =>
          val date = request.queryParams("date").headOption match {
            case Some(date) => date
            case None       => today
          }
          ZIO
            .serviceWithZIO[MedicineScheduleRepository] { repo =>
              for {
                _ <- repo.addtakendosages(time, date)
                schedule <-
                  if (date == today) repo.getDailySchedule().map(_.toJson)
                  else repo.getPastDailySchedules().map(_.toJson)
              } yield Response.json(schedule)
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
  ) @@ cors(MedicateCorsConfig.allAllowed)
}
