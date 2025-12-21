package org.ergoplatform.ergoscript.lsp.imports

/** Represents the original location of code before import expansion.
  *
  * @param originalFile
  *   The file where this code originally appeared
  * @param originalLine
  *   The line number in the original file (1-based)
  * @param originalColumn
  *   The column number in the original file (1-based)
  * @param importChain
  *   The chain of imports leading to this code (for circular import debugging)
  */
case class SourceLocation(
    originalFile: String,
    originalLine: Int,
    originalColumn: Int,
    importChain: List[String]
)

/** Expanded code with source mapping information for error reporting.
  *
  * @param code
  *   The expanded code with all imports resolved
  * @param sourceMap
  *   Maps each line in the expanded code to its original location
  * @param totalLines
  *   Total number of lines in the expanded code
  */
case class ExpandedCode(
    code: String,
    sourceMap: Map[Int, SourceLocation],
    totalLines: Int
) {

  /** Get the original location for a line in the expanded code.
    *
    * @param expandedLine
    *   Line number in the expanded code (1-based)
    * @return
    *   The original source location, if available
    */
  def getOriginalLocation(expandedLine: Int): Option[SourceLocation] = {
    sourceMap.get(expandedLine)
  }

  /** Get the original location with column adjustment.
    *
    * @param expandedLine
    *   Line number in the expanded code (1-based)
    * @param expandedColumn
    *   Column number in the expanded code (1-based)
    * @return
    *   The original source location with adjusted column
    */
  def getOriginalLocation(
      expandedLine: Int,
      expandedColumn: Int
  ): Option[SourceLocation] = {
    sourceMap.get(expandedLine).map { loc =>
      // For now, keep the column as-is since we're doing full line replacements
      // In the future, we could track column offsets more precisely
      loc.copy(originalColumn = expandedColumn)
    }
  }

  /** Format an error message with original source location.
    *
    * @param message
    *   The error message
    * @param expandedLine
    *   Line number in the expanded code (1-based)
    * @param expandedColumn
    *   Optional column number in the expanded code (1-based)
    * @return
    *   Formatted error message with original location
    */
  def formatError(
      message: String,
      expandedLine: Int,
      expandedColumn: Option[Int] = None
  ): String = {
    getOriginalLocation(expandedLine, expandedColumn.getOrElse(1)) match {
      case Some(loc) =>
        val colStr = expandedColumn.map(c => s", column $c").getOrElse("")
        val chainStr =
          if (loc.importChain.nonEmpty)
            s"\n  Import chain: ${loc.importChain.mkString(" -> ")}"
          else ""
        s"${loc.originalFile}:${loc.originalLine}$colStr: $message$chainStr"

      case None =>
        // Fallback if no mapping exists
        val colStr = expandedColumn.map(c => s", column $c").getOrElse("")
        s"line $expandedLine$colStr: $message"
    }
  }
}

object SourceMap {

  /** Create a source map from expanded code.
    *
    * @param code
    *   The expanded code
    * @param mappings
    *   Map of line numbers to source locations
    * @return
    *   ExpandedCode with source mapping
    */
  def apply(
      code: String,
      mappings: Map[Int, SourceLocation]
  ): ExpandedCode = {
    val lines = code.split("\n", -1).length
    ExpandedCode(code, mappings, lines)
  }

  /** Create a simple source map for code without imports.
    *
    * @param code
    *   The source code
    * @param filePath
    *   The file path
    * @return
    *   ExpandedCode with identity mapping
    */
  def identity(code: String, filePath: String): ExpandedCode = {
    val lines = code.split("\n", -1)
    val mappings = lines.indices.map { idx =>
      val lineNum = idx + 1 // Convert to 1-based
      lineNum -> SourceLocation(
        originalFile = filePath,
        originalLine = lineNum,
        originalColumn = 1,
        importChain = List.empty
      )
    }.toMap

    ExpandedCode(code, mappings, lines.length)
  }
}
