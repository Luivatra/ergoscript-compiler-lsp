package org.ergoplatform.ergoscript.testing

import sigma.ast.SType

/** Source position in the original ErgoScript file. */
case class SourcePosition(
    file: String,
    line: Int,
    column: Int,
    sourceText: String // The actual ErgoScript code snippet
)

/** A node in the evaluation trace tree.
  *
  * Each node represents a single operation during ErgoTree evaluation,
  * capturing the operation type, computed value, cost, and any sub-expressions.
  *
  * @param id
  *   Unique node ID for tree structure identification
  * @param operation
  *   Operation code (e.g., "GT", "AND", "ValUse", "Fold")
  * @param operationDesc
  *   Human-readable description of the operation
  * @param value
  *   The computed value (can be Any type from evaluation)
  * @param valueStr
  *   Formatted string representation of the value
  * @param valueType
  *   The Sigma type of the value (if known)
  * @param cost
  *   Cost of this operation in JIT cost units
  * @param sourcePos
  *   Optional mapped source location in the original ErgoScript
  * @param children
  *   Sub-expressions that were evaluated as part of this operation
  * @param isLoop
  *   Whether this is a loop operation (fold/map/filter) that can be collapsed
  * @param loopIterations
  *   For loop operations, the individual iteration traces
  */
case class TracedNode(
    id: Int,
    operation: String,
    operationDesc: String,
    value: Any,
    valueStr: String,
    valueType: Option[String] = None,
    cost: Long = 0,
    sourcePos: Option[SourcePosition] = None,
    children: Seq[TracedNode] = Seq.empty,
    isLoop: Boolean = false,
    loopIterations: Option[Seq[TracedNode]] = None
)

/** Result of an evaluation with tracing enabled.
  *
  * @param result
  *   The final evaluation result (true/false for contracts)
  * @param rootTrace
  *   The root of the trace tree containing all evaluated expressions
  * @param totalCost
  *   Total accumulated cost of the evaluation
  * @param operationCount
  *   Total number of operations traced
  */
case class TracedEvaluation(
    result: Boolean,
    rootTrace: TracedNode,
    totalCost: Long,
    operationCount: Int
)

/** Mapping from ErgoTree expression structure to source positions.
  *
  * This is built during compilation and used during tracing to correlate
  * evaluated expressions back to their source locations.
  *
  * @param exprHash
  *   Hash of expression structure
  * @param exprType
  *   Operation type e.g., "GT", "ValDef", "Apply"
  * @param sourcePos
  *   The resolved source position (original file for imports)
  * @param varBindings
  *   Variable names used in expression
  * @param expandedLine
  *   Line number in expanded code (for position-aware lookup)
  */
case class ExpressionMapping(
    exprHash: Int,
    exprType: String,
    sourcePos: SourcePosition,
    varBindings: Map[String, String] = Map.empty,
    expandedLine: Option[Int] = None
)

/** Entry in the value trace, capturing the evaluated result of an AST node.
  *
  * @param nodeId
  *   Unique identifier for this trace entry
  * @param operation
  *   Operation type (e.g., "GT", "Height", "Constant")
  * @param value
  *   The computed value
  * @param valueStr
  *   Formatted string representation of the value
  * @param expandedLine
  *   Line number in expanded code (for correlation with source mapping)
  * @param column
  *   Column number in expanded code
  * @param inputs
  *   Input values to this operation (for debugging)
  */
case class ValueTraceEntry(
    nodeId: Int,
    operation: String,
    value: Any,
    valueStr: String,
    expandedLine: Int,
    column: Int,
    inputs: Seq[Any] = Seq.empty
)

/** A collection of expression-to-source mappings built during compilation.
  *
  * @param mappings
  *   List of all expression mappings
  * @param sourceLines
  *   Map from line number to source text for quick lookup
  */
case class SourcePositionMap(
    mappings: Seq[ExpressionMapping] = Seq.empty,
    sourceLines: Map[Int, String] = Map.empty
) {

  /** Look up source position by expression hash. */
  def lookupByHash(hash: Int): Option[SourcePosition] =
    mappings.find(_.exprHash == hash).map(_.sourcePos)

  /** Look up source position by expression type and approximate line. */
  def lookupByTypeAndLine(
      exprType: String,
      approximateLine: Int
  ): Option[SourcePosition] = {
    // Find mappings of the same type, preferring those closest to the approximate line
    mappings
      .filter(_.exprType == exprType)
      .sortBy(m => Math.abs(m.sourcePos.line - approximateLine))
      .headOption
      .map(_.sourcePos)
  }

  /** Look up source position by operation name.
    *
    * This method tries to find a source position for an operation by:
    *   1. Looking for exact matches of the operation name 2. Looking for
    *      partial matches (e.g., "GT" matches "GT", "Plus" matches "Plus")
    *
    * @param operationName
    *   The operation name from the cost trace (e.g., "GT", "Plus", "Height")
    * @return
    *   The first matching source position, if found
    */
  def lookupByOperation(operationName: String): Option[SourcePosition] = {
    // Try exact match first
    mappings.find(_.exprType == operationName).map(_.sourcePos).orElse {
      // Try to find operations that contain this name
      // For example, "Plus" might be stored as "ArithOp.Plus"
      mappings
        .find(m =>
          m.exprType.contains(operationName) || operationName
            .contains(m.exprType)
        )
        .map(_.sourcePos)
    }
  }

  /** Look up source position by operation name and expanded line position.
    *
    * This method finds the mapping that best matches both the operation type
    * and the position in the expanded code. This is essential for correctly
    * handling the same operation appearing multiple times (e.g., in different
    * imported files).
    *
    * @param operationName
    *   The operation name from the cost trace (e.g., "GT", "Plus", "Height")
    * @param expandedLine
    *   The line number in the expanded code where this operation was traced
    * @return
    *   The closest matching source position, if found
    */
  def lookupByOperationAndPosition(
      operationName: String,
      expandedLine: Int
  ): Option[SourcePosition] = {
    // Find all mappings of this operation type
    val candidates = mappings.filter { m =>
      m.exprType == operationName ||
      m.exprType.contains(operationName) ||
      operationName.contains(m.exprType)
    }

    if (candidates.isEmpty) {
      None
    } else if (candidates.size == 1) {
      Some(candidates.head.sourcePos)
    } else {
      // Find the mapping closest to the expanded line
      val closest = candidates.minBy { m =>
        m.expandedLine match {
          case Some(expLine) => Math.abs(expLine - expandedLine)
          case None          => Math.abs(m.sourcePos.line - expandedLine)
        }
      }
      Some(closest.sourcePos)
    }
  }

  /** Look up all source positions for a given operation type. Useful when an
    * operation appears multiple times.
    */
  def lookupAllByOperation(operationName: String): Seq[SourcePosition] = {
    mappings
      .filter(m =>
        m.exprType == operationName || m.exprType.contains(operationName)
      )
      .map(_.sourcePos)
  }

  /** Get the source line text for a given line number. */
  def getSourceLine(line: Int): Option[String] = sourceLines.get(line)
}

object SourcePositionMap {

  /** Create an empty source position map. */
  def empty: SourcePositionMap = SourcePositionMap()

  /** Build a source position map from source code.
    *
    * This performs a simple parse of the source to extract line information.
    * Full expression mapping requires integration with the sigma compiler.
    *
    * @param source
    *   The ErgoScript source code
    * @param fileName
    *   The source file name
    * @return
    *   A SourcePositionMap with line information
    */
  def fromSource(source: String, fileName: String): SourcePositionMap = {
    val lines = source.split("\n")
    val sourceLines = lines.zipWithIndex.map { case (text, idx) =>
      (idx + 1) -> text
    }.toMap

    // Extract basic expression positions from the source
    // This is a simplified version - full implementation would parse the AST
    val mappings = extractExpressionMappings(source, fileName)

    SourcePositionMap(mappings, sourceLines)
  }

  /** Extract expression mappings from source code.
    *
    * This performs pattern matching on the source to identify common expression
    * patterns and their positions. This is a heuristic approach that works for
    * many common cases.
    */
  private def extractExpressionMappings(
      source: String,
      fileName: String
  ): Seq[ExpressionMapping] = {
    val lines = source.split("\n")
    val mappings = scala.collection.mutable.ListBuffer[ExpressionMapping]()

    lines.zipWithIndex.foreach { case (line, lineIdx) =>
      val lineNum = lineIdx + 1

      // Detect val definitions
      val valPattern = """val\s+(\w+)\s*=""".r
      valPattern.findAllMatchIn(line).foreach { m =>
        mappings += ExpressionMapping(
          exprHash = s"ValDef:$lineNum:${m.group(1)}".hashCode,
          exprType = "ValDef",
          sourcePos = SourcePosition(fileName, lineNum, m.start + 1, line.trim),
          varBindings = Map("name" -> m.group(1)),
          expandedLine = Some(lineNum)
        )
      }

      // Detect comparison operations
      val comparisonPattern =
        """(\w+(?:\.\w+)*)\s*(>|<|>=|<=|==|!=)\s*(\w+(?:\.\w+)*)""".r
      comparisonPattern.findAllMatchIn(line).foreach { m =>
        val opType = m.group(2) match {
          case ">"  => "GT"
          case "<"  => "LT"
          case ">=" => "GE"
          case "<=" => "LE"
          case "==" => "EQ"
          case "!=" => "NEQ"
          case _    => "COMPARE"
        }
        mappings += ExpressionMapping(
          exprHash = s"$opType:$lineNum:${m.start}".hashCode,
          exprType = opType,
          sourcePos = SourcePosition(fileName, lineNum, m.start + 1, line.trim),
          varBindings = Map("left" -> m.group(1), "right" -> m.group(3)),
          expandedLine = Some(lineNum)
        )
      }

      // Detect boolean operations
      val boolPattern = """(\w+)\s*(&&|\|\|)\s*(\w+)""".r
      boolPattern.findAllMatchIn(line).foreach { m =>
        val opType = m.group(2) match {
          case "&&" => "AND"
          case "||" => "OR"
          case _    => "BOOL"
        }
        mappings += ExpressionMapping(
          exprHash = s"$opType:$lineNum:${m.start}".hashCode,
          exprType = opType,
          sourcePos = SourcePosition(fileName, lineNum, m.start + 1, line.trim),
          varBindings = Map("left" -> m.group(1), "right" -> m.group(3)),
          expandedLine = Some(lineNum)
        )
      }

      // Detect method calls (for collection operations like filter, map, fold)
      val methodPattern = """\.(\w+)\s*\(""".r
      methodPattern.findAllMatchIn(line).foreach { m =>
        val method = m.group(1)
        if (
          Set("filter", "map", "fold", "exists", "forall", "flatMap")
            .contains(method)
        ) {
          mappings += ExpressionMapping(
            exprHash = s"MethodCall:$lineNum:$method".hashCode,
            exprType = s"MethodCall.$method",
            sourcePos =
              SourcePosition(fileName, lineNum, m.start + 1, line.trim),
            varBindings = Map("method" -> method),
            expandedLine = Some(lineNum)
          )
        }
      }
    }

    mappings.toSeq
  }
}
