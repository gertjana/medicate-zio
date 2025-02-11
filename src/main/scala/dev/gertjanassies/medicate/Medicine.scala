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

  override def toString(): String = s"$name ($dose $unit)"

  def toApiMedicine: ApiMedicine = ApiMedicine(name, dose, unit, stock)

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

  implicit def orderingByName[A <: Medicine]: Ordering[A] =
    Ordering.by(medicine => medicine.name)
}

final case class ApiMedicine(
    name: String,
    dose: Double,
    unit: String,
    stock: Double
)

object ApiMedicine {
  implicit val encoder: JsonEncoder[ApiMedicine] =
    DeriveJsonEncoder.gen[ApiMedicine]
  implicit val decoder: JsonDecoder[ApiMedicine] =
    DeriveJsonDecoder.gen[ApiMedicine]
}
