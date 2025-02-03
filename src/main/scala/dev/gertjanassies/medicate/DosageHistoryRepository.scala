package dev.gertjanassies.medicate

import zio.*
import zio.redis.*
import zio.json.*
import java.time.LocalDate

class DosageHistoryRepository(redis: Redis, prefix: String) {
  def create(
      dosageHistory: ApiDosageHistory,
      description: String = ""
  ): ZIO[Any, RedisError, String] = {
    for {
      id <- ZIO.succeed(java.util.UUID.randomUUID().toString)
      dh <- ZIO.succeed(dosageHistory.toDosageHistory(id, description))
      _ <- redis.set(s"$prefix${id}", dh.toJson)
    } yield (id)
  }

  def getAll: Task[List[DosageHistory]] = for {
    keys <- redis.keys(s"$prefix*").returning[String]
    histories <-
      if (keys.isEmpty) ZIO.succeed(List.empty[DosageHistory])
      else
        redis
          .mGet(keys.head, keys.tail: _*)
          .returning[String]
          .map(values =>
            values
              .map(_.flatMap(_.fromJson[DosageHistory].toOption))
              .filter(_.isDefined)
              .map(_.get)
              .toList
          )
    sortedHistories <- ZIO.succeed(histories.sorted)
  } yield sortedHistories

  def getToday: Task[List[DosageHistory]] = for {
    histories <- getAll
    today <- ZIO.succeed(LocalDate.now().toString)
    todayHistories <- ZIO.succeed(histories.filter(_.date == today))
    sortedTodayHistories <- ZIO.succeed(todayHistories.sorted)
  } yield sortedTodayHistories
}

object DosageHistoryRepository {
  def layer(prefix: String): ZLayer[Redis, Nothing, DosageHistoryRepository] =
    ZLayer.fromFunction((redis: Redis) =>
      new DosageHistoryRepository(redis, prefix)
    )
}
