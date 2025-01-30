package dev.gertjanassies.medicate

import zio.*
import zio.redis.*
import zio.json.*
import java.time.LocalDate

class DosageHistoryRepository(redis: Redis, prefix: String) {
  def create(dosageHistory: DosageHistory): ZIO[Any, RedisError, Boolean] =
    redis.set(
      s"$prefix${dosageHistory.date}-${dosageHistory.time}",
      dosageHistory.toJson
    )

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
  } yield histories.sorted

  def getToday: Task[List[DosageHistory]] = for {
    histories <- getAll
    today <- ZIO.succeed(LocalDate.now().toString)
    todayHistories <- ZIO.succeed(histories.filter(_.date == today))
  } yield todayHistories.sorted
}

object DosageHistoryRepository {
  def layer(prefix: String): ZLayer[Redis, Nothing, DosageHistoryRepository] =
    ZLayer.fromFunction((redis: Redis) =>
      new DosageHistoryRepository(redis, prefix)
    )
}
