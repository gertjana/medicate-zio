package dev.gertjanassies

import medicate._
import zio.test._
import zio._
import zio.json.{DecoderOps, EncoderOps}

object TestMedicine extends ZIOSpecDefault {
  val med =
    Medicine(
      id = "test1",
      name = "Test",
      dose = 1.0,
      unit = "mg",
      stock = 10
    )

  def spec = suite("Medicine Spec should")(
    test("calculate takeDose/addStock/daysLeft correctly") {
      for {
        updated <- ZIO.succeed(med.addStock(10))
      } yield assertTrue(updated.stock == 20)
    },
    test("encode and decode correctly") {
      val encoded = med.toJson
      val decoded = encoded.fromJson[Medicine].toOption
      assertTrue(decoded == Some(med))
    }
  )
}
