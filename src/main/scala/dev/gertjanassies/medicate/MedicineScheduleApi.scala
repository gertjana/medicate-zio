package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*
import zio.http.Middleware.cors

import java.time.LocalDate

object MedicineScheduleApi {
  private type Env =
    MedicineRepository & MedicineScheduleRepository & DosageHistoryRepository

  def routes: Routes[Env, Nothing] =
    (
      medicineRoutes ++
        scheduleRoutes ++
        scheduleActionRoutes ++
        dosageRoutes
    ) @@ cors(MedicateCorsConfig.allAllowed)

  private def medicineRoutes: Routes[Env, Nothing] = Routes(
    Method.POST / "medicines" -> handler { (request: Request) =>
      ZIO.logInfo("POST /medicines called")
      request.body.asString
        .map(_.fromJson[ApiMedicine])
        .flatMap {
          case Left(error) =>
            ZIO.succeed(Response.text(error).status(Status.BadRequest))
          case Right(medicine) =>
            ZIO.serviceWithZIO[MedicineRepository] { repo =>
              for {
                result <- repo.create(medicine)
                created <- repo.getById(result)
              } yield Response.json(created.toJson).status(Status.Created)
            }
        }
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.GET / "medicines" -> handler {
      ZIO.logInfo("GET /medicines called")
      ZIO
        .serviceWithZIO[MedicineRepository](_.getAll)
        .map(meds => Response.json(meds.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    },
    Method.GET / "medicines" / string("id") -> handler {
      (id: String, request: Request) =>
        ZIO.logInfo(s"GET /medicines/$id")
        ZIO
          .serviceWithZIO[MedicineRepository](_.getById(id))
          .map(optMed =>
            optMed match {
              case Some(medicine) => Response.json(medicine.toJson)
              case None           => Response.status(Status.NotFound)
            }
          )
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    Method.PUT / "medicines" / string("id") -> handler {
      (id: String, request: Request) =>
        ZIO.logInfo(s"PUT /medicines/$id")
        request.body.asString
          .map(_.fromJson[ApiMedicine])
          .flatMap {
            case Left(error) =>
              ZIO.succeed(Response.text(error).status(Status.BadRequest))
            case Right(medicine) =>
              ZIO.serviceWithZIO[MedicineRepository] { repo =>
                repo.getById(id).flatMap {
                  case Some(_) =>
                    repo.update(id, medicine) *>
                      repo
                        .getById(id)
                        .map(medicine => Response.json(medicine.toJson))
                  case None => ZIO.succeed(Response.status(Status.NotFound))
                }
              }
          }
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    Method.DELETE / "medicines" / string("id") -> handler {
      (id: String, request: Request) =>
        ZIO.logInfo(s"DELETE /medicines/$id")
        ZIO
          .serviceWithZIO[MedicineRepository](repo => {
            repo.getById(id).flatMap {
              case Some(_) =>
                repo.delete(id) *> ZIO
                  .succeed(Response.status(Status.NoContent))
              case None =>
                ZIO.succeed(Response.status(Status.NotFound))
            }
          })
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    },
    Method.POST / "medicines" / string("id") / "addStock" -> handler {
      (id: String, request: Request) =>
        ZIO.logInfo(s"POST /medicines/$id/addStock")
        request.queryParam("amount") match {
          case Some(amount) =>
            ZIO
              .serviceWithZIO[MedicineRepository](repo =>
                repo
                  .getById(id)
                  .flatMap {
                    case Some(medicine) =>
                      val updatedMedicine = medicine.addStock(amount.toInt)
                      repo.update(id, updatedMedicine.toApiMedicine) *>
                        ZIO.succeed(Response.json(updatedMedicine.toJson))
                    case None =>
                      ZIO.succeed(Response.status(Status.NotFound))
                  }
              )
              .catchAll(error =>
                ZIO.succeed(
                  Response
                    .text(error.getMessage)
                    .status(Status.InternalServerError)
                )
              )
          case None =>
            ZIO.succeed(
              Response
                .text("missing queryParam 'amount'")
                .status(Status.BadRequest)
            )
        }
    }
  )

  private def dosageRoutes: Routes[Env, Nothing] = Routes(
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
  )

  private def scheduleRoutes: Routes[Env, Nothing] = Routes(
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
    }
  )

  private def scheduleActionRoutes: Routes[Env, Nothing] = Routes(
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
    },
    Method.GET / "schedules" / "daysleft" -> handler {
      ZIO.logInfo("GET /schedules/daysleft")
      ZIO
        .serviceWithZIO[MedicineScheduleRepository](_.calculateDaysLeft())
        .map(daysLeft => Response.json(daysLeft.toJson))
        .catchAll(error =>
          ZIO.succeed(
            Response.text(error.getMessage).status(Status.InternalServerError)
          )
        )
    }
  )
}
