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
  implicit val encoder: JsonEncoder[MedicineSchedule] = DeriveJsonEncoder.gen[MedicineSchedule]
  implicit val decoder: JsonDecoder[MedicineSchedule] = DeriveJsonDecoder.gen[MedicineSchedule]

  implicit val itemSchema: Schema[MedicineSchedule] = DeriveSchema.gen[MedicineSchedule]

  implicit def orderingById[A <: MedicineSchedule]: Ordering[A] =
    Ordering.by(schedule => schedule.id)

  def create(id: String, time: String, medicineId: MedicineId, amount: Double): MedicineSchedule =
    MedicineSchedule(id, time, medicineId, amount)
}

final case class CombinedSchedule(
    time: String,
    medicines: List[(Option[Medicine], Double)]
) {}

object CombinedSchedule {
  implicit val encoder: JsonEncoder[CombinedSchedule] = DeriveJsonEncoder.gen[CombinedSchedule]
  implicit val decoder: JsonDecoder[CombinedSchedule] = DeriveJsonDecoder.gen[CombinedSchedule]

  implicit val itemSchema: Schema[CombinedSchedule] = DeriveSchema.gen[CombinedSchedule]

  implicit def orderingById[A <: CombinedSchedule]: Ordering[A] =
    Ordering.by(schedule => schedule.time)
}