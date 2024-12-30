package dev.gertjanassies.medicate

import zio.*
import zio.redis.*
import zio.json.*

class MedicineRepository(redis: Redis) {
  private val prefix = "medicine:"

  def create(medicine: Medicine): Task[Boolean] =
    redis.set(s"$prefix${medicine.id}", medicine.toJson)

  def getAll: Task[List[Medicine]] = for {
    keys   <- redis.keys(s"$prefix*").returning[String]
    values <- redis.mGet(keys.toSeq).returning[Set[String]]
    medicines <- ZIO.succeed(
      values.flatten.flatten.flatMap(_.fromJson[Medicine].toOption)
    )
  } yield medicines.toList

  def getById(id: String): Task[Option[Medicine]] =
    redis
      .get(s"$prefix$id")
      .returning[String]
      .map(_.flatMap(_.fromJson[Medicine].toOption))

  def update(id: String, medicine: Medicine): Task[Boolean] =
    redis.set(s"$prefix$id", medicine.toJson)

  def delete(id: String): Task[Unit] =
    redis.del(s"$prefix$id").unit
}

object MedicineRepository {
  val layer: ZLayer[Redis, Nothing, MedicineRepository] =
    ZLayer.fromFunction(new MedicineRepository(_))
}
