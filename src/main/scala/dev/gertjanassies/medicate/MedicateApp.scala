package dev.gertjanassies.medicate

import zio.*
import zio.http.*
import zio.json.*

object MedicateApp {
  def routes: Routes[MedicineRepository, Response] = Routes(
    // Create
    Method.POST / "medicines" -> handler { (request: Request) =>
      request.body.asString
        .map(_.fromJson[Medicine])
        .flatMap {
          case Left(error) =>
            ZIO.succeed(Response.text(error).status(Status.BadRequest))
          case Right(medicine) =>
            ZIO.serviceWithZIO[MedicineRepository] { repo =>
              for {
                result <- repo.create(medicine)
              } yield Response.json(medicine.toJson).status(Status.Created)
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
      ZIO
        .serviceWithZIO[MedicineRepository](_.getAll)
        // .map(Medicine.calculateDaysLeftFor(_))
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
        request.body.asString
          .map(_.fromJson[Medicine])
          .flatMap {
            case Left(error) =>
              ZIO.succeed(Response.text(error).status(Status.BadRequest))
            case Right(medicine) =>
              ZIO.serviceWithZIO[MedicineRepository] { repo =>
                repo.getById(id).flatMap {
                  case Some(_) =>
                    println("found id, updating...")
                    repo.update(id, medicine) *>
                      ZIO.succeed(Response.json(medicine.toJson))
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
        request.queryParam("amount") match {
          case Some(amount) =>
            ZIO
              .serviceWithZIO[MedicineRepository](repo =>
                repo
                  .getById(id)
                  .flatMap {
                    case Some(medicine) =>
                      var updatedMedicine = medicine.addStock(amount.toInt)
                      repo.update(id, updatedMedicine) *>
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
    },
    Method.POST / "medicines" / string("id") / "takeDose" -> handler {
      (id: String, request: Request) =>
        ZIO
          .serviceWithZIO[MedicineRepository] { repo =>
            repo
              .getById(id)
              .flatMap {
                case Some(medicine) =>
                  var updatedMedicine = medicine.takeDose()
                  repo.update(id, updatedMedicine) *>
                    ZIO.succeed(Response.json(updatedMedicine.toJson))
                case None => ZIO.succeed(Response.status(Status.NotFound))
              }

          }
          .catchAll(error =>
            ZIO.succeed(
              Response.text(error.getMessage).status(Status.InternalServerError)
            )
          )
    }
  ) // routes
}
