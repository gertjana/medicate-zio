package dev.gertjanassies.medicate

import zio.json._
import zio.schema._

trait Medication {
  def addStock(amount: Int): Medication
  def takeDose(): Medication
}

type MedicineId = String

final case class Medicine(
    id: MedicineId,
    name: String,
    dose: Double,
    unit: String,
    amount: Option[Double] = None,
    stock: Double,
    daysLeft: Option[Int] = None
) extends Medication {
  import Medicine._

  def addStock(newStock: Int): Medicine =
    val resultingStock = this.stock + newStock
    this.copy(
      stock = resultingStock,
      daysLeft = calcDaysLeft(resultingStock, amount)
    )

  def takeDose(): Medicine =
    val resultingStock = this.stock // TODO - (dose * amount.getOrElse(0))
    this.copy(
      stock = resultingStock,
      daysLeft = calcDaysLeft(resultingStock, amount)
    )
}

object Medicine {
  implicit val encoder: JsonEncoder[Medicine] = DeriveJsonEncoder.gen[Medicine]
  implicit val decoder: JsonDecoder[Medicine] = DeriveJsonDecoder.gen[Medicine]

  implicit val itemSchema: Schema[Medicine] = DeriveSchema.gen[Medicine]

  implicit def orderingById[A <: Medicine]: Ordering[A] =
    Ordering.by(medicine => medicine.id)

  def calcDaysLeft(
      stock: Double,
      amount: Option[Double]
  ): Option[Int] =
    amount match {
      case None    => None
      case Some(0) => None
      case Some(v) => Some((stock / v).toInt)
    }

  def create(
      id: String,
      name: String,
      dose: Double,
      unit: String,
      amount: Option[Double],
      stock: Double
  ): Medicine =
    Medicine(id, name, dose, unit, amount, stock, calcDaysLeft(stock, amount))
}
