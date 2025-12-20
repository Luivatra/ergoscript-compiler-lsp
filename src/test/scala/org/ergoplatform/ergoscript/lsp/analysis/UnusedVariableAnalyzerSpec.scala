package org.ergoplatform.ergoscript.lsp.analysis

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UnusedVariableAnalyzerSpec extends AnyFlatSpec with Matchers {

  "UnusedVariableAnalyzer" should "detect unused variables" in {
    val script = """{
      |  val usedVar = 100
      |  val unusedVar = 200
      |  val anotherUnused = 300
      |
      |  sigmaProp(HEIGHT > usedVar)
      |}""".stripMargin

    val unusedVars = UnusedVariableAnalyzer.findUnusedVariables(script)

    unusedVars should have size 2
    unusedVars.map(_.name) should contain allOf ("unusedVar", "anotherUnused")
  }

  it should "not report variables that are used" in {
    val script = """{
      |  val x = 100
      |  val y = x + 200
      |  sigmaProp(y > 0)
      |}""".stripMargin

    val unusedVars = UnusedVariableAnalyzer.findUnusedVariables(script)

    unusedVars shouldBe empty
  }

  it should "report all unused variables when none are used" in {
    val script = """{
      |  val a = 1
      |  val b = 2
      |  val c = 3
      |  sigmaProp(true)
      |}""".stripMargin

    val unusedVars = UnusedVariableAnalyzer.findUnusedVariables(script)

    unusedVars should have size 3
    unusedVars.map(_.name) should contain allOf ("a", "b", "c")
  }

  it should "correctly identify variable position" in {
    val script = """{
      |  val unused = 42
      |  sigmaProp(true)
      |}""".stripMargin

    val unusedVars = UnusedVariableAnalyzer.findUnusedVariables(script)

    unusedVars should have size 1
    val unusedVar = unusedVars.head
    unusedVar.name shouldBe "unused"
    unusedVar.line shouldBe 1 // Second line (0-based)
    unusedVar.column should be >= 0
  }
}
