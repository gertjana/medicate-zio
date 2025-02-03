package dev.gertjanassies.medicate

import zio.json._
import zio.schema._

final case class DosageHistory(
    id: String,
    date: String,
    time: String,
    medicineId: MedicineId,
    description: String,
    amount: Double
)

object DosageHistory {
  implicit val encoder: JsonEncoder[DosageHistory] =
    DeriveJsonEncoder.gen[DosageHistory]
  implicit val decoder: JsonDecoder[DosageHistory] =
    DeriveJsonDecoder.gen[DosageHistory]

  implicit val itemSchema: Schema[DosageHistory] =
    DeriveSchema.gen[DosageHistory]

  implicit val itemOrdering: Ordering[DosageHistory] =
    def intTime(s: String) = s.replaceAll(":", "").toInt
    Ordering.fromLessThan { (a, b) =>
      a.date.compareTo(b.date) > 0 ||
      (a.date.compareTo(b.date) == 0 && intTime(a.time) > intTime(b.time))
    }
}

case class ApiDosageHistory(
    date: String,
    time: String,
    medicineId: MedicineId,
    amount: Double
) {
  def toDosageHistory(id: String, description: String = ""): DosageHistory = DosageHistory(
    id = id,
    date = date,
    time = time,
    medicineId = medicineId,
    description = description,
    amount = amount
  )
}

object ApiDosageHistory {
  implicit val encoder: JsonEncoder[ApiDosageHistory] =
    DeriveJsonEncoder.gen[ApiDosageHistory]
  implicit val decoder: JsonDecoder[ApiDosageHistory] =
    DeriveJsonDecoder.gen[ApiDosageHistory]
}
