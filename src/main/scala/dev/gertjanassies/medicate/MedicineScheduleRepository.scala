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
      schedules <- if (keys.isEmpty) {
        ZIO.succeed(List.empty)
      } else {
        for {
          values <- redis.mGet(keys.head, keys.tail: _*).returning[String]
          schedules <- ZIO.succeed(
          values
            .map(_.flatMap(_.fromJson[MedicineSchedule].toOption))
            .filter(_.isDefined)
            .map(_.get)
          )
        } yield schedules.toList.sorted
      }
    } yield schedules

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

  def getSchedule(): ZIO[
    MedicineScheduleRepository
      with MedicineRepository
      with DosageHistoryRepository,
    Throwable,
    List[DailySchedule]
  ] = for {
    schedules <- getAll
    medicines <- ZIO.serviceWithZIO[MedicineRepository](_.getAll)
    dosageHistory <- ZIO.serviceWithZIO[DosageHistoryRepository](_.getToday)
    groupedSchedules = schedules.groupBy(_.time).map { case (time, schedules) =>
      (
        time,
        schedules.map(m => (medicines.find(_.id == m.medicineId), m.amount))
      )
    }
    dailySchedules <- ZIO.succeed(groupedSchedules.map { case (time, grouped) =>
      DailySchedule(
        time,
        grouped,
        dosageHistory.find(_.time == time).map(_ => true)
      )
    // DailySchedule(time, grouped)
    })
  } yield dailySchedules.toList.sorted

  def addtakendosages(time: String, date: String): ZIO[
    MedicineScheduleRepository
      with MedicineRepository
      with DosageHistoryRepository,
    RedisError,
    Boolean
  ] = {
    for {
      schedules <- getSchedule().mapError(_ =>
        RedisError.ProtocolError("Failed to get schedule")
      )
      schedule <- ZIO.succeed(schedules.find(_.time == time).get)
      dosageRepository <- ZIO.service[DosageHistoryRepository]
      medicines <- ZIO.foreach(schedule.medicines) { medicine =>
        {
          dosageRepository.create(
            DosageHistory(
              date = date,
              time = time,
              medicineId = medicine._1.get.id,
              amount = medicine._2
            )
          )
        }
      }
    } yield true
  }
}

object MedicineScheduleRepository {
  def layer(
      prefix: String
  ): ZLayer[Redis, Nothing, MedicineScheduleRepository] =
    ZLayer.fromFunction((redis: Redis) =>
      new MedicineScheduleRepository(redis, prefix)
    )
}
