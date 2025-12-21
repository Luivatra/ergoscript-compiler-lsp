package org.ergoplatform.ergoscript.testing

import scala.util.matching.Regex
import scala.util.{Try, Success, Failure}
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging

/** Severity levels for test parse errors */
sealed trait TestParseSeverity
object TestParseSeverity {
  case object Error extends TestParseSeverity
  case object Warning extends TestParseSeverity
}

/** Represents a parsing error or warning in a test file.
  *
  * @param message
  *   Human-readable error message
  * @param line
  *   Line number (1-based)
  * @param column
  *   Column number (1-based)
  * @param severity
  *   Error or Warning
  */
case class TestParseError(
    message: String,
    line: Int,
    column: Int,
    severity: TestParseSeverity
)

/** Parser for @test blocks in ErgoScript files.
  *
  * Syntax:
  * ```
  * @test def testName() = {
  *   @context {
  *     HEIGHT = 100
  *     SELF = Box { value = 1000000L }
  *     INPUTS = [SELF]
  *     OUTPUTS = [Box { value = 900000L }]
  *   }
  *
  *   @assert contractName() == true
  * }
  * ```
  */
object TestParser extends LazyLogging {

  // Pattern to find start of @test definitions
  private val testStartPattern: Regex =
    """@test\s+def\s+(\w+)\s*\(\s*\)\s*=""".r

  // Pattern to find start of @context blocks
  private val contextStartPattern: Regex =
    """@context\s*""".r

  // Pattern to match @assert statements
  private val assertPattern: Regex =
    """@assert(?:\s*\("([^"]+)"\))?\s+(.+?)(?:==|===)\s*(.+?)(?:\s|$)""".r

  // Pattern to match @fixture definitions
  private val fixturePattern: Regex =
    """@fixture\s+def\s+(\w+)\s*\(([^)]*)\)\s*=\s*(.+)""".r

  /** Parse all test definitions from ErgoScript source.
    *
    * @param source
    *   ErgoScript source code
    * @param filePath
    *   Source file path (for error reporting)
    * @return
    *   List of test definitions
    */
  def parseTests(source: String, filePath: String): List[TestDefinition] = {
    testStartPattern
      .findAllMatchIn(source)
      .flatMap { m =>
        val testName = m.group(1)
        val testStart = m.end

        // Find the test body by counting braces
        extractBalancedBraces(source, testStart) match {
          case Some((testBody, _)) =>
            val line = source.substring(0, m.start).count(_ == '\n') + 1
            val column = m.start - source.lastIndexOf('\n', m.start)

            logger.debug(s"Parsing test: $testName at $filePath:$line")

            // Parse context
            val context = parseContext(testBody).getOrElse {
              logger.warn(s"Test $testName has no @context block")
              MockContext(
                height = 0,
                self = MockBox(value = 0),
                inputs = List.empty,
                outputs = List.empty
              )
            }

            // Parse assertions
            val assertions = parseAssertions(testBody)

            Some(
              TestDefinition(
                name = testName,
                context = context,
                assertions = assertions,
                line = line,
                column = column
              )
            )
          case None =>
            logger.warn(s"Failed to extract test body for $testName")
            None
        }
      }
      .toList
  }

  /** Validate test file structure and return any parsing errors/warnings. This
    * method is used by the LSP to provide diagnostics for test files.
    *
    * @param source
    *   Test file source code
    * @param filePath
    *   Source file path (for error reporting)
    * @return
    *   List of parse errors and warnings
    */
  def validateTests(source: String, filePath: String): List[TestParseError] = {
    val errors = ListBuffer[TestParseError]()

    // Check if file has any imports (which is valid)
    val hasImports = source.contains("#import")

    // Check for @test blocks
    val testMatches = testStartPattern.findAllMatchIn(source).toList

    if (testMatches.isEmpty && !hasImports) {
      // Only warn if there are no imports either (could be an empty test file)
      errors += TestParseError(
        message = "No @test blocks found in test file",
        line = 1,
        column = 1,
        severity = TestParseSeverity.Warning
      )
    }

    // Validate each @test block
    testMatches.foreach { m =>
      val testName = m.group(1)
      val testStart = m.end
      val line = source.substring(0, m.start).count(_ == '\n') + 1
      val column = m.start - source.lastIndexOf('\n', m.start)

      // Check for balanced braces
      extractBalancedBraces(source, testStart) match {
        case None =>
          errors += TestParseError(
            message =
              s"Unbalanced braces in test '$testName' - missing closing '}'",
            line = line,
            column = column,
            severity = TestParseSeverity.Error
          )

        case Some((body, _)) =>
          // Check for @context block
          if (!body.contains("@context")) {
            errors += TestParseError(
              message = s"Missing @context block in test '$testName'",
              line = line,
              column = column,
              severity = TestParseSeverity.Warning
            )
          }

          // Check for @assert statements
          if (!body.contains("@assert")) {
            errors += TestParseError(
              message = s"No @assert statements in test '$testName'",
              line = line,
              column = column,
              severity = TestParseSeverity.Warning
            )
          }

          // Validate @context block structure if present
          contextStartPattern.findFirstMatchIn(body).foreach { contextMatch =>
            val contextStart = contextMatch.end
            extractBalancedBraces(body, contextStart) match {
              case None =>
                val contextLine =
                  line + body.substring(0, contextMatch.start).count(_ == '\n')
                errors += TestParseError(
                  message =
                    s"Unbalanced braces in @context block of test '$testName'",
                  line = contextLine,
                  column = 1,
                  severity = TestParseSeverity.Error
                )
              case Some(_) => // Valid context block
            }
          }
      }
    }

    errors.toList
  }

  /** Extract content within balanced braces starting from a position.
    *
    * @param source
    *   Source string
    * @param startPos
    *   Position where '{' is expected
    * @return
    *   Option of (content, endPos) where content is what's inside the braces
    */
  def extractBalancedBraces(
      source: String,
      startPos: Int
  ): Option[(String, Int)] = {
    if (startPos >= source.length) return None

    // Skip whitespace to find opening brace
    var pos = startPos
    while (pos < source.length && source(pos).isWhitespace) {
      pos += 1
    }

    if (pos >= source.length || source(pos) != '{') return None

    val openPos = pos
    pos += 1
    var braceCount = 1

    while (pos < source.length && braceCount > 0) {
      source(pos) match {
        case '{' => braceCount += 1
        case '}' => braceCount -= 1
        case _   => // continue
      }
      pos += 1
    }

    if (braceCount == 0) {
      Some((source.substring(openPos + 1, pos - 1), pos))
    } else {
      None
    }
  }

  /** Parse @context block from test body.
    *
    * @param testBody
    *   Test body text
    * @return
    *   Parsed MockContext or None
    */
  private def parseContext(testBody: String): Option[MockContext] = {
    contextStartPattern.findFirstMatchIn(testBody).flatMap { m =>
      extractBalancedBraces(testBody, m.end).map { case (contextBody, _) =>
        // Parse context fields
        val height = extractLongValue(contextBody, "HEIGHT").getOrElse(0L)
        val selfBox = extractBoxValue(contextBody, "SELF").getOrElse(
          MockBox(value = 0)
        )
        val inputs = extractBoxListValue(contextBody, "INPUTS").getOrElse(
          List(selfBox)
        )
        val outputs = extractBoxListValue(contextBody, "OUTPUTS").getOrElse(
          List.empty
        )
        val dataInputs = extractBoxListValue(contextBody, "DATA_INPUTS")
          .orElse(extractBoxListValue(contextBody, "dataInputs"))
          .getOrElse(List.empty)

        MockContext(
          height = height,
          self = selfBox,
          inputs = inputs,
          outputs = outputs,
          dataInputs = dataInputs,
          preHeader = None
        )
      }
    }
  }

  /** Parse assertions from test body.
    *
    * @param testBody
    *   Test body text
    * @return
    *   List of test assertions
    */
  private def parseAssertions(testBody: String): List[TestAssertion] = {
    assertPattern
      .findAllMatchIn(testBody)
      .map { m =>
        val description = Option(m.group(1))
        val expression = m.group(2).trim
        val expected = m.group(3).trim

        // Determine assertion type based on operator
        val assertionType = if (testBody.contains("===")) {
          AssertionType.Equals // Strict equality (SigmaProp)
        } else {
          AssertionType.Equals // Regular equality
        }

        TestAssertion(
          description = description,
          expression = expression,
          assertionType = assertionType,
          expected = Some(expected)
        )
      }
      .toList
  }

  /** Extract a Long value from context.
    */
  private def extractLongValue(
      context: String,
      fieldName: String
  ): Option[Long] = {
    val pattern = s"$fieldName\\s*=\\s*(\\d+)L?".r
    pattern.findFirstMatchIn(context).flatMap { m =>
      Try(m.group(1).toLong).toOption
    }
  }

  /** Extract a Box value from context.
    */
  private def extractBoxValue(
      context: String,
      fieldName: String
  ): Option[MockBox] = {
    val pattern = s"$fieldName\\s*=\\s*Box\\s*\\{([^}]+)\\}".r
    pattern.findFirstMatchIn(context).map { m =>
      val boxBody = m.group(1)
      parseBoxDefinition(boxBody)
    }
  }

  /** Extract a list of Box values from context.
    */
  private def extractBoxListValue(
      context: String,
      fieldName: String
  ): Option[List[MockBox]] = {
    val pattern = s"$fieldName\\s*=\\s*\\[([^\\]]+)\\]".r
    pattern.findFirstMatchIn(context).map { m =>
      val listBody = m.group(1)

      // Handle special case: [SELF] or references to other boxes
      if (listBody.trim == "SELF") {
        // Return None so caller can substitute SELF box
        return None
      } else {
        // Parse box definitions
        parseBoxList(listBody)
      }
    }
  }

  /** Parse a box definition from its body.
    */
  private def parseBoxDefinition(boxBody: String): MockBox = {
    val value = extractLongValue(boxBody, "value").getOrElse(0L)
    val tokens = extractTokenList(boxBody, "tokens").getOrElse(List.empty)
    val registers = extractRegisters(boxBody).getOrElse(Map.empty)

    MockBox(
      value = value,
      tokens = tokens,
      registers = registers
    )
  }

  /** Parse a list of box definitions.
    */
  private def parseBoxList(listBody: String): List[MockBox] = {
    // Simple parser: split by Box keyword
    val boxPattern = """Box\s*\{([^}]+)\}""".r
    boxPattern
      .findAllMatchIn(listBody)
      .map { m =>
        parseBoxDefinition(m.group(1))
      }
      .toList
  }

  /** Extract token list from box body.
    */
  private def extractTokenList(
      boxBody: String,
      fieldName: String
  ): Option[List[MockToken]] = {
    val pattern = s"$fieldName\\s*=\\s*\\[([^\\]]+)\\]".r
    pattern.findFirstMatchIn(boxBody).map { m =>
      val tokensBody = m.group(1)
      // Parse Token { id = "...", amount = ... }
      val tokenPattern =
        """Token\s*\{\s*id\s*=\s*"([^"]+)"\s*,\s*amount\s*=\s*(\d+)L?\s*\}""".r
      tokenPattern
        .findAllMatchIn(tokensBody)
        .map { tm =>
          MockToken(tm.group(1), tm.group(2).toLong)
        }
        .toList
    }
  }

  /** Extract registers from box body.
    */
  private def extractRegisters(boxBody: String): Option[Map[String, Any]] = {
    val pattern = """registers\s*=\s*\{([^}]+)\}""".r
    pattern.findFirstMatchIn(boxBody).map { m =>
      val registersBody = m.group(1)

      // Parse register assignments: R4 = value, R5 = "string", etc.
      val registerPattern = """(R[4-9])\s*=\s*([^,]+)""".r
      registerPattern
        .findAllMatchIn(registersBody)
        .map { rm =>
          val regName = rm.group(1)
          val regValue = rm.group(2).trim

          // Try to parse the value
          val value: Any = parseRegisterValue(regValue)
          regName -> value
        }
        .toMap
    }
  }

  /** Parse a register value (Int, Long, String, etc.).
    */
  private def parseRegisterValue(valueStr: String): Any = {
    Try {
      if (valueStr.endsWith("L")) {
        valueStr.dropRight(1).toLong
      } else if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
        valueStr.substring(1, valueStr.length - 1)
      } else if (valueStr.startsWith("0x")) {
        valueStr // Hex string
      } else {
        valueStr.toInt
      }
    }.getOrElse(valueStr)
  }

  /** Parse fixture definitions from source.
    *
    * @param source
    *   ErgoScript source code
    * @return
    *   List of fixtures
    */
  def parseFixtures(source: String): List[TestFixture] = {
    fixturePattern
      .findAllMatchIn(source)
      .map { m =>
        val name = m.group(1)
        val params = m.group(2)
        val body = m.group(3)

        // Parse parameters
        val parameters = if (params.trim.nonEmpty) {
          params
            .split(",")
            .map { p =>
              val parts = p.trim.split(":")
              if (parts.length == 2) {
                (parts(0).trim, parts(1).trim)
              } else {
                (parts(0).trim, "Any")
              }
            }
            .toList
        } else {
          List.empty
        }

        TestFixture(name, parameters, body)
      }
      .toList
  }

  /** Check if source contains test definitions.
    */
  def hasTests(source: String): Boolean = {
    testStartPattern.findFirstIn(source).isDefined
  }
}
