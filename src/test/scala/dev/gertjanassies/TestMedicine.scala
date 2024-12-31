package dev.gertjanassies

import medicate._
import zio.test._
import zio._

object TestMedicine extends ZIOSpecDefault {
  val med = Medicine(id = "test1", name = "Test", amount = 2.0, dose = 1.0, stock = 10)
  val med2 = Medicine(id = "test2", name = "Test2", amount = 1.0, dose = 3.0, stock = 10)
  
  def spec = suite("Medicine Spec should")(
    test("calculate takeDose/addStock/daysLeft correctly") {
      for {
        dosed <- ZIO.succeed(med.takeDose())
        updated <- ZIO.succeed(dosed.addStock(10))
        daysLeft <- ZIO.succeed(updated.daysLeft())
      } yield assertTrue(daysLeft == 9)
    },
    test("calculate daysLeft for multiple medicines correctly") {
      for {
        daysLeft <- ZIO.succeed(Medicine.calculateDaysLeftFor(List(med, med2)))
      } yield assertTrue(daysLeft == List((med, 5), (med2, 3)))
    },
  )
}
