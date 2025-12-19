package org.ergoplatform.ergoscript.lsp.hover

import org.ergoplatform.ergoscript.lsp.jsonrpc.LspMessages._
import com.typesafe.scalalogging.LazyLogging

/** Provides hover information for ErgoScript symbols.
  *
  * When the user hovers over a symbol, this provider extracts the symbol at
  * that position and returns relevant documentation, type information, and
  * examples.
  */
class HoverProvider extends LazyLogging {

  /** Generate hover information for the symbol at the given position.
    *
    * @param documentText
    *   The full text of the document
    * @param position
    *   The cursor position (0-based line and character)
    * @return
    *   Hover information if a symbol is found at the position, None otherwise
    */
  def hover(
      documentText: String,
      position: Position
  ): Option[Hover] = {
    logger.debug(s"Hover requested at ${position.line}:${position.character}")

    // Extract the symbol at the cursor position
    extractSymbolAtPosition(documentText, position) match {
      case Some((symbol, range)) =>
        logger.debug(s"Found symbol: $symbol")

        // First, try built-in symbols
        HoverSymbols.getHoverInfo(symbol) match {
          case Some(hoverInfo) =>
            val content = formatHoverContent(hoverInfo)
            Some(
              Hover(
                contents = content,
                range = Some(range)
              )
            )
          case None =>
            // If not a built-in, try user-defined symbols
            getUserDefinedHoverInfo(documentText, symbol) match {
              case Some(hoverInfo) =>
                val content = formatHoverContent(hoverInfo)
                Some(
                  Hover(
                    contents = content,
                    range = Some(range)
                  )
                )
              case None =>
                logger.debug(s"No hover info found for symbol: $symbol")
                None
            }
        }

      case None =>
        logger.debug("No symbol found at position")
        None
    }
  }

  /** Get hover information for a user-defined symbol.
    *
    * @param documentText
    *   The full document text
    * @param symbol
    *   The symbol name to look up
    * @return
    *   HoverInfo if the symbol is found and type can be inferred
    */
  private def getUserDefinedHoverInfo(
      documentText: String,
      symbol: String
  ): Option[HoverInfo] = {
    val userSymbols = TypeInference.extractUserDefinedSymbols(documentText)

    userSymbols.get(symbol).flatMap { userSym =>
      TypeInference.inferType(userSym.expression, userSymbols).map {
        inferredType =>
          logger.debug(s"Inferred type for $symbol: $inferredType")
          TypeInference.createUserSymbolHoverInfo(userSym, inferredType)
      }
    }
  }

  /** Extract the symbol (identifier) at the given position.
    *
    * @return
    *   A tuple of (symbol, range) if found, None otherwise
    */
  private def extractSymbolAtPosition(
      documentText: String,
      position: Position
  ): Option[(String, Range)] = {
    val lines = documentText.split("\n", -1)

    if (position.line >= lines.length) {
      return None
    }

    val line = lines(position.line)
    val character = position.character

    if (character >= line.length) {
      return None
    }

    // Find the start and end of the identifier at the cursor position
    // Identifiers consist of alphanumeric characters and underscores
    val isIdentifierChar = (c: Char) => c.isLetterOrDigit || c == '_'

    // If the cursor is not on an identifier character, return None
    if (!isIdentifierChar(line(character))) {
      return None
    }

    // Find the start of the identifier (scan left)
    var start = character
    while (start > 0 && isIdentifierChar(line(start - 1))) {
      start -= 1
    }

    // Find the end of the identifier (scan right)
    var end = character
    while (end < line.length && isIdentifierChar(line(end))) {
      end += 1
    }

    val symbol = line.substring(start, end)
    val range = Range(
      start = Position(position.line, start),
      end = Position(position.line, end)
    )

    Some((symbol, range))
  }

  /** Format hover information into a markdown string suitable for display.
    *
    * The format includes:
    *   - Code block with type signature
    *   - Description
    *   - Optional examples
    */
  private def formatHoverContent(info: HoverInfo): String = {
    val sb = new StringBuilder()

    // Add type signature in a code block
    info.signature.foreach { sig =>
      sb.append("```ergoscript\n")
      sb.append(sig)
      sb.append("\n```\n\n")
    }

    // Add description
    sb.append(info.description)

    // Add category if present
    info.category.foreach { cat =>
      sb.append(s"\n\n**Category:** $cat")
    }

    // Add examples if present
    if (info.examples.nonEmpty) {
      sb.append("\n\n**Examples:**\n")
      info.examples.foreach { example =>
        sb.append("```ergoscript\n")
        sb.append(example)
        sb.append("\n```\n")
      }
    }

    // Add related symbols if present
    if (info.related.nonEmpty) {
      sb.append("\n\n**See also:** ")
      sb.append(info.related.mkString(", "))
    }

    sb.toString()
  }
}

/** Represents hover information for a symbol.
  */
case class HoverInfo(
    signature: Option[String],
    description: String,
    category: Option[String] = None,
    examples: List[String] = List.empty,
    related: List[String] = List.empty
)
