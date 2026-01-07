package org.ergoplatform.ergoscript.testing

/** Mock types for ErgoScript testing framework. These types represent the test
  * data structures that users define in @test blocks.
  */

/** Represents a token in a mock box.
  *
  * @param id
  *   Token ID (hex string)
  * @param amount
  *   Token amount
  */
case class MockToken(
    id: String,
    amount: Long
)

/** Represents a mock box for testing.
  *
  * @param id
  *   Box ID (optional, auto-generated if not provided)
  * @param value
  *   Box value in nanoErgs
  * @param propositionBytes
  *   The contract/script (can be a contract reference or raw bytes)
  * @param tokens
  *   List of tokens in the box
  * @param registers
  *   Map of register values (R4-R9)
  * @param creationHeight
  *   Height when box was created
  */
case class MockBox(
    id: Option[String] = None,
    value: Long,
    propositionBytes: Option[String] = None,
    tokens: List[MockToken] = List.empty,
    registers: Map[String, Any] = Map.empty,
    creationHeight: Option[Int] = None
)

/** Represents a mock transaction context for testing.
  *
  * @param height
  *   Current blockchain height
  * @param self
  *   The box being spent (must be in inputs)
  * @param inputs
  *   Transaction inputs
  * @param outputs
  *   Transaction outputs
  * @param dataInputs
  *   Data inputs (read-only)
  * @param preHeader
  *   Pre-header information
  */
case class MockContext(
    height: Long,
    self: MockBox,
    inputs: List[MockBox],
    outputs: List[MockBox],
    dataInputs: List[MockBox] = List.empty,
    preHeader: Option[MockPreHeader] = None
)

/** Represents a mock pre-header for testing.
  *
  * @param version
  *   Protocol version
  * @param parentId
  *   Parent block ID
  * @param timestamp
  *   Block timestamp
  * @param nBits
  *   Difficulty bits
  * @param height
  *   Block height
  * @param minerPk
  *   Miner public key
  * @param votes
  *   Block votes
  */
case class MockPreHeader(
    version: Byte = 2,
    parentId: String,
    timestamp: Long,
    nBits: Long,
    height: Int,
    minerPk: String,
    votes: String = "000000"
)

/** Represents a test definition.
  *
  * @param name
  *   Test name
  * @param context
  *   Mock context
  * @param assertions
  *   List of assertions
  * @param line
  *   Line number in source file
  * @param column
  *   Column number in source file
  */
case class TestDefinition(
    name: String,
    context: MockContext,
    assertions: List[TestAssertion],
    line: Int,
    column: Int
)

/** Types of assertions.
  */
sealed trait AssertionType
object AssertionType {
  case object Equals extends AssertionType
  case object NotEquals extends AssertionType
  case object Provable extends AssertionType
  case object NotProvable extends AssertionType
}

/** Represents a test assertion.
  *
  * @param description
  *   Optional description
  * @param expression
  *   Expression to evaluate
  * @param assertionType
  *   Type of assertion
  * @param expected
  *   Expected value (for Equals assertions)
  */
case class TestAssertion(
    description: Option[String],
    expression: String,
    assertionType: AssertionType,
    expected: Option[String] = None
)

/** Result of an assertion.
  *
  * @param assertion
  *   The assertion that was evaluated
  * @param passed
  *   Whether the assertion passed
  * @param message
  *   Result message
  * @param trace
  *   Optional evaluation trace (when tracing is enabled)
  */
case class AssertionResult(
    assertion: TestAssertion,
    passed: Boolean,
    message: String,
    trace: Option[TracedEvaluation] = None
)

/** Result of a test execution.
  *
  * @param name
  *   Test name
  * @param passed
  *   Whether all assertions passed
  * @param duration
  *   Test duration in milliseconds
  * @param error
  *   Error message if test failed
  * @param assertions
  *   Results of individual assertions
  */
case class TestResult(
    name: String,
    passed: Boolean,
    duration: Long,
    error: Option[String],
    assertions: List[AssertionResult]
)

/** Result of a test suite execution.
  *
  * @param file
  *   Test file path
  * @param tests
  *   Results of individual tests
  * @param passed
  *   Number of tests that passed
  * @param failed
  *   Number of tests that failed
  * @param duration
  *   Total duration in milliseconds
  */
case class TestSuiteResult(
    file: String,
    tests: List[TestResult],
    passed: Int,
    failed: Int,
    duration: Long
)

/** Represents a test fixture (reusable test data).
  *
  * @param name
  *   Fixture name
  * @param parameters
  *   Parameter names and types
  * @param body
  *   Fixture body (returns MockBox or other data)
  */
case class TestFixture(
    name: String,
    parameters: List[(String, String)],
    body: String
)
