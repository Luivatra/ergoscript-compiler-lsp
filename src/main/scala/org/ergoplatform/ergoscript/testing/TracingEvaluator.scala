package org.ergoplatform.ergoscript.testing

import org.ergoplatform.ErgoLikeContext
import sigma.ast.{
  ErgoTree,
  SType,
  SBoolean,
  SInt,
  SLong,
  SBigInt,
  SBox,
  SGroupElement,
  SSigmaProp,
  JitCost,
  CostItem,
  FixedCostItem,
  TypeBasedCostItem,
  SeqCostItem,
  OperationDesc
}
import sigma.ast._
import sigma.ast.syntax._
import sigma.data.{TrivialProp, CSigmaProp, SigmaBoolean, Nullable}
import sigma.eval.{EvalSettings, Profiler}
import sigma.{Coll, VersionContext}
import sigmastate.interpreter.{CErgoTreeEvaluator, CostAccumulator}
import sigmastate.eval.CProfiler

import scala.collection.mutable
import scala.util.{Try, Success, Failure}

/** Evaluator that captures a trace of all operations during ErgoTree
  * evaluation.
  *
  * This wraps the standard CErgoTreeEvaluator and extracts the cost trace,
  * converting it into a structured TracedNode tree that shows all intermediate
  * values.
  *
  * @param context
  *   The ErgoLikeContext for evaluation
  * @param ergoTree
  *   The ErgoTree to evaluate
  * @param sourceMap
  *   Optional source position map for correlating traces to source code. When
  *   created with AstSourceMapper.fromAst() with expandedCode, positions are
  *   already resolved to original import files.
  * @param expandedCode
  *   Optional expanded code (kept for backwards compatibility, but source
  *   mapping is now done in AstSourceMapper)
  */
class TracingEvaluator(
    context: ErgoLikeContext,
    ergoTree: ErgoTree,
    sourceMap: Option[SourcePositionMap] = None,
    expandedCode: Option[org.ergoplatform.ergoscript.lsp.imports.ExpandedCode] =
      None
) {

  private var nodeIdCounter = 0

  // Track operation occurrence count for position-aware lookup
  private val operationOccurrences = mutable.Map[String, Int]()

  /** Evaluate the ErgoTree and return both the result and a detailed trace.
    *
    * @return
    *   TracedEvaluation containing the result, trace tree, and cost information
    */
  def evaluateWithTrace(): TracedEvaluation = {
    val treeVersion = ergoTree.version

    VersionContext.withVersions(treeVersion, treeVersion) {
      // Create cost accumulator
      val costAccumulator = new CostAccumulator(
        initialCost = JitCost.fromBlockCost(context.initCost.toInt),
        costLimit = Some(JitCost.fromBlockCost(context.costLimit.toInt))
      )

      // Create eval settings with cost tracing enabled
      val tracingSettings = EvalSettings(
        isMeasureOperationTime = true,
        isMeasureScriptTime = true,
        costTracingEnabled = true
      )

      // Create profiler
      val profiler = new CProfiler

      // Create the evaluator
      val sigmaContext = context.toSigmaContext()
      val evaluator = new CErgoTreeEvaluator(
        sigmaContext,
        ergoTree.constants,
        costAccumulator,
        profiler,
        tracingSettings
      )

      // Clear any previous trace
      evaluator.clearTrace()

      // Perform evaluation
      val proposition = ergoTree.toProposition(replaceConstants = false)
      val evalResult = Try(evaluator.eval(Map.empty, proposition))

      // Get the cost trace
      val costTrace = evaluator.getCostTrace()
      val totalCost = evaluator.getAccumulatedCost.toBlockCost

      // Convert the result
      val (result, resultValue) = evalResult match {
        case Success(value) =>
          value match {
            case sp: CSigmaProp =>
              sp.wrappedValue match {
                case TrivialProp.TrueProp  => (true, "true")
                case TrivialProp.FalseProp => (false, "false")
                case sb: SigmaBoolean =>
                  (true, sb.toString) // Non-trivial sigma prop
              }
            case sb: SigmaBoolean =>
              sb match {
                case TrivialProp.TrueProp  => (true, "true")
                case TrivialProp.FalseProp => (false, "false")
                case _                     => (true, sb.toString)
              }
            case b: Boolean => (b, b.toString)
            case other      => (true, formatValue(other))
          }
        case Failure(ex) =>
          (false, s"Error: ${ex.getMessage}")
      }

      // Capture values by walking the AST
      val valueTrace = captureValuesFromAst(proposition, evaluator)

      // Also capture known context values
      val contextValues = captureContextValues()

      // Build the trace tree from cost items, merging with value trace
      val rootTrace =
        buildTraceTree(costTrace, resultValue, valueTrace ++ contextValues)

      TracedEvaluation(
        result = result,
        rootTrace = rootTrace,
        totalCost = totalCost,
        operationCount = costTrace.length
      )
    }
  }

  /** Capture well-known context values that we can extract directly. These are
    * values from the ErgoLikeContext that we know at trace time.
    */
  private def captureContextValues(): Seq[ValueTraceEntry] = {
    val entries = mutable.ListBuffer[ValueTraceEntry]()
    var entryId = 1000 // Start at 1000 to avoid collision with AST walk entries

    // HEIGHT value
    entries += ValueTraceEntry(
      nodeId = { entryId += 1; entryId },
      operation = "Height",
      value = context.preHeader.height,
      valueStr = context.preHeader.height.toString,
      expandedLine = 0,
      column = 0
    )

    // SELF box value (simplified representation)
    val selfBox = context.boxesToSpend(context.selfIndex)
    entries += ValueTraceEntry(
      nodeId = { entryId += 1; entryId },
      operation = "Self",
      value = selfBox,
      valueStr = s"Box(value=${selfBox.value})",
      expandedLine = 0,
      column = 0
    )

    // INPUTS count
    entries += ValueTraceEntry(
      nodeId = { entryId += 1; entryId },
      operation = "Inputs",
      value = context.boxesToSpend,
      valueStr = s"Coll(${context.boxesToSpend.length} boxes)",
      expandedLine = 0,
      column = 0
    )

    // OUTPUTS count
    entries += ValueTraceEntry(
      nodeId = { entryId += 1; entryId },
      operation = "Outputs",
      value = context.spendingTransaction.outputs,
      valueStr = s"Coll(${context.spendingTransaction.outputs.length} boxes)",
      expandedLine = 0,
      column = 0
    )

    entries.toSeq
  }

  /** Capture values from the AST by walking it and evaluating each node.
    *
    * This creates ValueTraceEntry objects that can be correlated with cost
    * items to provide actual computed values in the trace output.
    */
  private def captureValuesFromAst(
      proposition: SValue,
      evaluator: CErgoTreeEvaluator
  ): Seq[ValueTraceEntry] = {
    val entries = mutable.ListBuffer[ValueTraceEntry]()
    var entryId = 0

    def captureNode(node: SValue, env: Map[Int, Any]): Any = {
      val opName = getOperationName(node)
      val (expandedLine, column) = node.sourceContext match {
        case Nullable(ctx) => (ctx.line, ctx.column)
        case _             => (0, 0)
      }

      // Evaluate the node to get its value
      val value = Try {
        evaluator.eval(env, node)
      }.getOrElse("?")

      val valueStr = formatValue(value)

      entryId += 1
      entries += ValueTraceEntry(
        nodeId = entryId,
        operation = opName,
        value = value,
        valueStr = valueStr,
        expandedLine = expandedLine,
        column = column
      )

      value
    }

    // Walk the AST recursively, capturing values for significant operations
    def walkAst(node: SValue, env: Map[Int, Any]): Unit = {
      node match {
        // Capture values for comparison operations
        case n: GT[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: LT[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: GE[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: LE[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: EQ[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: NEQ[_] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        // Capture logical operations
        case n: BinAnd =>
          walkAst(n.left, env)
          val leftVal = Try(evaluator.eval(env, n.left)).getOrElse(false)
          // Short-circuit: only evaluate right if left is true
          if (leftVal == true || leftVal == TrivialProp.TrueProp) {
            walkAst(n.right, env)
          }
          captureNode(node, env)

        case n: BinOr =>
          walkAst(n.left, env)
          val leftVal = Try(evaluator.eval(env, n.left)).getOrElse(true)
          // Short-circuit: only evaluate right if left is false
          if (leftVal == false || leftVal == TrivialProp.FalseProp) {
            walkAst(n.right, env)
          }
          captureNode(node, env)

        // Capture context operations (these have well-known values)
        case Height =>
          captureNode(node, env)

        case Self =>
          captureNode(node, env)

        case Inputs =>
          captureNode(node, env)

        case Outputs =>
          captureNode(node, env)

        // Capture constants
        case _: ConstantNode[_] =>
          captureNode(node, env)

        case _: ConstantPlaceholder[_] =>
          captureNode(node, env)

        // Capture If expressions
        case n: If[_] =>
          walkAst(n.condition, env)
          val condVal = Try(evaluator.eval(env, n.condition)).getOrElse(false)
          if (condVal == true || condVal == TrivialProp.TrueProp) {
            walkAst(n.trueBranch, env)
          } else {
            walkAst(n.falseBranch, env)
          }
          captureNode(node, env)

        // Capture method calls
        case n: MethodCall =>
          walkAst(n.obj, env)
          n.args.foreach(arg => walkAst(arg, env))
          captureNode(node, env)

        // Capture Apply nodes
        case n: Apply =>
          walkAst(n.func, env)
          n.args.foreach(arg => walkAst(arg, env))
          captureNode(node, env)

        // Capture blocks
        case n: Block =>
          // For blocks, we walk the bindings and result but don't track env
          // since the evaluator handles variable scoping internally
          n.bindings.foreach { valBinding =>
            walkAst(valBinding.body, env)
          }
          walkAst(n.result, env)
          captureNode(node, env)

        // For other operations, just recurse
        case n: TwoArgumentsOperation[_, _, _] =>
          walkAst(n.left, env)
          walkAst(n.right, env)
          captureNode(node, env)

        case n: Transformer[_, _] =>
          walkAst(n.input, env)
          captureNode(node, env)

        case _ =>
          // Capture leaf nodes
          captureNode(node, env)
      }
    }

    Try(walkAst(proposition, Map.empty))
    entries.toSeq
  }

  /** Build a trace tree from the flat list of cost items.
    *
    * This converts the linear cost trace into a hierarchical tree structure by
    * analyzing the operation types and their relationships. Now also merges
    * value information from the AST walk.
    */
  private def buildTraceTree(
      costItems: Seq[CostItem],
      finalResult: String,
      valueTrace: Seq[ValueTraceEntry]
  ): TracedNode = {
    if (costItems.isEmpty) {
      return TracedNode(
        id = nextId(),
        operation = "Result",
        operationDesc = "Evaluation result",
        value = finalResult,
        valueStr = finalResult
      )
    }

    // Create a lookup map for value trace entries by operation and position
    val valueMap =
      valueTrace.groupBy(e => (e.operation, e.expandedLine, e.column))

    // Reset occurrence tracking for each trace tree build
    operationOccurrences.clear()

    // Group related operations into a tree structure
    // For now, create a flat list with the root being the final result
    val childNodes = costItems.map(item => costItemToNode(item, valueMap)).toSeq

    // Create root node representing the entire evaluation
    TracedNode(
      id = nextId(),
      operation = "Evaluation",
      operationDesc = "Contract evaluation",
      value = finalResult,
      valueStr = finalResult,
      cost = childNodes.map(_.cost).sum,
      children = childNodes
    )
  }

  /** Convert a CostItem to a TracedNode. */
  private def costItemToNode(
      item: CostItem,
      valueMap: Map[(String, Int, Int), Seq[ValueTraceEntry]]
  ): TracedNode = {
    val (operation, desc, cost, valueType, isLoop) = item match {
      case FixedCostItem(opDesc, costKind) =>
        (
          opDesc.operationName,
          formatOpDesc(opDesc),
          costKind.cost.value.toLong,
          None,
          false
        )

      case TypeBasedCostItem(opDesc, costKind, tpe) =>
        (
          opDesc.operationName,
          s"${formatOpDesc(opDesc)} : ${formatType(tpe)}",
          costKind.costFunc(tpe).value.toLong,
          Some(formatType(tpe)),
          false
        )

      case SeqCostItem(opDesc, costKind, nItems) =>
        val isLoopOp =
          Set("Fold", "Map", "Filter", "Exists", "ForAll", "FlatMap")
            .contains(opDesc.operationName)
        (
          opDesc.operationName,
          s"${formatOpDesc(opDesc)} ($nItems items)",
          costKind.cost(nItems).value.toLong,
          Some(s"Seq[$nItems]"),
          isLoopOp
        )
    }

    // Track which occurrence of this operation we're at
    val occurrenceIdx = operationOccurrences.getOrElse(operation, 0)
    operationOccurrences(operation) = occurrenceIdx + 1

    // Find source position using position-aware lookup
    // The sourceMap positions are already resolved to original files by AstSourceMapper
    val sourcePos = sourceMap.flatMap { sm =>
      // Get all positions for this operation
      val allPositions = sm.mappings.filter { m =>
        m.exprType == operation ||
        m.exprType.contains(operation) ||
        operation.contains(m.exprType)
      }

      if (allPositions.isEmpty) {
        None
      } else if (allPositions.size == 1) {
        Some(allPositions.head.sourcePos)
      } else {
        // Use occurrence index to select the right position
        allPositions
          .lift(occurrenceIdx)
          .map(_.sourcePos)
          .orElse(
            Some(allPositions.head.sourcePos)
          )
      }
    }

    // Try to get the actual value from the value trace
    // Collect all matching entries for this operation type
    val matchingEntries = valueMap
      .collect {
        case ((op, _, _), entries)
            if op == operation ||
              op.contains(operation) || operation.contains(op) =>
          entries
      }
      .flatten
      .toSeq

    // Use occurrence index to pick the right value entry
    val (value, valueStr) = if (matchingEntries.nonEmpty) {
      val entry =
        matchingEntries.lift(occurrenceIdx).getOrElse(matchingEntries.head)
      (entry.value, entry.valueStr)
    } else {
      ("?", "?")
    }

    TracedNode(
      id = nextId(),
      operation = operation,
      operationDesc = desc,
      value = value,
      valueStr = valueStr,
      valueType = valueType,
      cost = cost,
      sourcePos = sourcePos,
      isLoop = isLoop
    )
  }

  /** Get operation name from an AST node. */
  private def getOperationName(node: SValue): String = node match {
    case _: GT[_]                  => "GT"
    case _: LT[_]                  => "LT"
    case _: GE[_]                  => "GE"
    case _: LE[_]                  => "LE"
    case _: EQ[_]                  => "EQ"
    case _: NEQ[_]                 => "NEQ"
    case _: BinAnd                 => "BinAnd"
    case _: BinOr                  => "BinOr"
    case a: ArithOp[_]             => a.opName
    case _: LogicalNot             => "LogicalNot"
    case _: BoolToSigmaProp        => "BoolToSigmaProp"
    case _: If[_]                  => "If"
    case _: Block                  => "BlockValue"
    case v: ValDef                 => "ValDef"
    case v: ValUse[_]              => "ValUse"
    case _: Apply                  => "Apply"
    case _: MethodCall             => "MethodCall"
    case _: FuncValue              => "FuncValue"
    case _: Fold[_, _]             => "Fold"
    case _: MapCollection[_, _]    => "Map"
    case _: Filter[_]              => "Filter"
    case _: Exists[_]              => "Exists"
    case _: ForAll[_]              => "ForAll"
    case Height                    => "Height"
    case Inputs                    => "Inputs"
    case Outputs                   => "Outputs"
    case Self                      => "Self"
    case _: ConstantNode[_]        => "Constant"
    case _: ConstantPlaceholder[_] => "ConstantPlaceholder"
    case _                         => node.companion.typeName
  }

  /** Format an operation descriptor for display. */
  private def formatOpDesc(opDesc: OperationDesc): String = {
    // The operationName gives us the operation type
    val opName = opDesc.operationName

    // Map common operations to human-readable names
    opName match {
      case "GT"                  => "GT (>)"
      case "LT"                  => "LT (<)"
      case "GE"                  => "GE (>=)"
      case "LE"                  => "LE (<=)"
      case "EQ"                  => "EQ (==)"
      case "NEQ"                 => "NEQ (!=)"
      case "BinAnd"              => "AND (&&)"
      case "BinOr"               => "OR (||)"
      case "LogicalNot"          => "NOT (!)"
      case "ValUse"              => "ValUse"
      case "ConstantPlaceholder" => "ConstantPlaceholder"
      case "BlockValue"          => "BlockValue"
      case "ValDef"              => "ValDef"
      case "If"                  => "If"
      case "Apply"               => "Apply"
      case "MethodCall"          => "MethodCall"
      case "PropertyCall"        => "PropertyCall"
      case "FuncValue"           => "FuncValue"
      case _                     => opName
    }
  }

  /** Format a Sigma type for display. */
  private def formatType(tpe: SType): String = tpe match {
    case SBoolean                        => "Boolean"
    case SInt                            => "Int"
    case SLong                           => "Long"
    case SBigInt                         => "BigInt"
    case SBox                            => "Box"
    case SGroupElement                   => "GroupElement"
    case SSigmaProp                      => "SigmaProp"
    case sigma.ast.SCollectionType(elem) => s"Coll[${formatType(elem)}]"
    case sigma.ast.STuple(items) =>
      s"(${items.map(formatType).mkString(", ")})"
    case sigma.ast.SOption(elem) => s"Option[${formatType(elem)}]"
    case other                   => other.toString
  }

  /** Format a value for display. */
  private def formatValue(value: Any): String = value match {
    case null                     => "null"
    case b: Boolean               => b.toString
    case i: Int                   => i.toString
    case l: Long                  => s"${l}L"
    case bi: java.math.BigInteger => s"BigInt($bi)"
    case coll: Coll[_]            => s"Coll(${coll.length} items)"
    case sp: CSigmaProp           => formatSigmaProp(sp)
    case TrivialProp.TrueProp     => "true"
    case TrivialProp.FalseProp    => "false"
    case Some(v)                  => s"Some(${formatValue(v)})"
    case None                     => "None"
    case (a, b)                   => s"(${formatValue(a)}, ${formatValue(b)})"
    case other                    => other.toString.take(50)
  }

  /** Format a SigmaProp value. */
  private def formatSigmaProp(sp: CSigmaProp): String = {
    sp.wrappedValue match {
      case TrivialProp.TrueProp  => "true"
      case TrivialProp.FalseProp => "false"
      case sb                    => s"SigmaProp(${sb.getClass.getSimpleName})"
    }
  }

  /** Generate a unique node ID. */
  private def nextId(): Int = {
    nodeIdCounter += 1
    nodeIdCounter
  }
}

object TracingEvaluator {

  /** Convenience method to evaluate an ErgoTree with tracing.
    *
    * @param context
    *   The evaluation context
    * @param ergoTree
    *   The tree to evaluate
    * @param sourceMap
    *   Optional source position map
    * @param expandedCode
    *   Optional expanded code for mapping import source locations
    * @return
    *   The traced evaluation result
    */
  def evaluate(
      context: ErgoLikeContext,
      ergoTree: ErgoTree,
      sourceMap: Option[SourcePositionMap] = None,
      expandedCode: Option[
        org.ergoplatform.ergoscript.lsp.imports.ExpandedCode
      ] = None
  ): TracedEvaluation = {
    val evaluator =
      new TracingEvaluator(context, ergoTree, sourceMap, expandedCode)
    evaluator.evaluateWithTrace()
  }
}
