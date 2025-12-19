package org.ergoplatform.ergoscript.eip5

import io.circe.syntax._
import io.circe.Printer

object JsonSerializer {

  private val printer = Printer.spaces2.copy(dropNullValues = false)

  def toJson(template: ContractTemplate): String = {
    printer.print(template.asJson)
  }

  def toJsonCompact(template: ContractTemplate): String = {
    template.asJson.noSpaces
  }
}
