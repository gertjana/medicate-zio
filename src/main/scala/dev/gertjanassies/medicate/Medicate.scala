package dev.gertjanassies.medicate

import zio.json.*

trait Medication:
  def addStock(amount: Int): Medication
  def takeDose(): Medication
  def daysLeft(): Int

trait MedicationObject:
  def calculateDaysLeftFor(medicines: List[Medicine]): List[(Medicine, Int)]


final case class Medicine(
    id: String,
    name: String,
    amount: Int,
    dose: Double,
    stock: Double
) extends Medication:

  def addStock(newStock: Int): Medicine =
    this.copy(stock = stock + newStock)

  def takeDose(): Medicine =
    this.copy(stock = stock - dose)

  def daysLeft(): Int =
    (stock / dose).toInt


object Medicine extends MedicationObject {
  implicit val encoder: JsonEncoder[Medicine] = DeriveJsonEncoder.gen[Medicine]
  implicit val decoder: JsonDecoder[Medicine] = DeriveJsonDecoder.gen[Medicine]
  def calculateDaysLeftFor(medicines: List[Medicine]): List[(Medicine, Int)] = {
    medicines.map(medicine => (medicine.copy(), medicine.daysLeft()))
  }
}
