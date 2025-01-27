package dev.gertjanassies.medicate

import zio._
import zio.redis._
import zio.json._

class MedicineScheduleRepository(redis: Redis, prefix: String) {

  def create(schedule: MedicineSchedule): ZIO[Any, RedisError, Boolean] =
    redis.set(s"$prefix${schedule.id}", schedule.toJson)

  def getAll: Task[List[MedicineSchedule]] = for {
    keys <- redis
      .keys(s"$prefix*") // keys is blocking, replace with scan
      .returning[String]
    values <- redis.mGet(keys.head, keys.tail: _*).returning[String]
    schedules <- ZIO.succeed(
      values
        .map(_.flatMap(_.fromJson[MedicineSchedule].toOption))
        .filter(_.isDefined)
        .map(_.get)
    )
    _ <- ZIO.succeed(println(schedules))
  } yield schedules.toList.sorted

  def getById(id: ScheduleId): Task[Option[MedicineSchedule]] =
    redis
      .get(s"$prefix$id")
      .returning[String]
      .map(_.flatMap(_.fromJson[MedicineSchedule].toOption))

  def update(id: ScheduleId, schedule: MedicineSchedule): Task[Boolean] =
    var updated = schedule.copy(id = id)
    redis.set(s"$prefix$id", updated.toJson)

  def delete(id: ScheduleId): Task[Unit] =
    redis.del(s"$prefix$id").unit

  def getSchedule()
      : ZIO[MedicineScheduleRepository with MedicineRepository, Throwable, List[
        CombinedSchedule
      ]] = for {
    schedules <- getAll
    medicines <- ZIO.serviceWithZIO[MedicineRepository](_.getAll)
    groupedSchedules = schedules.groupBy(_.time).map { case (time, schedules) =>
      (
        time,
        schedules.map(m => (medicines.find(_.id == m.medicineId), m.amount))
      )
    }
    combinedSchedules <- ZIO.succeed(groupedSchedules.map {
      case (time, grouped) => CombinedSchedule(time, grouped)
    })
  } yield combinedSchedules.toList.sorted
}

object MedicineScheduleRepository {
  def layer(
      prefix: String
  ): ZLayer[Redis, Nothing, MedicineScheduleRepository] =
    ZLayer.fromFunction((redis: Redis) =>
      new MedicineScheduleRepository(redis, prefix)
    )
}
