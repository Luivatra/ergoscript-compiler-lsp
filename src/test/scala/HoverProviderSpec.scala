package org.ergoplatform.ergoscript.lsp.hover

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.ergoplatform.ergoscript.lsp.jsonrpc.LspMessages._

class HoverProviderSpec extends AnyFunSuite with Matchers {

  val provider = new HoverProvider()

  test("Hover on 'sigmaProp' should return function documentation") {
    val documentText = "sigmaProp(true)"
    val position = Position(0, 2) // Middle of "sigmaProp"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("sigmaProp")
    result.get.contents should include("Boolean")
    result.get.contents should include("SigmaProp")
    result.get.contents should include("Sigma proposition")
    result.get.range shouldBe defined
  }

  test("Hover on 'SELF' should return constant documentation") {
    val documentText = "SELF.value"
    val position = Position(0, 1) // Middle of "SELF"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("SELF")
    result.get.contents should include("Box")
    result.get.contents should include("currently being spent")
  }

  test("Hover on 'HEIGHT' should return constant documentation") {
    val documentText = "HEIGHT > 100"
    val position = Position(0, 3) // Middle of "HEIGHT"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("HEIGHT")
    result.get.contents should include("Int")
    result.get.contents should include("blockchain height")
  }

  test("Hover on 'value' should return property documentation") {
    val documentText = "SELF.value"
    val position = Position(0, 6) // Middle of "value"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("value")
    result.get.contents should include("Long")
    result.get.contents should include("ERG")
  }

  test("Hover on 'get' should return method documentation") {
    val documentText = "SELF.R4[Int].get"
    val position = Position(0, 14) // Middle of "get"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("get")
    result.get.contents should include("Option")
    result.get.contents should include("Extracts the value")
  }

  test("Hover on register 'R4' should return documentation") {
    val documentText = "SELF.R4[Int].get"
    val position = Position(0, 5) // On "R4"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("R4")
    result.get.contents should include("Register")
    result.get.contents should include("type parameter")
  }

  test("Hover on type 'Int' should return type documentation") {
    val documentText = "val x: Int = 5"
    val position = Position(0, 7) // On "Int"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("Int")
    result.get.contents should include("32-bit")
  }

  test("Hover on 'val' keyword should return documentation") {
    val documentText = "val deadline = 100"
    val position = Position(0, 1) // Middle of "val"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("val")
    result.get.contents should include("immutable")
  }

  test("Hover on 'if' keyword should return documentation") {
    val documentText = "if (true) 1 else 0"
    val position = Position(0, 0) // On "if"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("if")
    result.get.contents should include("Conditional")
  }

  test("Hover on collection method 'map' should return documentation") {
    val documentText = "OUTPUTS.map(box => box.value)"
    val position = Position(0, 9) // On "map"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("map")
    result.get.contents should include("Transforms")
  }

  test("Hover on 'blake2b256' should return documentation") {
    val documentText = "blake2b256(data)"
    val position = Position(0, 5) // Middle of "blake2b256"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("blake2b256")
    result.get.contents should include("BLAKE2b-256")
    result.get.contents should include("hash")
  }

  test("Hover on whitespace should return None") {
    val documentText = "SELF.value"
    val position = Position(0, 4) // On the dot "."
    val result = provider.hover(documentText, position)

    result shouldBe None
  }

  test("Hover on operator should return None") {
    val documentText = "HEIGHT > 100"
    val position = Position(0, 7) // On ">"
    val result = provider.hover(documentText, position)

    result shouldBe None
  }

  test("Hover on unknown symbol should return None") {
    val documentText = "unknownSymbol"
    val position = Position(0, 5)
    val result = provider.hover(documentText, position)

    result shouldBe None
  }

  test("Hover should work in multi-line documents") {
    val documentText = """{
      |  val deadline = SELF.R4[Int].get
      |  sigmaProp(HEIGHT > deadline)
      |}""".stripMargin

    // Hover on "sigmaProp" on line 2
    val position = Position(2, 5)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("sigmaProp")
  }

  test("Hover should handle position at start of identifier") {
    val documentText = "HEIGHT"
    val position = Position(0, 0) // At the start of "HEIGHT"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("HEIGHT")
  }

  test("Hover should handle position at end of identifier") {
    val documentText = "HEIGHT"
    val position = Position(0, 5) // At the last char of "HEIGHT"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("HEIGHT")
  }

  test("Hover should return None for position beyond document bounds") {
    val documentText = "HEIGHT"
    val position = Position(10, 0) // Line doesn't exist
    val result = provider.hover(documentText, position)

    result shouldBe None
  }

  test("Hover should return None for position beyond line length") {
    val documentText = "HEIGHT"
    val position = Position(0, 100) // Character beyond line length
    val result = provider.hover(documentText, position)

    result shouldBe None
  }

  test("Hover content should include signature in code block") {
    val documentText = "sigmaProp(true)"
    val position = Position(0, 2)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("```ergoscript")
    result.get.contents should include("```")
  }

  test("Hover content should include category") {
    val documentText = "sigmaProp(true)"
    val position = Position(0, 2)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("**Category:**")
    result.get.contents should include("Function")
  }

  test("Hover content should include examples when available") {
    val documentText = "sigmaProp(true)"
    val position = Position(0, 2)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("**Examples:**")
  }

  test("Hover content should include related symbols when available") {
    val documentText = "sigmaProp(true)"
    val position = Position(0, 2)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("**See also:**")
  }

  test("Hover range should cover the full identifier") {
    val documentText = "  HEIGHT  "
    val position = Position(0, 4) // Inside "HEIGHT"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    val range = result.get.range.get
    range.start.line shouldBe 0
    range.start.character shouldBe 2 // Start of "HEIGHT"
    range.end.line shouldBe 0
    range.end.character shouldBe 8 // End of "HEIGHT"
  }

  test("Hover on 'OUTPUTS' should return documentation") {
    val documentText = "OUTPUTS(0).value"
    val position = Position(0, 3)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("OUTPUTS")
    result.get.contents should include("Coll[Box]")
  }

  test("Hover on type 'Box' should return documentation") {
    val documentText = "val myBox: Box = SELF"
    val position = Position(0, 12)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("Box")
    result.get.contents should include("UTXO")
  }

  test("Hover on 'getOrElse' should return documentation") {
    val documentText = "SELF.R4[Int].getOrElse(0)"
    val position = Position(0, 15)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("getOrElse")
    result.get.contents should include("default value")
  }

  // Tests for user-defined values (type inference)

  test("Hover on user-defined value should show inferred type") {
    val documentText = """val deadline = SELF.R4[Int].get
      |sigmaProp(HEIGHT > deadline)""".stripMargin
    val position = Position(1, 21) // On "deadline" in second line
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("deadline")
    result.get.contents should include("Int")
    result.get.contents should include("User-defined value")
    result.get.contents should include("SELF.R4[Int].get")
  }

  test("Hover on user-defined value with HEIGHT") {
    val documentText = "val currentHeight = HEIGHT"
    val position = Position(0, 5) // On "currentHeight"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("currentHeight")
    result.get.contents should include("Int")
    result.get.contents should include("HEIGHT")
  }

  test("Hover on user-defined value with box property") {
    val documentText = "val boxValue = SELF.value"
    val position = Position(0, 5) // On "boxValue"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("boxValue")
    result.get.contents should include("Long")
    result.get.contents should include("SELF.value")
  }

  test("Hover on user-defined boolean expression") {
    val documentText = """val deadline = 100
      |val condition = HEIGHT > deadline""".stripMargin
    val position = Position(1, 5) // On "condition"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("condition")
    result.get.contents should include("Boolean")
  }

  test("Hover on user-defined sigmaProp call") {
    val documentText = "val prop = sigmaProp(true)"
    val position = Position(0, 5) // On "prop"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("prop")
    result.get.contents should include("SigmaProp")
  }

  test("Hover on user-defined value referencing another user value") {
    val documentText = """val boxValue = SELF.value
      |val amount = boxValue""".stripMargin
    val position = Position(1, 5) // On "amount"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("amount")
    result.get.contents should include(
      "Long"
    ) // Should resolve through boxValue
  }

  test("Hover on user-defined value in complex document") {
    val documentText = """{
      |  val deadline = SELF.R4[Int].get
      |  val currentHeight = HEIGHT
      |  val condition = currentHeight > deadline
      |  sigmaProp(condition)
      |}""".stripMargin

    // Hover on "currentHeight"
    val position = Position(2, 7)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("currentHeight")
    result.get.contents should include("Int")
  }

  test("Hover on user-defined value with literal") {
    val documentText = "val count = 42"
    val position = Position(0, 5) // On "count"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("count")
    result.get.contents should include("Int")
    result.get.contents should include("42")
  }

  test("Hover on user-defined value with long literal") {
    val documentText = "val nanoErgs = 1000000000L"
    val position = Position(0, 5) // On "nanoErgs"
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("nanoErgs")
    result.get.contents should include("Long")
  }

  test("Hover on undefined user value returns None") {
    val documentText = "val x = unknownFunction()"
    val position =
      Position(0, 4) // On "x" (val starts at 0, space at 3, x at 4)
    val result = provider.hover(documentText, position)

    // Should return None because type cannot be inferred
    result shouldBe None
  }

  test("Built-in symbols take precedence over user-defined") {
    // Even if someone defines "val HEIGHT = 5", hovering on HEIGHT in that
    // line should show the built-in HEIGHT documentation
    val documentText = "val myHeight = HEIGHT"
    val position = Position(0, 16) // On "HEIGHT" (the built-in)
    val result = provider.hover(documentText, position)

    result shouldBe defined
    result.get.contents should include("HEIGHT")
    result.get.contents should include("blockchain height")
    // Should show built-in docs, not user-defined
    result.get.contents should not include "User-defined"
  }
}
