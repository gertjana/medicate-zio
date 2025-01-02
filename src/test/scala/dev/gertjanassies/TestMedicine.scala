package dev.gertjanassies

import medicate._
import zio.test._
import zio._
import zio.json.{DecoderOps, EncoderOps}

object TestMedicine extends ZIOSpecDefault {
  val med =
    Medicine.create(
      id = "test1",
      name = "Test",
      amount = 2.0,
      dose = 1.0,
      stock = 10
    )
  val med2 =
    Medicine.create(
      id = "test2",
      name = "Test2",
      amount = 1.0,
      dose = 3.0,
      stock = 10
    )

  def spec = suite("Medicine Spec should")(
    test("calculate takeDose/addStock/daysLeft correctly") {
      for {
        dosed    <- ZIO.succeed(med.takeDose())
        updated  <- ZIO.succeed(dosed.addStock(10))
        daysLeft <- ZIO.succeed(updated.daysLeft)
      } yield assertTrue(daysLeft == Some(9))
    },
    test("calculate daysLeft for multiple medicines correctly") {
      assertTrue(med.daysLeft == Some(5)) &&
      assertTrue(med2.daysLeft == Some(3))
    },
    test("calculate daysLeft correctly when using the create constructor") {
      val m = Medicine.create(
        id = "test3",
        name = "Test3",
        amount = 1.0,
        dose = 1.0,
        stock = 10
      )
      assertTrue(m.daysLeft == Some(10))
    },
    test("encode and decode correctly") {
      val encoded = med.toJson
      val decoded = encoded.fromJson[Medicine].toOption
      assertTrue(decoded == Some(med))
    },
    test("handle division by zero when calculating daysLeft") {
      val m = Medicine.create(
        id = "test4",
        name = "Test4",
        amount = 0.0,
        dose = 0.0,
        stock = 10
      )
      assertTrue(m.daysLeft == None)
    }
  )
}
