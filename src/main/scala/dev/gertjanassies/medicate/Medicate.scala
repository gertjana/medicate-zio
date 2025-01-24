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
      dose: Double,
      unit: String,
      amount: Double,
      stock: Double
  ): Medication
}

final case class Medicine(
    id: String,
    name: String,
    dose: Double,
    unit: String,
    amount: Double,
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
    val resultingStock = this.stock - dose * amount
    this.copy(
      stock = resultingStock,
      daysLeft = calcDaysLeft(resultingStock, amount)
    )
}

object Medicine extends MedicationObject {
  implicit val encoder: JsonEncoder[Medicine] = DeriveJsonEncoder.gen[Medicine]
  implicit val decoder: JsonDecoder[Medicine] = DeriveJsonDecoder.gen[Medicine]

  implicit val itemSchema: Schema[Medicine] = DeriveSchema.gen[Medicine]

  def calcDaysLeft(
      stock: Double,
      amount: Double
  ): Option[Int] =
    if amount == 0  then None
    else Some((stock / amount).toInt)

  def create( 
      id: String,
      name: String,
      dose: Double,
      unit: String,
      amount: Double,
      stock: Double
  ): Medicine =
    Medicine(id, name, dose, unit, amount, stock, calcDaysLeft(stock, amount))
}
