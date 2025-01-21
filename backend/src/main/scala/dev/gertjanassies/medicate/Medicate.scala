package dev.gertjanassies.medicate

import zio.json._
import zio.schema._

trait Medication {
  def addStock(amount: Int): Medication
  def takeDose(): Medication
}

trait MedicationObject {
  def create(
      id: String,
      name: String,
      amount: Double,
      dose: Double,
      stock: Double
  ): Medication
}

final case class Medicine(
    id: String,
    name: String,
    amount: Double,
    dose: Double,
    stock: Double,
    daysLeft: Option[Int] = None
) extends Medication {
  import Medicine._

  def addStock(newStock: Int): Medicine =
    val resultingStock = this.stock + newStock
    this.copy(
      stock = resultingStock,
      daysLeft = calcDaysLeft(resultingStock, amount, dose)
    )

  def takeDose(): Medicine =
    val resultingStock = this.stock - dose * amount
    this.copy(
      stock = resultingStock,
      daysLeft = calcDaysLeft(resultingStock, amount, dose)
    )
}

object Medicine extends MedicationObject {
  implicit val encoder: JsonEncoder[Medicine] = DeriveJsonEncoder.gen[Medicine]
  implicit val decoder: JsonDecoder[Medicine] = DeriveJsonDecoder.gen[Medicine]

  implicit val itemSchema: Schema[Medicine] = DeriveSchema.gen[Medicine]

  private def calcDaysLeft(
      stock: Double,
      amount: Double,
      dose: Double
  ): Option[Int] =
    if amount == 0 || dose == 0 then None
    else Some((stock / (dose * amount)).toInt)

  def create(
      id: String,
      name: String,
      amount: Double,
      dose: Double,
      stock: Double
  ): Medicine =
    Medicine(id, name, amount, dose, stock, calcDaysLeft(stock, amount, dose))
}
