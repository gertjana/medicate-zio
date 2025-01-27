package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Header.AccessControlAllowOrigin

import zio.Tag

object MedicineScheduleApi {
  val config: CorsConfig = // remove in production
    CorsConfig(
      allowedOrigin = {
        case origin if origin.renderedValue.contains("localhost") =>
          Some(AccessControlAllowOrigin.Specific(origin))
        case _ => None
      }
    )
  def routes: Routes[MedicineRepository & MedicineScheduleRepository, Nothing] =
    Routes(
      // create
      Method.POST / "schedules" -> handler { (request: Request) =>
        request.body.asString
          .map(_.fromJson[MedicineSchedule])
          .flatMap {
            case Left(error: String) =>
              ZIO.succeed(Response.text(error).status(Status.BadRequest))
            case Right(schedule: MedicineSchedule) =>
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
        println("GET /schedules")
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
                Response
                  .text(error.getMessage)
                  .status(Status.InternalServerError)
              )
            )
      },
      // Update
      Method.PUT / "schedules" / string("id") -> handler {
        (id: ScheduleId, request: Request) =>
          request.body.asString
            .map(_.fromJson[MedicineSchedule])
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
                Response
                  .text(error.getMessage)
                  .status(Status.InternalServerError)
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
                Response
                  .text(error.getMessage)
                  .status(Status.InternalServerError)
              )
            )
      },
      // Combined
      Method.GET / "schedules" / "combined" -> handler {
        ZIO
          .serviceWithZIO[MedicineScheduleRepository](_.getSchedule())
          .map(combined => Response.json(combined.toJson))
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
      }
    ) @@ cors(config)
}
