package dev.gertjanassies.medicate

import zio.schema._
import zio.json._

type ScheduleId = String

final case class MedicineSchedule(
    id: String,
    time: String,
    medicineId: MedicineId,
    description: String,
    amount: Double
) {}

object MedicineSchedule {
  implicit val encoder: JsonEncoder[MedicineSchedule] =
    DeriveJsonEncoder.gen[MedicineSchedule]
  implicit val decoder: JsonDecoder[MedicineSchedule] =
    DeriveJsonDecoder.gen[MedicineSchedule]

  implicit val itemSchema: Schema[MedicineSchedule] =
    DeriveSchema.gen[MedicineSchedule]

  implicit def orderingByTime[A <: MedicineSchedule]: Ordering[A] =
    Ordering.by(schedule => schedule.time.replaceAll(":", "").toInt)

}

final case class ApiMedicineSchedule(
    time: String,
    medicineId: MedicineId,
    amount: Double
) {}

object ApiMedicineSchedule {
  implicit val encoder: JsonEncoder[ApiMedicineSchedule] =
    DeriveJsonEncoder.gen[ApiMedicineSchedule]
  implicit val decoder: JsonDecoder[ApiMedicineSchedule] =
    DeriveJsonDecoder.gen[ApiMedicineSchedule]
}

final case class DailySchedule(
    time: String,
    medicines: List[(Option[Medicine], Double)],
    taken: Option[Boolean] = None
) {}

object DailySchedule {
  implicit val encoder: JsonEncoder[DailySchedule] =
    DeriveJsonEncoder.gen[DailySchedule]
  implicit val decoder: JsonDecoder[DailySchedule] =
    DeriveJsonDecoder.gen[DailySchedule]

  implicit val itemSchema: Schema[DailySchedule] =
    DeriveSchema.gen[DailySchedule]

  implicit def orderingById[A <: DailySchedule]: Ordering[A] =
    Ordering.by(schedule => schedule.time)
}

final case class DailyScheduleWithDate(
    date: String,
    schedules: List[DailySchedule]
) {}

object DailyScheduleWithDate {
  implicit val encoder: JsonEncoder[DailyScheduleWithDate] =
    DeriveJsonEncoder.gen[DailyScheduleWithDate]
  implicit val decoder: JsonDecoder[DailyScheduleWithDate] =
    DeriveJsonDecoder.gen[DailyScheduleWithDate]

  implicit val itemSchema: Schema[DailyScheduleWithDate] =
    DeriveSchema.gen[DailyScheduleWithDate]

  implicit def orderingById[A <: DailyScheduleWithDate]: Ordering[A] =
    Ordering.by(schedule => schedule.date)
}
