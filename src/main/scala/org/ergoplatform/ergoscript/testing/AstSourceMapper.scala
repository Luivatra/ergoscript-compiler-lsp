package org.ergoplatform.ergoscript.testing

import sigma.ast._
import sigma.ast.syntax.SValue
import sigma.data.Nullable
import org.ergoplatform.ergoscript.lsp.imports.ExpandedCode

import scala.collection.mutable

/** Extracts source positions from typed ErgoScript AST nodes.
  *
  * This class walks the typed SValue tree (before it's compiled to ErgoTree)
  * and extracts source position information that's attached to AST nodes by the
  * parser.
  *
  * When expandedCode is provided, positions are resolved back to their original
  * source files (handling imports correctly).
  *
  * @param fileName
  *   The name of the source file (used as fallback)
  * @param expandedCode
  *   Optional expanded code with source mapping for resolving import positions
  */
class AstSourceMapper(
    fileName: String,
    expandedCode: Option[ExpandedCode] = None
) {

  private val positionMap = mutable.Map[String, SourcePosition]()
  // Store expanded line for each mapping to enable position-aware lookup
  private val expandedLineMap = mutable.Map[String, Int]()

  /** Extract source positions from a typed AST.
    *
    * @param ast
    *   The typed SValue tree (output of typecheck phase)
    * @return
    *   SourcePositionMap with positions for all nodes
    */
  def extractPositions(ast: SValue): SourcePositionMap = {
    visitNode(ast, List.empty)

    val mappings = positionMap.map { case (key, pos) =>
      // Create expression mapping with operation type from key
      val opType = key.split(":")(0)
      val expandedLine = expandedLineMap.getOrElse(key, pos.line)
      ExpressionMapping(
        exprHash = key.hashCode,
        exprType = opType,
        sourcePos = pos,
        varBindings = Map.empty,
        expandedLine = Some(expandedLine)
      )
    }.toSeq

    // Extract source lines from positions
    val sourceLines =
      mappings.groupBy(_.sourcePos.line).map { case (line, ms) =>
        line -> ms.head.sourcePos.sourceText
      }

    SourcePositionMap(mappings, sourceLines)
  }

  /** Visit a node and extract its source position.
    *
    * @param node
    *   The AST node
    * @param path
    *   Current path in the AST (for uniqueness)
    */
  private def visitNode(node: SValue, path: List[String]): Unit = {
    // Extract source context if available
    node.sourceContext match {
      case Nullable(srcCtx) =>
        val opName = getOperationName(node)
        val expandedLine = srcCtx.line
        val key = s"$opName:$expandedLine:${srcCtx.column}"

        if (!positionMap.contains(key)) {
          // Resolve to original position if we have expandedCode mapping
          val resolvedPos = expandedCode
            .flatMap(_.getOriginalLocation(expandedLine))
            .map { origLoc =>
              SourcePosition(
                file = origLoc.originalFile,
                line = origLoc.originalLine,
                column = srcCtx.column,
                sourceText = extractSourceText(srcCtx)
              )
            }
            .getOrElse {
              // Fallback: use file name and expanded code position
              SourcePosition(
                file = fileName,
                line = expandedLine,
                column = srcCtx.column,
                sourceText = extractSourceText(srcCtx)
              )
            }

          positionMap(key) = resolvedPos
          expandedLineMap(key) = expandedLine
        }
      case _ => // No source context
    }

    // Recursively visit children
    visitChildren(node, path)
  }

  /** Get a descriptive operation name for an AST node. */
  private def getOperationName(node: SValue): String = node match {
    // Binary operations
    case _: GT[_]      => "GT"
    case _: LT[_]      => "LT"
    case _: GE[_]      => "GE"
    case _: LE[_]      => "LE"
    case _: EQ[_]      => "EQ"
    case _: NEQ[_]     => "NEQ"
    case _: BinAnd     => "BinAnd"
    case _: BinOr      => "BinOr"
    case a: ArithOp[_] => a.opName // Plus, Minus, Multiply, etc.

    // Logical operations
    case _: LogicalNot      => "LogicalNot"
    case _: BoolToSigmaProp => "BoolToSigmaProp"

    // Control flow
    case _: If[_] => "If"
    case _: Block => "Block"

    // Value definitions
    case v: ValDef    => s"ValDef:${v.id}"
    case v: ValUse[_] => s"ValUse:${v.valId}"

    // Function operations
    case _: Apply      => "Apply"
    case _: MethodCall => "MethodCall"
    case _: FuncValue  => "FuncValue"

    // Collection operations
    case _: Fold[_, _]          => "Fold"
    case _: MapCollection[_, _] => "Map"
    case _: Filter[_]           => "Filter"
    case _: Exists[_]           => "Exists"
    case _: ForAll[_]           => "ForAll"

    // Context operations
    case Height  => "Height"
    case Inputs  => "Inputs"
    case Outputs => "Outputs"
    case Self    => "Self"

    // Constants and placeholders
    case _: ConstantNode[_]        => "Constant"
    case _: ConstantPlaceholder[_] => "ConstantPlaceholder"

    // Default
    case _ => node.companion.typeName
  }

  /** Visit all children of a node. */
  private def visitChildren(node: SValue, path: List[String]): Unit =
    node match {
      // Binary operations (includes comparisons, logical, and arithmetic ops)
      case n: TwoArgumentsOperation[_, _, _] =>
        visitNode(n.left, "left" :: path)
        visitNode(n.right, "right" :: path)

      // Unary operations
      case n: Transformer[_, _] =>
        visitNode(n.input, "input" :: path)

      // Control flow
      case n: If[_] =>
        visitNode(n.condition, "cond" :: path)
        visitNode(n.trueBranch, "then" :: path)
        visitNode(n.falseBranch, "else" :: path)

      case n: Block =>
        n.bindings.zipWithIndex.foreach { case (valBinding, idx) =>
          visitNode(valBinding.body, s"binding$idx" :: path)
        }
        visitNode(n.result, "result" :: path)

      // Function operations
      case n: Apply =>
        visitNode(n.func, "func" :: path)
        n.args.zipWithIndex.foreach { case (arg, idx) =>
          visitNode(arg, s"arg$idx" :: path)
        }

      case n: MethodCall =>
        visitNode(n.obj, "obj" :: path)
        n.args.zipWithIndex.foreach { case (arg, idx) =>
          visitNode(arg, s"arg$idx" :: path)
        }

      case n: FuncValue =>
        visitNode(n.body, "body" :: path)

      // Collection operations
      case n: Fold[_, _] =>
        visitNode(n.input, "input" :: path)
        visitNode(n.zero, "zero" :: path)
        visitNode(n.foldOp, "op" :: path)

      case n: MapCollection[_, _] =>
        visitNode(n.input, "input" :: path)
        visitNode(n.mapper, "mapper" :: path)

      case n: Filter[_] =>
        visitNode(n.input, "input" :: path)
        visitNode(n.condition, "cond" :: path)

      case n: Exists[_] =>
        visitNode(n.input, "input" :: path)
        visitNode(n.condition, "cond" :: path)

      case n: ForAll[_] =>
        visitNode(n.input, "input" :: path)
        visitNode(n.condition, "cond" :: path)

      // Leaf nodes have no children
      case _ => ()
    }

  /** Extract source text from source context. */
  private def extractSourceText(srcCtx: SourceContext): String = {
    // SourceContext has sourceLine as a field
    if (srcCtx.sourceLine.nonEmpty) {
      srcCtx.sourceLine
    } else {
      s"line ${srcCtx.line}:${srcCtx.column}"
    }
  }
}

object AstSourceMapper {

  /** Create a source position map from a typed AST.
    *
    * @param ast
    *   The typed SValue tree
    * @param fileName
    *   The source file name
    * @param expandedCode
    *   Optional expanded code for resolving import positions
    * @return
    *   SourcePositionMap with extracted positions
    */
  def fromAst(
      ast: SValue,
      fileName: String,
      expandedCode: Option[ExpandedCode] = None
  ): SourcePositionMap = {
    val mapper = new AstSourceMapper(fileName, expandedCode)
    mapper.extractPositions(ast)
  }
}
