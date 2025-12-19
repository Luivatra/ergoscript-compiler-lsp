package org.ergoplatform.ergoscript.lsp.hover

import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging

/** Provides type inference for user-defined values in ErgoScript.
  *
  * This uses heuristic-based analysis to infer types from expressions without
  * requiring full compilation.
  */
object TypeInference extends LazyLogging {

  /** Represents a user-defined symbol with its expression.
    */
  case class UserSymbol(
      name: String,
      expression: String,
      lineNumber: Int
  )

  /** Extract all user-defined symbols (val declarations) from the document.
    *
    * @param documentText
    *   The full document text
    * @return
    *   Map of symbol names to their definitions
    */
  def extractUserDefinedSymbols(
      documentText: String
  ): Map[String, UserSymbol] = {
    // Pattern to match: val <name> = <expression>
    // Handles both simple and multi-line expressions
    val valPattern = """val\s+(\w+)\s*=\s*([^\n]+)""".r

    val lines = documentText.split("\n")
    val symbols = scala.collection.mutable.Map[String, UserSymbol]()

    valPattern.findAllMatchIn(documentText).foreach { m =>
      val name = m.group(1)
      val expression = m.group(2).trim

      // Find line number
      val matchStart = m.start
      val lineNumber = documentText.substring(0, matchStart).count(_ == '\n')

      symbols(name) = UserSymbol(name, expression, lineNumber)
      logger.debug(s"Found user symbol: $name = $expression (line $lineNumber)")
    }

    symbols.toMap
  }

  /** Infer the type of an expression using heuristic analysis.
    *
    * @param expression
    *   The expression to analyze
    * @param knownSymbols
    *   Previously defined symbols for resolution
    * @return
    *   The inferred type as a string, or None if type cannot be inferred
    */
  def inferType(
      expression: String,
      knownSymbols: Map[String, UserSymbol] = Map.empty
  ): Option[String] = {
    val expr = expression.trim

    logger.debug(s"Inferring type for expression: $expr")

    // Check for function calls FIRST (before checking operators inside them)

    // Collection constructor
    if (expr.startsWith("Coll(")) {
      // Try to infer element type from first element
      val insideParens = expr.substring(5, expr.lastIndexOf(')'))
      val firstElement = insideParens.split(",").headOption.map(_.trim)
      firstElement.flatMap(inferType(_, knownSymbols)) match {
        case Some(elemType) => return Some(s"Coll[$elemType]")
        case None =>
          return Some("Coll[Int]") // Default to Int if we can't infer
      }
    }

    // SigmaProp functions
    if (
      expr.startsWith("sigmaProp(") || expr.startsWith("proveDlog(") ||
      expr.startsWith("proveDHTuple(") || expr.startsWith("atLeast(") ||
      expr.startsWith("PK(")
    ) {
      return Some("SigmaProp")
    }

    // Hash functions return Coll[Byte]
    if (
      expr.startsWith("blake2b256(") || expr.startsWith("sha256(") ||
      expr.startsWith("fromBase16(") || expr.startsWith("fromBase58(") ||
      expr.startsWith("fromBase64(") || expr.startsWith("longToByteArray(") ||
      expr.startsWith("serialize(") || expr.startsWith("xor(")
    ) {
      return Some("Coll[Byte]")
    }

    // Boolean functions
    if (
      expr.startsWith("allOf(") || expr.startsWith("anyOf(") ||
      expr.startsWith("xorOf(")
    ) {
      return Some("Boolean")
    }

    // BigInt functions
    if (expr.startsWith("byteArrayToBigInt(") || expr.startsWith("bigInt(")) {
      return Some("BigInt")
    }

    // UnsignedBigInt functions
    if (expr.startsWith("unsignedBigInt(")) {
      return Some("UnsignedBigInt")
    }

    // Long functions
    if (expr.startsWith("byteArrayToLong(")) {
      return Some("Long")
    }

    // GroupElement functions
    if (expr.startsWith("decodePoint(") || expr == "groupGenerator") {
      return Some("GroupElement")
    }

    // Generic deserialization (returns based on type parameter, but we can't infer that)
    if (expr.startsWith("deserializeTo[")) {
      // Try to extract type parameter
      val typeParamPattern = """deserializeTo\[(\w+(?:\[.*?\])?)\]""".r
      return typeParamPattern.findFirstMatchIn(expr).map(_.group(1))
    }

    // getVar returns Option[T]
    if (expr.startsWith("getVar[")) {
      val typeParamPattern = """getVar\[(\w+(?:\[.*?\])?)\]""".r
      return typeParamPattern
        .findFirstMatchIn(expr)
        .map(m => s"Option[${m.group(1)}]")
    }

    // Check for getOrElse first (before other chained access handling)
    if (expr.contains(".getOrElse(")) {
      val beforeGetOrElse = expr.substring(0, expr.indexOf(".getOrElse"))
      return inferType(beforeGetOrElse, knownSymbols).flatMap { tpe =>
        if (tpe.startsWith("Option[") && tpe.endsWith("]")) {
          Some(tpe.substring(7, tpe.length - 1))
        } else {
          Some(tpe)
        }
      }
    }

    // Handle collection transformation methods that return collections
    // These must be checked BEFORE boolean operators (to avoid matching operators in lambdas)
    // For chained methods, we process the LAST (rightmost) method call first

    // Find the last occurrence of any collection method
    val mapIdx = expr.lastIndexOf(".map")
    val filterIdx = expr.lastIndexOf(".filter")
    val flatMapIdx = expr.lastIndexOf(".flatMap")
    val foldIdx = expr.lastIndexOf(".fold")
    val zipIdx = expr.lastIndexOf(".zip")
    val existsIdx = expr.lastIndexOf(".exists")
    val forallIdx = expr.lastIndexOf(".forall")

    val lastMethodIdx = List(
      mapIdx,
      filterIdx,
      flatMapIdx,
      foldIdx,
      zipIdx,
      existsIdx,
      forallIdx
    ).max

    if (lastMethodIdx >= 0) {
      // Determine which method it is
      if (lastMethodIdx == existsIdx || lastMethodIdx == forallIdx) {
        return Some("Boolean")
      } else if (lastMethodIdx == filterIdx) {
        val beforeFilter = expr.substring(0, filterIdx)
        return inferType(beforeFilter, knownSymbols)
      } else if (lastMethodIdx == mapIdx) {
        val beforeMap = expr.substring(0, mapIdx)
        inferType(beforeMap, knownSymbols) match {
          case Some(tpe) if tpe.startsWith("Coll[") =>
            return Some("Coll[T]")
          case Some(tpe) if tpe.startsWith("Option[") =>
            return Some("Option[T]")
          case _ => // Continue
        }
      } else if (lastMethodIdx == flatMapIdx) {
        return Some("Coll[T]")
      } else if (lastMethodIdx == foldIdx) {
        return Some("T")
      } else if (lastMethodIdx == zipIdx) {
        val beforeZip = expr.substring(0, zipIdx)
        inferType(beforeZip, knownSymbols) match {
          case Some(tpe) if tpe.startsWith("Coll[") =>
            val innerType = tpe.stripPrefix("Coll[").stripSuffix("]")
            return Some(s"Coll[($innerType, T)]")
          case _ => return Some("Coll[(T, T)]")
        }
      }
    }

    // Check for boolean/comparison operators (but not inside function calls/lambdas)
    // This is checked AFTER collection methods to avoid matching operators in lambda bodies
    if (
      expr.contains(" > ") || expr.contains(" < ") ||
      expr.contains(">=") || expr.contains("<=") ||
      expr.contains(" == ") || expr.contains(" != ") ||
      expr.contains(" && ") || expr.contains(" || ")
    ) {
      return Some("Boolean")
    }
    if (expr.startsWith("!") && expr.length > 1) {
      return Some("Boolean")
    }

    // Handle chained member access like OUTPUTS(0).value
    // Process from right to left to get the final type
    if (
      expr.contains(".") && !expr.contains("(") ||
      (expr.contains(".") && expr.indexOf(".") > expr.lastIndexOf(")"))
    ) {
      // This is a property/method access
      val parts = expr.split("\\.").toList
      if (parts.size >= 2) {
        val lastPart = parts.last
        // Check what the last access is
        lastPart match {
          // Box properties
          case "value" => return Some("Long")
          case "propositionBytes" | "bytes" | "bytesWithoutRef" | "id" =>
            return Some("Coll[Byte]")
          case "tokens"       => return Some("Coll[(Coll[Byte], Long)]")
          case "creationInfo" => return Some("(Int, Coll[Byte])")

          // Context properties
          case "dataInputs"            => return Some("Coll[Box]")
          case "headers"               => return Some("Coll[Header]")
          case "preHeader"             => return Some("PreHeader")
          case "minerPubKey"           => return Some("Coll[Byte]")
          case "LastBlockUtxoRootHash" => return Some("AvlTree")

          // Collection/Option properties
          case "size" | "indices" => return Some("Int")
          case "isEmpty" | "nonEmpty" | "isDefined" | "startsWith" |
              "endsWith" =>
            return Some("Boolean")
          case "propBytes" => return Some("Coll[Byte]")

          // Register access
          case s if s.startsWith("R") && s.length == 2 =>
            // Register without .get
            return extractRegisterType(expr)
          case "get" =>
            // Need to infer what comes before .get
            val beforeGet = parts.dropRight(1).mkString(".")
            return inferTypeBeforeMethod(beforeGet, ".get", knownSymbols)
          case s if s.contains("getOrElse(") =>
            val beforeGetOrElse = parts.dropRight(1).mkString(".")
            return inferType(beforeGetOrElse, knownSymbols).flatMap { tpe =>
              if (tpe.startsWith("Option[") && tpe.endsWith("]")) {
                Some(tpe.substring(7, tpe.length - 1))
              } else {
                Some(tpe)
              }
            }
          case s if s.startsWith("exists(") || s.startsWith("forall(") =>
            return Some("Boolean")
          case _ => // Continue with pattern matching below
        }
      }
    }

    // Remove trailing operations for better matching of base expression
    val baseExpr = expr
      .takeWhile(c => c != '+' && c != '-' && c != '*' && c != '/')
      .trim

    val inferredType = baseExpr match {
      // Global constants
      case "HEIGHT"                                         => Some("Int")
      case "SELF"                                           => Some("Box")
      case "CONTEXT"                                        => Some("Context")
      case s if s.startsWith("OUTPUTS(")                    => Some("Box")
      case s if s.startsWith("INPUTS(")                     => Some("Box")
      case s if s.startsWith("OUTPUTS") && !s.contains("(") => Some("Coll[Box]")
      case s if s.startsWith("INPUTS") && !s.contains("(")  => Some("Coll[Box]")

      // Register access with type parameter
      case s if s.contains(".R") && s.contains("[") && s.contains("]") =>
        extractRegisterType(s)

      // Collection methods with function arguments
      case s if s.contains(".map(") => Some("Coll[T]")
      case s if s.contains(".filter(") =>
        val beforeFilter = s.substring(0, s.indexOf(".filter"))
        inferType(beforeFilter, knownSymbols).map(t =>
          if (t.startsWith("Coll[")) t else s"Coll[$t]"
        )
      case s if s.contains(".fold(") => Some("T")

      // Literals
      case "true" | "false"                            => Some("Boolean")
      case s if s.matches("\\d+L")                     => Some("Long")
      case s if s.matches("\\d+")                      => Some("Int")
      case s if s.matches("\\d+\\.\\d+")               => Some("Double")
      case s if s.startsWith("\"") && s.endsWith("\"") => Some("String")

      // If expressions
      case s if s.startsWith("if") => None

      // Reference to another user-defined symbol
      case s if knownSymbols.contains(s) =>
        inferType(knownSymbols(s).expression, knownSymbols)

      // Unknown
      case _ =>
        logger.debug(s"Could not infer type for: $baseExpr")
        None
    }

    inferredType
  }

  /** Extract the type parameter from a register access expression.
    *
    * Example: "SELF.R4[Int].get" => Some("Int") Example:
    * "SELF.R6[Coll[Byte]].get" => Some("Coll[Byte]")
    */
  private def extractRegisterType(expr: String): Option[String] = {
    // Find the content between [ and ]
    val startIdx = expr.indexOf("[")
    val endIdx = expr.lastIndexOf("]")

    if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) {
      return None
    }

    val tpe = expr.substring(startIdx + 1, endIdx)

    // If it ends with .get, return the unwrapped type
    // Otherwise return Option[T]
    if (expr.endsWith(".get")) {
      Some(tpe)
    } else {
      Some(s"Option[$tpe]")
    }
  }

  /** Infer the type of an expression before a method call.
    *
    * This is used to infer the wrapped type before methods like .get
    */
  private def inferTypeBeforeMethod(
      expr: String,
      method: String,
      knownSymbols: Map[String, UserSymbol]
  ): Option[String] = {
    // Special handling for .get - unwrap Option[T] to T
    if (method == ".get") {
      inferType(expr, knownSymbols).flatMap { tpe =>
        if (tpe.startsWith("Option[") && tpe.endsWith("]")) {
          Some(tpe.substring(7, tpe.length - 1))
        } else {
          Some(tpe)
        }
      }
    } else {
      inferType(expr, knownSymbols)
    }
  }

  /** Create hover information for a user-defined symbol.
    *
    * @param symbol
    *   The user-defined symbol
    * @param inferredType
    *   The inferred type
    * @return
    *   HoverInfo for display
    */
  def createUserSymbolHoverInfo(
      symbol: UserSymbol,
      inferredType: String
  ): HoverInfo = {
    HoverInfo(
      signature = Some(s"val ${symbol.name}: $inferredType"),
      description = s"User-defined value (line ${symbol.lineNumber + 1}).\n\n" +
        s"**Inferred type:** `$inferredType`\n\n" +
        s"**Expression:** `${symbol.expression}`",
      category = Some("Variable"),
      examples = List.empty,
      related = List.empty
    )
  }
}
