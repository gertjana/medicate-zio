package dev.gertjanassies.medicate

import zio.json._
import zio.schema._

trait Medication {
  def addStock(amount: Int): Medication
}

type MedicineId = String

final case class Medicine(
    id: MedicineId,
    name: String,
    dose: Double,
    unit: String,
    stock: Double
) extends Medication {
  import Medicine._

  def addStock(newStock: Int): Medicine =
    val resultingStock = this.stock + newStock
    this.copy(
      stock = resultingStock
    )
}

object Medicine {
  implicit val encoder: JsonEncoder[Medicine] = DeriveJsonEncoder.gen[Medicine]
  implicit val decoder: JsonDecoder[Medicine] = DeriveJsonDecoder.gen[Medicine]

  implicit val itemSchema: Schema[Medicine] = DeriveSchema.gen[Medicine]

  implicit def orderingById[A <: Medicine]: Ordering[A] =
    Ordering.by(medicine => medicine.id)

  def create(
      id: String,
      name: String,
      dose: Double,
      unit: String,
      stock: Double
  ): Medicine =
    Medicine(id, name, dose, unit, stock)
}
