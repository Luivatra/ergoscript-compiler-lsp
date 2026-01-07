package org.ergoplatform.ergoscript.testing

import org.ergoplatform.ergoscript.cli.Compiler
import sigma.ast.ErgoTree
import sigma.compiler.SigmaCompiler
import sigma.compiler.ir.IRContext
import org.ergoplatform.ErgoLikeContext
import sigmastate.interpreter.CErgoTreeEvaluator

import java.nio.file.{Files, Path}
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

/** Configuration for test tracing.
  *
  * @param enabled
  *   Whether tracing is enabled
  * @param format
  *   Output format: "tree", "json", or "compact"
  * @param onFailureOnly
  *   Whether to only trace failed tests
  */
case class TraceConfig(
    enabled: Boolean = false,
    format: String = "tree",
    onFailureOnly: Boolean = true
)

/** Runs ErgoScript tests and evaluates assertions.
  *
  * Note: This is a basic implementation of the testing framework. Full test
  * execution with proving/verification requires additional setup.
  */
class TestRunner extends LazyLogging {

  implicit val IR: IRContext = new sigma.compiler.ir.CompiletimeIRContext()

  /** Run all tests in a file.
    *
    * @param filePath
    *   Path to the test file
    * @param workspaceRoot
    *   Workspace root for resolving imports and constants
    * @param networkPrefix
    *   Network prefix
    * @param traceConfig
    *   Configuration for tracing
    * @return
    *   Test suite result
    */
  def runTestFile(
      filePath: Path,
      workspaceRoot: Option[String] = None,
      networkPrefix: Byte = 0x00,
      traceConfig: TraceConfig = TraceConfig()
  ): TestSuiteResult = {
    val startTime = System.currentTimeMillis()

    logger.info(s"Running tests in ${filePath.getFileName}")

    val source = Try(Files.readString(filePath)).getOrElse {
      logger.error(s"Failed to read test file: $filePath")
      return TestSuiteResult(
        file = filePath.toString,
        tests = List.empty,
        passed = 0,
        failed = 1,
        duration = 0
      )
    }

    // Parse tests
    val tests = TestParser.parseTests(source, filePath.toString)

    if (tests.isEmpty) {
      logger.warn(s"No tests found in $filePath")
      return TestSuiteResult(
        file = filePath.toString,
        tests = List.empty,
        passed = 0,
        failed = 0,
        duration = System.currentTimeMillis() - startTime
      )
    }

    // Compile contracts in the file
    val contractSource = removeTestBlocks(source)

    // Build source position map if tracing is enabled
    val sourceMap = if (traceConfig.enabled) {
      Some(SourcePositionMap.fromSource(source, filePath.getFileName.toString))
    } else {
      None
    }

    // Run each test
    val results = tests.map { test =>
      runTest(
        test,
        contractSource,
        Some(filePath.toString),
        workspaceRoot,
        networkPrefix,
        traceConfig,
        sourceMap
      )
    }

    val passed = results.count(_.passed)
    val failed = results.count(!_.passed)
    val duration = System.currentTimeMillis() - startTime

    TestSuiteResult(
      file = filePath.toString,
      tests = results,
      passed = passed,
      failed = failed,
      duration = duration
    )
  }

  /** Run a single test.
    *
    * @param test
    *   Test definition
    * @param contractSource
    *   Contract source code (without @test blocks)
    * @param testFilePath
    *   Path to the test file (for resolving imports)
    * @param workspaceRoot
    *   Workspace root
    * @param networkPrefix
    *   Network prefix
    * @param traceConfig
    *   Configuration for tracing
    * @param sourceMap
    *   Optional source position map for tracing
    * @return
    *   Test result
    */
  def runTest(
      test: TestDefinition,
      contractSource: String,
      testFilePath: Option[String],
      workspaceRoot: Option[String],
      networkPrefix: Byte,
      traceConfig: TraceConfig = TraceConfig(),
      sourceMap: Option[SourcePositionMap] = None
  ): TestResult = {
    val startTime = System.currentTimeMillis()

    logger.debug(s"Running test: ${test.name}")

    try {
      // First, expand imports to check if there's a contract after expansion
      import org.ergoplatform.ergoscript.lsp.imports.ImportResolver
      val importResult = ImportResolver.expandImports(
        contractSource,
        testFilePath,
        workspaceRoot
      )

      // Detect if this is a library-only test (no @contract annotation after import expansion)
      val hasContract = importResult.expandedCode.code.contains("@contract")

      // If no contract and we have assertions, create a synthetic contract wrapper
      val sourceToCompile =
        if (!hasContract && test.assertions.nonEmpty) {
          // Library function test - create synthetic contract from first assertion
          val assertionExpr = test.assertions.head.expression
          createSyntheticContractFromAssertion(
            contractSource,
            assertionExpr
          )
        } else {
          contractSource
        }

      // Compile the contract (either original or synthetic)
      val compilationResult = Compiler.compileWithImports(
        sourceToCompile,
        test.name,
        "",
        testFilePath,
        workspaceRoot,
        networkPrefix
      )

      compilationResult match {
        case Left(error) =>
          TestResult(
            name = test.name,
            passed = false,
            duration = System.currentTimeMillis() - startTime,
            error = Some(s"Compilation failed: ${error.message}"),
            assertions = List.empty
          )

        case Right(result) =>
          // Build context
          val context =
            ContextBuilder.build(test.context, Some(result.ergoTree))

          context match {
            case Success(ctx) =>
              // Evaluate assertions
              val assertionResults = test.assertions.map { assertion =>
                // Check if we have a template and need to bind parameters
                val treeToEvaluate = if (result.template.isDefined) {
                  val template = result.template.get
                  // Try to extract parameters from the assertion expression
                  extractTemplateParameters(
                    assertion.expression,
                    template
                  ) match {
                    case Some(params) =>
                      // Re-bind the template with the extracted parameters
                      template.applyTemplate(
                        Some(result.ergoTree.version),
                        params
                      )
                    case None =>
                      // No parameters found, use the default tree
                      result.ergoTree
                  }
                } else {
                  // Not a template, use the compiled tree
                  result.ergoTree
                }

                evaluateAssertion(
                  assertion,
                  ctx,
                  treeToEvaluate,
                  networkPrefix,
                  traceConfig,
                  result.sourceMap.orElse(
                    sourceMap
                  ), // Use compilation source map if available
                  result.expandedCode // Pass expanded code for import source mapping
                )
              }

              val allPassed = assertionResults.forall(_.passed)
              val error = if (!allPassed) {
                Some(
                  assertionResults
                    .find(!_.passed)
                    .map(_.message)
                    .getOrElse("Assertion failed")
                )
              } else {
                None
              }

              TestResult(
                name = test.name,
                passed = allPassed,
                duration = System.currentTimeMillis() - startTime,
                error = error,
                assertions = assertionResults
              )

            case Failure(ex) =>
              TestResult(
                name = test.name,
                passed = false,
                duration = System.currentTimeMillis() - startTime,
                error = Some(s"Context building failed: ${ex.getMessage}"),
                assertions = List.empty
              )
          }
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Test ${test.name} failed with exception", ex)
        TestResult(
          name = test.name,
          passed = false,
          duration = System.currentTimeMillis() - startTime,
          error = Some(s"Exception: ${ex.getMessage}"),
          assertions = List.empty
        )
    }
  }

  /** Evaluate a single assertion.
    *
    * Note: This is a simplified implementation that checks basic contract
    * evaluation. Full assertion support with proving/verification requires
    * additional work.
    */
  private def evaluateAssertion(
      assertion: TestAssertion,
      context: ErgoLikeContext,
      contractTree: ErgoTree,
      networkPrefix: Byte,
      traceConfig: TraceConfig = TraceConfig(),
      sourceMap: Option[SourcePositionMap] = None,
      expandedCode: Option[
        org.ergoplatform.ergoscript.lsp.imports.ExpandedCode
      ] = None
  ): AssertionResult = {
    assertion.assertionType match {
      case AssertionType.Equals =>
        evaluateEqualsAssertion(
          assertion,
          context,
          contractTree,
          networkPrefix,
          traceConfig,
          sourceMap,
          expandedCode
        )
      case AssertionType.NotEquals =>
        val result = evaluateEqualsAssertion(
          assertion,
          context,
          contractTree,
          networkPrefix,
          traceConfig,
          sourceMap,
          expandedCode
        )
        result.copy(passed = !result.passed)
      case AssertionType.Provable =>
        // For now, treat as equals
        evaluateEqualsAssertion(
          assertion,
          context,
          contractTree,
          networkPrefix,
          traceConfig,
          sourceMap,
          expandedCode
        )
      case AssertionType.NotProvable =>
        val result = evaluateEqualsAssertion(
          assertion,
          context,
          contractTree,
          networkPrefix,
          traceConfig,
          sourceMap,
          expandedCode
        )
        result.copy(passed = !result.passed)
    }
  }

  /** Evaluate an equals assertion by compiling and checking the expression.
    *
    * Note: This is a simplified implementation. Full evaluation with
    * CErgoTreeEvaluator requires additional setup with current sigma-state
    * version.
    */
  private def evaluateEqualsAssertion(
      assertion: TestAssertion,
      context: ErgoLikeContext,
      contractTree: ErgoTree,
      networkPrefix: Byte,
      traceConfig: TraceConfig = TraceConfig(),
      sourceMap: Option[SourcePositionMap] = None,
      expandedCode: Option[
        org.ergoplatform.ergoscript.lsp.imports.ExpandedCode
      ] = None
  ): AssertionResult = {
    Try {
      val expectedValue = assertion.expected.getOrElse("true").toLowerCase
      val expectedBool = expectedValue == "true"

      // Use tracing evaluator if tracing is enabled
      if (traceConfig.enabled) {
        val tracingEvaluator =
          new TracingEvaluator(context, contractTree, sourceMap, expandedCode)
        val tracedResult = tracingEvaluator.evaluateWithTrace()

        val passed = tracedResult.result == expectedBool

        // Only include trace if we should (based on config)
        val includeTrace = !traceConfig.onFailureOnly || !passed

        AssertionResult(
          assertion = assertion,
          passed = passed,
          message = if (passed) {
            s"✓ ${assertion.description.getOrElse(assertion.expression)}"
          } else {
            s"✗ ${assertion.description.getOrElse(assertion.expression)}: expected $expectedValue, got ${tracedResult.result}"
          },
          trace = if (includeTrace) Some(tracedResult) else None
        )
      } else {
        // Standard evaluation without tracing
        import sigmastate.interpreter.CErgoTreeEvaluator
        import sigma.VersionContext

        // Set the global version context to match the contract version
        val treeVersion = contractTree.version
        VersionContext.withVersions(treeVersion, treeVersion) {
          val reductionResult = CErgoTreeEvaluator.evalToCrypto(
            context,
            contractTree,
            CErgoTreeEvaluator.DefaultEvalSettings
          )

          // Check if evaluation result matches expected
          import sigma.data.TrivialProp
          val actualResult = reductionResult.value match {
            case TrivialProp.TrueProp  => true
            case TrivialProp.FalseProp => false
            case _ =>
              true // SigmaProp that needs proving - consider as potentially true for now
          }

          val passed = actualResult == expectedBool

          AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) {
              s"✓ ${assertion.description.getOrElse(assertion.expression)}"
            } else {
              s"✗ ${assertion.description.getOrElse(assertion.expression)}: expected $expectedValue, got $actualResult"
            }
          )
        }
      }
    } match {
      case Success(result) => result
      case Failure(ex)     =>
        // Print stack trace for debugging
        ex.printStackTrace()
        AssertionResult(
          assertion = assertion,
          passed = false,
          message =
            s"✗ ${assertion.description.getOrElse(assertion.expression)}: ${ex.getClass.getSimpleName}: ${ex.getMessage}"
        )
    }
  }

  /** Extract template parameters from an assertion expression.
    *
    * Parses expressions like "heightLock(minHeight = 100)" to extract parameter
    * bindings.
    */
  private def extractTemplateParameters(
      expression: String,
      template: org.ergoplatform.sdk.ContractTemplate
  ): Option[Map[String, sigma.ast.Constant[sigma.ast.SType]]] = {
    import sigma.ast._

    // Pattern to match function calls: functionName(param1, param2, ...) or functionName(name = value, ...)
    val functionCallPattern = """(\w+)\s*\(([^)]*)\)""".r

    functionCallPattern.findFirstMatchIn(expression).flatMap { m =>
      val functionName = m.group(1)
      val paramsStr = m.group(2)

      // Check if function name matches template name
      if (functionName != template.name) {
        return None
      }

      // Parse parameter values (supports both positional and named parameters)
      val paramValues = paramsStr.split(",").map(_.trim).filter(_.nonEmpty)

      val paramMap =
        paramValues.zipWithIndex.flatMap { case (paramValue, idx) =>
          // Check if it's a named parameter (contains '=')
          val (paramName, valueStr) = if (paramValue.contains("=")) {
            val parts = paramValue.split("=").map(_.trim)
            (parts(0), parts(1))
          } else {
            // Positional parameter - use template parameter order
            if (idx < template.parameters.length) {
              (template.parameters(idx).name, paramValue)
            } else {
              return None // Too many positional args
            }
          }

          // Find the parameter in the template
          template.parameters
            .find(_.name == paramName)
            .flatMap { param =>
              val constType = template.constTypes(param.constantIndex)

              // Parse the value according to the type
              try {
                val constant: Constant[SType] = constType match {
                  case SInt =>
                    IntConstant(valueStr.toInt)
                      .asInstanceOf[Constant[SType]]
                  case SLong =>
                    val longValue =
                      if (valueStr.endsWith("L"))
                        valueStr.dropRight(1).toLong
                      else
                        valueStr.toLong
                    LongConstant(longValue).asInstanceOf[Constant[SType]]
                  case SBoolean =>
                    BooleanConstant(valueStr.toBoolean)
                      .asInstanceOf[Constant[SType]]
                  case _ =>
                    IntConstant(0).asInstanceOf[Constant[SType]]
                }
                Some(paramName -> constant)
              } catch {
                case _: Exception => None
              }
            }
        }.toMap

      if (paramMap.nonEmpty) Some(paramMap) else None
    }
  }

  /** Create a synthetic contract from library code and assertion expression.
    * This allows unit testing of library functions without requiring a
    * contract.
    *
    * @param libraryCode
    *   The expanded library code (functions, imports, etc.)
    * @param assertionExpr
    *   The assertion expression (e.g., "checkHeight(100) == true")
    * @return
    *   A synthetic contract that wraps the assertion expression
    */
  private def createSyntheticContractFromAssertion(
      libraryCode: String,
      assertionExpr: String
  ): String = {
    // Extract the expression before '==' or '!='
    val expr = if (assertionExpr.contains("==")) {
      assertionExpr.split("==")(0).trim
    } else if (assertionExpr.contains("!=")) {
      assertionExpr.split("!=")(0).trim
    } else {
      assertionExpr.trim
    }

    // Create synthetic contract wrapping the expression
    // Note: We use a simple contract expression without @contract annotation
    // because after import expansion, library functions will be included
    // which makes @contract templates invalid
    s"""{
       |  $libraryCode
       |  $expr
       |}
     """.stripMargin
  }

  /** Remove @test and @fixture blocks from source to get clean contract code.
    */
  private def removeTestBlocks(source: String): String = {
    val testStartPattern = """@test\s+def\s+\w+\s*\(\s*\)\s*=""".r
    val fixturePattern = """@fixture\s+def\s+\w+\s*\([^)]*\)\s*=\s*[^\n]+""".r

    var cleaned = source

    // Remove @test blocks using balanced brace matching
    var offset = 0
    testStartPattern.findAllMatchIn(source).foreach { m =>
      val start = m.start - offset
      val bodyStart = m.end - offset

      // Find balanced braces
      TestParser.extractBalancedBraces(cleaned, bodyStart) match {
        case Some((_, endPos)) =>
          val before = cleaned.substring(0, start)
          val after = cleaned.substring(endPos)
          cleaned = before + after
          offset += (endPos - start)
        case None => // Skip if can't find balanced braces
      }
    }

    // Remove @fixture blocks
    cleaned = fixturePattern.replaceAllIn(cleaned, "")
    cleaned.trim
  }

  /** Run all tests in multiple files.
    */
  def runTestFiles(
      files: List[Path],
      workspaceRoot: Option[String] = None,
      networkPrefix: Byte = 0x00,
      traceConfig: TraceConfig = TraceConfig()
  ): List[TestSuiteResult] = {
    files.map(file =>
      runTestFile(file, workspaceRoot, networkPrefix, traceConfig)
    )
  }
}

object TestRunner {
  def apply(): TestRunner = new TestRunner()
}
