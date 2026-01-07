package org.ergoplatform.ergoscript.testing

import scala.collection.mutable

/** Formats evaluation traces for display in various output formats.
  *
  * Supports tree format (for terminal), JSON format (for tooling), and compact
  * format (for CI/logs).
  */
object TraceFormatter {

  /** Format the trace as a tree structure with box-drawing characters.
    *
    * @param trace
    *   The root traced node
    * @param showCost
    *   Whether to include cost information
    * @param showSource
    *   Whether to include source positions
    * @return
    *   Formatted string with tree structure
    */
  def formatAsTree(
      trace: TracedNode,
      showCost: Boolean = true,
      showSource: Boolean = true
  ): String = {
    val sb = new StringBuilder

    def formatNode(
        node: TracedNode,
        prefix: String,
        isLast: Boolean,
        isRoot: Boolean
    ): Unit = {
      // Build the line prefix
      val connector = if (isRoot) "" else if (isLast) "└─ " else "├─ "
      val nodePrefix = prefix + connector

      // Build the node content
      val valueDisplay =
        if (node.valueStr == "?" || node.valueStr.isEmpty) ""
        else s" → ${node.valueStr}"
      val costDisplay =
        if (showCost && node.cost > 0) s" [cost: ${node.cost}]" else ""
      val sourceDisplay = if (showSource) {
        node.sourcePos
          .map { pos =>
            val fileInfo = s"[${pos.file}:${pos.line}]"
            // Include source text if available and not too long
            val sourceText = if (
              pos.sourceText.nonEmpty && pos.sourceText != s"line ${pos.line}:${pos.column}"
            ) {
              val trimmed = pos.sourceText.trim
              if (trimmed.length <= 60) s" $trimmed"
              else s" ${trimmed.take(57)}..."
            } else ""
            s" $fileInfo$sourceText"
          }
          .getOrElse("")
      } else ""

      // Operation description
      val opDisplay =
        if (node.operationDesc != node.operation) s" ${node.operationDesc}"
        else ""

      sb.append(
        s"$nodePrefix${node.operation}$opDisplay$valueDisplay$costDisplay$sourceDisplay\n"
      )

      // Handle loop iterations specially
      if (node.isLoop && node.loopIterations.isDefined) {
        val childPrefix =
          prefix + (if (isRoot) "" else if (isLast) "   " else "│  ")
        val iterations = node.loopIterations.get
        if (iterations.nonEmpty) {
          sb.append(
            s"$childPrefix├─ [${iterations.length} iterations - expand for details]\n"
          )
          // Show first and last iteration as examples
          if (iterations.length <= 3) {
            iterations.zipWithIndex.foreach { case (iter, idx) =>
              val isLastIter = idx == iterations.length - 1
              formatNode(iter, childPrefix, isLastIter, isRoot = false)
            }
          } else {
            formatNode(
              iterations.head,
              childPrefix,
              isLast = false,
              isRoot = false
            )
            sb.append(
              s"$childPrefix│  ... (${iterations.length - 2} more iterations)\n"
            )
            formatNode(
              iterations.last,
              childPrefix,
              isLast = true,
              isRoot = false
            )
          }
        }
      }

      // Format children
      val children = node.children
      if (children.nonEmpty) {
        val childPrefix =
          prefix + (if (isRoot) "" else if (isLast) "   " else "│  ")
        children.zipWithIndex.foreach { case (child, idx) =>
          val isLastChild = idx == children.length - 1
          formatNode(child, childPrefix, isLastChild, isRoot = false)
        }
      }
    }

    formatNode(trace, "", isLast = true, isRoot = true)
    sb.toString()
  }

  /** Format the trace as JSON for tooling integration.
    *
    * @param trace
    *   The root traced node
    * @return
    *   JSON string representation
    */
  def formatAsJson(trace: TracedNode): String = {
    def nodeToJson(node: TracedNode, indent: Int): String = {
      val pad = "  " * indent
      val childPad = "  " * (indent + 1)

      val sourceJson = node.sourcePos match {
        case Some(pos) =>
          s""",
$childPad"source": {
$childPad  "file": "${escapeJson(pos.file)}",
$childPad  "line": ${pos.line},
$childPad  "column": ${pos.column},
$childPad  "text": "${escapeJson(pos.sourceText)}"
$childPad}"""
        case None => ""
      }

      val valueTypeJson = node.valueType match {
        case Some(t) => s""",
$childPad"valueType": "${escapeJson(t)}""""
        case None => ""
      }

      val loopJson = if (node.isLoop) {
        val iterationsJson = node.loopIterations match {
          case Some(iters) if iters.nonEmpty =>
            val iterStrs = iters.map(i => nodeToJson(i, indent + 2))
            s""",
$childPad"loopIterations": [
${iterStrs.mkString(",\n")}
$childPad]"""
          case _ => ""
        }
        s""",
$childPad"isLoop": true$iterationsJson"""
      } else ""

      val childrenJson = if (node.children.nonEmpty) {
        val childStrs = node.children.map(c => nodeToJson(c, indent + 2))
        s""",
$childPad"children": [
${childStrs.mkString(",\n")}
$childPad]"""
      } else ""

      s"""$pad{
$childPad"id": ${node.id},
$childPad"op": "${escapeJson(node.operation)}",
$childPad"desc": "${escapeJson(node.operationDesc)}",
$childPad"value": "${escapeJson(node.valueStr)}",
$childPad"cost": ${node.cost}$valueTypeJson$sourceJson$loopJson$childrenJson
$pad}"""
    }

    nodeToJson(trace, 0)
  }

  /** Format the trace in compact form for CI/logs.
    *
    * Shows only key operations and their values, one per line.
    *
    * @param trace
    *   The root traced node
    * @param maxDepth
    *   Maximum depth to show (default 3)
    * @return
    *   Compact string representation
    */
  def formatCompact(trace: TracedNode, maxDepth: Int = 3): String = {
    val sb = new StringBuilder

    def formatNode(node: TracedNode, depth: Int): Unit = {
      if (depth > maxDepth) return

      val indent = "  " * depth
      val valueDisplay = if (node.valueStr == "?") "" else s"=${node.valueStr}"

      // Skip trivial operations in compact mode
      val isSignificant = Set(
        "Evaluation",
        "Result",
        "GT",
        "LT",
        "GE",
        "LE",
        "EQ",
        "NEQ",
        "AND",
        "OR",
        "NOT",
        "If",
        "ValDef"
      ).contains(node.operation) || node.valueStr != "?"

      if (isSignificant) {
        sb.append(s"$indent${node.operation}$valueDisplay\n")
      }

      node.children.foreach(c => formatNode(c, depth + 1))
    }

    formatNode(trace, 0)
    sb.toString()
  }

  /** Format a TracedEvaluation as a complete trace report.
    *
    * @param eval
    *   The traced evaluation result
    * @param format
    *   Output format: "tree", "json", or "compact"
    * @param assertionExpr
    *   The assertion expression being traced
    * @param expected
    *   The expected result
    * @return
    *   Formatted trace report
    */
  def formatEvaluation(
      eval: TracedEvaluation,
      format: String,
      assertionExpr: String,
      expected: Boolean
  ): String = {
    val header = if (eval.result == expected) {
      s"✓ $assertionExpr: passed (result=$expected)"
    } else {
      s"✗ $assertionExpr: expected $expected, got ${eval.result}"
    }

    val stats =
      s"Total cost: ${eval.totalCost}, Operations: ${eval.operationCount}"

    val traceContent = format.toLowerCase match {
      case "json"    => formatAsJson(eval.rootTrace)
      case "compact" => formatCompact(eval.rootTrace)
      case _         => formatAsTree(eval.rootTrace)
    }

    s"""$header
       |$stats
       |
       |Evaluation Trace:
       |$traceContent""".stripMargin
  }

  /** Escape a string for JSON output. */
  private def escapeJson(s: String): String = {
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }
}
