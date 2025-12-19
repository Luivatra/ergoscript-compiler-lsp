package org.ergoplatform.ergoscript.eip5

import io.circe.{Encoder, Json}
import io.circe.syntax._

case class Parameter(
    name: String,
    description: String,
    constantIndex: Int
)

object Parameter {
  implicit val encoder: Encoder[Parameter] = new Encoder[Parameter] {
    final def apply(p: Parameter): Json = Json.obj(
      ("name", Json.fromString(p.name)),
      ("description", Json.fromString(p.description)),
      ("constantIndex", Json.fromInt(p.constantIndex))
    )
  }
}

case class ContractTemplate(
    name: String,
    description: String,
    constTypes: Seq[String], // Hex-encoded type bytes
    constValues: Seq[Option[String]], // Hex-encoded value bytes or None
    parameters: Seq[Parameter],
    expressionTree: String // Hex-encoded expression bytes
)

object ContractTemplate {
  implicit val encoder: Encoder[ContractTemplate] =
    new Encoder[ContractTemplate] {
      final def apply(ct: ContractTemplate): Json = Json.obj(
        ("name", Json.fromString(ct.name)),
        ("description", Json.fromString(ct.description)),
        ("constTypes", Json.arr(ct.constTypes.map(Json.fromString): _*)),
        (
          "constValues",
          Json.arr(ct.constValues.map {
            case Some(value) => Json.fromString(value)
            case None        => Json.Null
          }: _*)
        ),
        ("parameters", Json.arr(ct.parameters.map(_.asJson): _*)),
        ("expressionTree", Json.fromString(ct.expressionTree))
      )
    }
}
