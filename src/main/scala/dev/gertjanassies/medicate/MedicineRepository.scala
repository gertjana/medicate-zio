package dev.gertjanassies.medicate

import zio.*
import zio.redis.*
import zio.json.*

class MedicineRepository(redis: Redis, prefix: String) {

  def create(medicine: Medicine): ZIO[Any, RedisError, Boolean] =
    redis.set(s"$prefix${medicine.id}", medicine.toJson)

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

  def update(id: String, medicine: Medicine): Task[Boolean] =
    redis.set(s"$prefix$id", medicine.toJson)

  def delete(id: String): Task[Unit] =
    redis.del(s"$prefix$id").unit
}

object MedicineRepository {
  def layer(prefix: String): ZLayer[Redis, Nothing, MedicineRepository] =
    ZLayer.fromFunction((redis: Redis) => new MedicineRepository(redis, prefix))
}
