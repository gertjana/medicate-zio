package dev.gertjanassies.medicate

import zio._
import zio.redis._
import zio.json._

class MedicineScheduleRepository(redis: Redis, prefix: String) {

  def create(schedule: ApiMedicineSchedule): ZIO[Any, RedisError, String] =
    for {
      id <- ZIO.succeed(java.util.UUID.randomUUID().toString)
      ms <- ZIO.succeed(MedicineSchedule(id, schedule.time, schedule.medicineId, "", schedule.amount))
      _ <- redis.set(s"$prefix$id", ms.toJson)
    } yield id


  def getAll: ZIO[MedicineRepository, Throwable, List[MedicineSchedule]] = for {
    keys <- redis
      .keys(s"$prefix*").returning[String]
    medicines <- ZIO.service[MedicineRepository].flatMap(_.getAll)
    schedules <-
      if (keys.isEmpty) {
        ZIO.succeed(List.empty)
      } else {
        for {
          values <- redis.mGet(keys.head, keys.tail: _*).returning[String]
          schedules <- ZIO.succeed(
            values
              .map(_.flatMap(_.fromJson[MedicineSchedule].toOption))
              .filter(_.isDefined)
              .map(_.get)
              .map(m => m.copy(description = medicines.find(_.id == m.medicineId).get.toString()))
          )
        } yield schedules.toList.sorted
      }
  } yield schedules

  def getById(id: ScheduleId): Task[Option[MedicineSchedule]] =
    redis
      .get(s"$prefix$id")
      .returning[String]
      .map(_.flatMap(_.fromJson[MedicineSchedule].toOption))

  def update(id: ScheduleId, schedule: ApiMedicineSchedule): Task[Boolean] =
    var updated = MedicineSchedule(id, schedule.time, schedule.medicineId, "", schedule.amount)
    redis.set(s"$prefix$id", updated.toJson)

  def delete(id: ScheduleId): Task[Unit] =
    redis.del(s"$prefix$id").unit

  def getDailySchedule(): ZIO[
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
        time = time,
        medicines = grouped,
        taken = dosageHistory.find(_.time == time).map(_ => true)
      )
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
      schedules <- getDailySchedule().mapError(_ =>
        RedisError.ProtocolError("Failed to get schedule")
      )
      schedule <- ZIO.succeed(schedules.find(_.time == time).get)
      dosageRepository <- ZIO.service[DosageHistoryRepository]
      medicines <- ZIO.foreach(schedule.medicines) { medicine =>
        {
          dosageRepository.create(
            DosageHistory(
              id = "",
              date = date,
              time = time,
              medicineId = medicine._1.get.id,
              description = medicine._1.get.toString(),
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
