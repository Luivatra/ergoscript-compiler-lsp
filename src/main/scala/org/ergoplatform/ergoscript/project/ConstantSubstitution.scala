package org.ergoplatform.ergoscript.project

import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging

/** Handles substitution of project constants in ErgoScript code.
  */
object ConstantSubstitution extends LazyLogging {

  // Pattern to match $CONSTANT_NAME in code
  private val constantPattern: Regex = """\$([A-Z_][A-Z0-9_]*)""".r

  /** Substitute constants in ErgoScript code.
    *
    * @param code
    *   The ErgoScript source code
    * @param constants
    *   Map of constant definitions
    * @return
    *   Code with constants substituted, or error if constant parsing fails
    */
  def substitute(
      code: String,
      constants: Map[String, ConstantDefinition]
  ): Either[String, String] = {
    var errors = List.empty[String]
    var substitutedCode = code

    // Find all constant references in the code
    val matches = constantPattern.findAllMatchIn(code).toList

    // Process each match
    matches.foreach { m =>
      val constantName = m.group(1)
      val constantRef = m.group(0) // Full match including $

      constants.get(constantName) match {
        case Some(constDef) =>
          // Parse and validate the constant
          ConstantTypes.parseConstant(constDef) match {
            case Right(value) =>
              // Replace the constant reference with the parsed value
              substitutedCode = substitutedCode.replace(constantRef, value)
              logger.debug(
                s"Substituted $constantRef with $value (type: ${constDef.constantType})"
              )
            case Left(error) =>
              errors =
                errors :+ s"Error parsing constant '$constantName': $error"
          }
        case None =>
          errors = errors :+ s"Undefined constant: $constantName"
      }
    }

    if (errors.nonEmpty) {
      Left(errors.mkString("; "))
    } else {
      Right(substitutedCode)
    }
  }

  /** Find all constant references in code.
    *
    * @param code
    *   The ErgoScript source code
    * @return
    *   List of constant names (without $ prefix)
    */
  def findConstantReferences(code: String): List[String] = {
    constantPattern.findAllMatchIn(code).map(_.group(1)).toList.distinct
  }

  /** Check if code contains any constant references.
    *
    * @param code
    *   The ErgoScript source code
    * @return
    *   True if code contains constant references
    */
  def hasConstantReferences(code: String): Boolean = {
    constantPattern.findFirstIn(code).isDefined
  }

  /** Validate that all constant references in code are defined.
    *
    * @param code
    *   The ErgoScript source code
    * @param constants
    *   Map of constant definitions
    * @return
    *   List of undefined constant names (empty if all are defined)
    */
  def validateReferences(
      code: String,
      constants: Map[String, ConstantDefinition]
  ): List[String] = {
    findConstantReferences(code).filter(name => !constants.contains(name))
  }

  /** Get completion suggestions for constants at a position in code.
    *
    * @param code
    *   The ErgoScript source code
    * @param position
    *   The position in the code
    * @param constants
    *   Map of constant definitions
    * @return
    *   List of constant names that could be completed (with $ prefix)
    */
  def getCompletionSuggestions(
      code: String,
      position: Int,
      constants: Map[String, ConstantDefinition]
  ): List[String] = {
    if (position > 0 && position <= code.length) {
      val prefix = code.substring(Math.max(0, position - 20), position)
      if (prefix.endsWith("$") || prefix.matches(".*\\$[A-Z_]*$")) {
        // User is typing a constant reference
        constants.keys.map("$" + _).toList.sorted
      } else {
        List.empty
      }
    } else {
      List.empty
    }
  }
}
