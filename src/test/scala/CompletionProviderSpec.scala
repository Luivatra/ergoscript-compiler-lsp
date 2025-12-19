package org.ergoplatform.ergoscript.lsp.completion

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.ergoplatform.ergoscript.lsp.jsonrpc.LspMessages._

class CompletionProviderSpec extends AnyFunSuite with Matchers {

  val provider = new CompletionProvider()

  test("General context completion should include keywords") {
    val documentText = ""
    val position = Position(0, 0)
    val result = provider.complete(documentText, position, None)

    result.items.exists(_.label == "val") shouldBe true
    result.items.exists(_.label == "if") shouldBe true
    result.items.exists(_.label == "sigmaProp") shouldBe true
  }

  test("General context completion should include global constants") {
    val documentText = ""
    val position = Position(0, 0)
    val result = provider.complete(documentText, position, None)

    result.items.exists(_.label == "SELF") shouldBe true
    result.items.exists(_.label == "HEIGHT") shouldBe true
    result.items.exists(_.label == "OUTPUTS") shouldBe true
  }

  test("Member access on SELF should return box members") {
    val documentText = "SELF."
    val position = Position(0, 5)
    val result = provider.complete(documentText, position, Some("."))

    result.items.exists(_.label == "value") shouldBe true
    result.items.exists(_.label == "R4") shouldBe true
    result.items.exists(_.label == "propositionBytes") shouldBe true

    // Should not include general keywords in member context
    result.items.exists(_.label == "val") shouldBe false
  }

  test("Member access on OUTPUTS should return box members") {
    val documentText = "OUTPUTS(0)."
    val position = Position(0, 11)
    val result = provider.complete(documentText, position, Some("."))

    result.items.exists(_.label == "value") shouldBe true
    result.items.exists(_.label == "tokens") shouldBe true
  }

  test("Member access on register should return getter members") {
    val documentText = "SELF.R4[Int]."
    val position = Position(0, 13)
    val result = provider.complete(documentText, position, Some("."))

    result.items.exists(_.label == "get") shouldBe true
    result.items.exists(_.label == "getOrElse") shouldBe true
    result.items.exists(_.label == "isDefined") shouldBe true
  }

  test("Function call context should return functions") {
    val documentText = "sigmaProp("
    val position = Position(0, 10)
    val result = provider.complete(documentText, position, Some("("))

    result.items.exists(_.label == "sigmaProp") shouldBe true
    result.items.exists(_.label == "proveDlog") shouldBe true
  }

  test("Completion should work in multi-line documents") {
    val documentText = """{
      |  val deadline = SELF.R4[Int].get
      |  sigmaProp(HEIGHT > deadline)
      |}""".stripMargin

    // Request completion at the start of line 2 (after "sigmaProp(")
    val position = Position(2, 2)
    val result = provider.complete(documentText, position, None)

    // Should have general completions available
    result.items.nonEmpty shouldBe true
  }

  test("Partial member access should still trigger member completion") {
    val documentText = "SELF.val"
    val position = Position(0, 8)
    val result = provider.complete(documentText, position, None)

    // Should recognize this as member access on SELF
    result.items.exists(_.label == "value") shouldBe true
  }

  test("Completion items should have proper structure") {
    val documentText = ""
    val position = Position(0, 0)
    val result = provider.complete(documentText, position, None)

    val sigmaPropItem = result.items.find(_.label == "sigmaProp")
    sigmaPropItem shouldBe defined

    sigmaPropItem.get.kind shouldBe defined
    sigmaPropItem.get.detail shouldBe defined
    sigmaPropItem.get.documentation shouldBe defined
    sigmaPropItem.get.insertText shouldBe defined
  }

  test("Empty document should return general completions") {
    val documentText = ""
    val position = Position(0, 0)
    val result = provider.complete(documentText, position, None)

    result.items.nonEmpty shouldBe true
    result.isIncomplete shouldBe false
  }

  test("Position beyond document bounds should return general completions") {
    val documentText = "sigmaProp(true)"
    val position = Position(10, 0) // Line 10 doesn't exist
    val result = provider.complete(documentText, position, None)

    // Should gracefully handle out-of-bounds position
    result.items.nonEmpty shouldBe true
  }
}
