package dev.gertjanassies.medicate

import zio.schema._
import zio.json._

type ScheduleId = String

final case class MedicineSchedule(
    id: String,
    time: String,
    medicineId: MedicineId,
    amount: Double
) {}

object MedicineSchedule {
  implicit val encoder: JsonEncoder[MedicineSchedule] =
    DeriveJsonEncoder.gen[MedicineSchedule]
  implicit val decoder: JsonDecoder[MedicineSchedule] =
    DeriveJsonDecoder.gen[MedicineSchedule]

  implicit val itemSchema: Schema[MedicineSchedule] =
    DeriveSchema.gen[MedicineSchedule]

  implicit def orderingById[A <: MedicineSchedule]: Ordering[A] =
    Ordering.by(schedule => schedule.id)

  def create(
      id: String,
      time: String,
      medicineId: MedicineId,
      amount: Double
  ): MedicineSchedule =
    MedicineSchedule(id, time, medicineId, amount)
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
