package org.ergoplatform.ergoscript.project

import scorex.util.encode.Base16
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

/** Type system for project constants. Handles parsing and validation of
  * constant values based on their declared types.
  */
object ConstantTypes extends LazyLogging {

  /** Resolve a constant value, supporting environment variable references.
    *
    * @param value
    *   The value string (can be "env:VAR_NAME" or literal)
    * @return
    *   The resolved value
    */
  def resolveValue(value: String): String = {
    if (value.startsWith("env:")) {
      val envVar = value.substring(4)
      sys.env.getOrElse(
        envVar,
        throw new IllegalArgumentException(
          s"Environment variable not found: $envVar"
        )
      )
    } else {
      value
    }
  }

  /** Parse and validate a constant value according to its type.
    *
    * @param constantDef
    *   The constant definition
    * @return
    *   The parsed value as ErgoScript code or error
    */
  def parseConstant(
      constantDef: ConstantDefinition
  ): Either[String, String] = {
    val resolvedValue =
      Try(resolveValue(constantDef.value)).toEither.left.map(_.getMessage)

    resolvedValue.flatMap { value =>
      constantDef.constantType match {
        case "Boolean" => parseBoolean(value)
        case "Byte"    => parseByte(value)
        case "Short"   => parseShort(value)
        case "Int"     => parseInt(value)
        case "Long"    => parseLong(value)
        case "BigInt"  => parseBigInt(value)
        case "String"  => parseString(value)
        case "Coll[Byte]" | "CollByte" =>
          parseCollByte(value)
        case "Address" => parseAddress(value)
        case "GroupElement" =>
          parseGroupElement(value)
        case "SigmaProp" =>
          parseSigmaProp(value)
        case other =>
          Left(s"Unsupported constant type: $other")
      }
    }
  }

  private def parseBoolean(value: String): Either[String, String] = {
    value.toLowerCase match {
      case "true"  => Right("true")
      case "false" => Right("false")
      case _       => Left(s"Invalid boolean value: $value")
    }
  }

  private def parseByte(value: String): Either[String, String] = {
    Try(value.toByte) match {
      case Success(b) => Right(s"${b.toString}")
      case Failure(_) => Left(s"Invalid Byte value: $value")
    }
  }

  private def parseShort(value: String): Either[String, String] = {
    Try(value.toShort) match {
      case Success(s) => Right(s"${s.toString}")
      case Failure(_) => Left(s"Invalid Short value: $value")
    }
  }

  private def parseInt(value: String): Either[String, String] = {
    Try(value.toInt) match {
      case Success(i) => Right(s"$i")
      case Failure(_) => Left(s"Invalid Int value: $value")
    }
  }

  private def parseLong(value: String): Either[String, String] = {
    Try(value.toLong) match {
      case Success(l) => Right(s"${l}L")
      case Failure(_) => Left(s"Invalid Long value: $value")
    }
  }

  private def parseBigInt(value: String): Either[String, String] = {
    Try(BigInt(value)) match {
      case Success(bi) => Right(s"BigInt(${bi.toString})")
      case Failure(_)  => Left(s"Invalid BigInt value: $value")
    }
  }

  private def parseString(value: String): Either[String, String] = {
    // Strings need to be quoted in ErgoScript
    Right(s""""$value"""")
  }

  private def parseCollByte(value: String): Either[String, String] = {
    val hexValue = if (value.startsWith("0x") || value.startsWith("0X")) {
      value.substring(2)
    } else {
      value
    }

    Try(Base16.decode(hexValue)) match {
      case Success(_) =>
        // In ErgoScript, we use fromBase16 for hex strings
        Right(s"""fromBase16("$hexValue")""")
      case Failure(_) =>
        Left(s"Invalid hex string for Coll[Byte]: $value")
    }
  }

  private def parseAddress(value: String): Either[String, String] = {
    // Address is a base58-encoded string that needs to be decoded to a script
    // In ErgoScript, we can use it as a PK or convert to propositionBytes
    // For now, we'll keep it as a string and let the compiler handle it
    if (value.matches("[1-9A-HJ-NP-Za-km-z]+")) {
      // Base58 format check (very basic)
      Right(s""""$value"""")
    } else {
      Left(s"Invalid address format: $value")
    }
  }

  private def parseGroupElement(value: String): Either[String, String] = {
    val hexValue = if (value.startsWith("0x") || value.startsWith("0X")) {
      value.substring(2)
    } else {
      value
    }

    Base16.decode(hexValue) match {
      case Success(bytes) if bytes.length == 33 =>
        // GroupElement is 33 bytes
        Right(s"""decodePoint(fromBase16("$hexValue"))""")
      case Success(bytes) =>
        Left(
          s"Invalid GroupElement: expected 33 bytes, got ${bytes.length}"
        )
      case Failure(_) =>
        Left(s"Invalid hex string for GroupElement: $value")
    }
  }

  private def parseSigmaProp(value: String): Either[String, String] = {
    // SigmaProp can be PK("address") or a complex expression
    if (value.startsWith("PK(") && value.endsWith(")")) {
      // Already in PK format
      Right(value)
    } else if (value.matches("[1-9A-HJ-NP-Za-km-z]+")) {
      // Looks like an address, wrap in PK
      Right(s"""PK("$value")""")
    } else {
      // Assume it's a complex expression
      Right(value)
    }
  }

  /** Get all constant names from a map of definitions.
    *
    * @param constants
    *   Map of constant definitions
    * @return
    *   List of constant names prefixed with $
    */
  def getConstantNames(
      constants: Map[String, ConstantDefinition]
  ): List[String] = {
    constants.keys.map("$" + _).toList
  }

  /** Validate all constants in a project configuration.
    *
    * @param config
    *   Project configuration
    * @return
    *   List of validation errors (empty if all valid)
    */
  def validateConstants(config: ProjectConfig): List[String] = {
    config.constants.toList.flatMap { case (name, constDef) =>
      parseConstant(constDef) match {
        case Left(error) => Some(s"Constant '$name': $error")
        case Right(_)    => None
      }
    }
  }
}
