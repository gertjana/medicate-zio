package dev.gertjanassies

import medicate._
import zio.test._
import zio._

object TestMedicine extends ZIOSpecDefault {
  val med = Medicine(id = "test1", name = "Test", amount = 2, dose = 1.0, stock = 10)
  
  def spec = suite("Medicine Spec")(
    test("Medicine: takeDose/addStock/daysLeft") {
      for {
        dosed <- ZIO.succeed(med.takeDose())
        updated <- ZIO.succeed(dosed.addStock(10))
        daysLeft <- ZIO.succeed(updated.daysLeft())
      } yield assertTrue(daysLeft == 9)
    }
  )
}
