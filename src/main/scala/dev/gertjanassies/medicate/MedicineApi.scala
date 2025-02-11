package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*
import zio.http.Middleware.cors

object MedicineApi {

  def routes: Routes[MedicineRepository, Response] = Routes(
    // Create
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

    // Read (all)
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

    // Read (single)
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

    // Update
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

    // Delete
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

    // add Stock
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
                      var updatedMedicine = medicine.addStock(amount.toInt)
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
  ) @@ cors(MedicateCorsConfig.allAllowed) // routes
}
