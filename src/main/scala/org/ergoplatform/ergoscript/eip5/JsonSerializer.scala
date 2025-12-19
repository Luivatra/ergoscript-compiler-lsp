package org.ergoplatform.ergoscript.eip5

import io.circe.{Encoder, Json}
import io.circe.syntax._
import io.circe.Printer
import org.ergoplatform.sdk.{ContractTemplate, Parameter}
import sigma.ast.SType
import sigma.serialization.{SigmaSerializer, TypeSerializer, DataSerializer}
import scorex.util.encode.Base16

object JsonSerializer {

  private val printer = Printer.spaces2.copy(dropNullValues = false)

  /** Encoder for SDK ContractTemplate to JSON following EIP-5 format */
  implicit val contractTemplateEncoder: Encoder[ContractTemplate] =
    new Encoder[ContractTemplate] {
      final def apply(template: ContractTemplate): Json = {
        // Serialize types to hex strings
        val constTypesJson = template.constTypes.map { tpe =>
          val w = SigmaSerializer.startWriter()
          TypeSerializer.serialize(tpe, w)
          Json.fromString("0x" + Base16.encode(w.toBytes))
        }

        // Serialize values to hex strings or null
        val constValuesJson = template.constValues match {
          case Some(values) =>
            values.map {
              case Some(value) =>
                // Find the corresponding type
                val idx = values.indexOf(Some(value))
                if (idx >= 0 && idx < template.constTypes.size) {
                  val tpe = template.constTypes(idx)
                  val w = SigmaSerializer.startWriter()
                  DataSerializer.serialize(value, tpe, w)
                  Json.fromString("0x" + Base16.encode(w.toBytes))
                } else {
                  Json.Null
                }
              case None => Json.Null
            }
          case None =>
            template.constTypes.map(_ => Json.Null)
        }

        // Serialize expression tree to hex string
        val w = SigmaSerializer.startWriter()
        sigma.serialization.ValueSerializer
          .serialize(template.expressionTree, w)
        val expressionTreeHex = "0x" + Base16.encode(w.toBytes)

        Json.obj(
          ("name", Json.fromString(template.name)),
          ("description", Json.fromString(template.description)),
          ("constTypes", Json.arr(constTypesJson: _*)),
          ("constValues", Json.arr(constValuesJson: _*)),
          (
            "parameters",
            Json.arr(template.parameters.map(parameterEncoder.apply): _*)
          ),
          ("expressionTree", Json.fromString(expressionTreeHex))
        )
      }
    }

  /** Encoder for SDK Parameter */
  implicit val parameterEncoder: Encoder[Parameter] = new Encoder[Parameter] {
    final def apply(p: Parameter): Json = Json.obj(
      ("name", Json.fromString(p.name)),
      ("description", Json.fromString(p.description)),
      ("constantIndex", Json.fromInt(p.constantIndex))
    )
  }

  def toJson(template: ContractTemplate): String = {
    printer.print(template.asJson)
  }

  def toJsonCompact(template: ContractTemplate): String = {
    template.asJson.noSpaces
  }
}
