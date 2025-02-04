package dev.gertjanassies.medicate

import zio._
import zio.redis._
import zio.json._

class MedicineRepository(redis: Redis, prefix: String) {

  def create(apiMedicine: ApiMedicine): Task[String] = for {
    id <- ZIO.succeed(java.util.UUID.randomUUID.toString())
    medicine <- ZIO.succeed(Medicine(id, apiMedicine.name, apiMedicine.dose, apiMedicine.unit, apiMedicine.stock))
    _ <- redis.set(s"$prefix$id", medicine.toJson)
  } yield id

  def getAll: Task[List[Medicine]] = for {
    keys <- redis
      .keys(s"$prefix*") // keys is blocking, replace with scan
      .returning[String]
    values <-
      if (keys.isEmpty) ZIO.succeed(List.empty)
      else redis.mGet(keys.head, keys.tail: _*).returning[String]
    medicines <- ZIO.succeed(
      values
        .map(_.flatMap(_.fromJson[Medicine].toOption))
        .filter(_.isDefined)
        .map(_.get)
    )
  } yield medicines.toList.sorted

  def getById(id: String): Task[Option[Medicine]] =
    redis
      .get(s"$prefix$id")
      .returning[String]
      .map(_.flatMap(_.fromJson[Medicine].toOption))

  def update(id: String, medicine: ApiMedicine): Task[Boolean] =
    for {
      to_update <- ZIO.succeed(Medicine(id, medicine.name, medicine.dose, medicine.unit, medicine.stock))
      result <- redis.set(s"$prefix$id", to_update.toJson)
    } yield result

  def delete(id: String): Task[Unit] =
    redis.del(s"$prefix$id").unit

  def reduceStock(id: String, amount: Double): Task[Boolean] =
    for {
      maybeMedicine <- getById(id)
      result <- maybeMedicine match {
        case Some(medicine) =>
          val newStock = medicine.stock - amount
          if (newStock < 0) ZIO.succeed(false)
          else update(id, medicine.toApiMedicine.copy(stock = newStock))
        case None => ZIO.succeed(false)
      }
    } yield result

}

object MedicineRepository {
  def layer(prefix: String): ZLayer[Redis, Nothing, MedicineRepository] =
    ZLayer.fromFunction((redis: Redis) => new MedicineRepository(redis, prefix))
}
