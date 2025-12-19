package org.ergoplatform.ergoscript.lsp.hover

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TypeInferenceSpec extends AnyFunSuite with Matchers {

  test("Extract user-defined symbols from document") {
    val document = """
      |val deadline = 100
      |val height = HEIGHT
      |val boxValue = SELF.value
    """.stripMargin

    val symbols = TypeInference.extractUserDefinedSymbols(document)

    symbols should have size 3
    symbols should contain key "deadline"
    symbols should contain key "height"
    symbols should contain key "boxValue"

    symbols("deadline").expression shouldBe "100"
    symbols("height").expression shouldBe "HEIGHT"
    symbols("boxValue").expression shouldBe "SELF.value"
  }

  test("Infer type for integer literal") {
    val result = TypeInference.inferType("100")
    result shouldBe Some("Int")
  }

  test("Infer type for long literal") {
    val result = TypeInference.inferType("1000000L")
    result shouldBe Some("Long")
  }

  test("Infer type for boolean literal") {
    TypeInference.inferType("true") shouldBe Some("Boolean")
    TypeInference.inferType("false") shouldBe Some("Boolean")
  }

  test("Infer type for HEIGHT") {
    val result = TypeInference.inferType("HEIGHT")
    result shouldBe Some("Int")
  }

  test("Infer type for SELF") {
    val result = TypeInference.inferType("SELF")
    result shouldBe Some("Box")
  }

  test("Infer type for SELF.value") {
    val result = TypeInference.inferType("SELF.value")
    result shouldBe Some("Long")
  }

  test("Infer type for OUTPUTS(0)") {
    val result = TypeInference.inferType("OUTPUTS(0)")
    result shouldBe Some("Box")
  }

  test("Infer type for OUTPUTS") {
    val result = TypeInference.inferType("OUTPUTS")
    result shouldBe Some("Coll[Box]")
  }

  test("Infer type for INPUTS") {
    val result = TypeInference.inferType("INPUTS")
    result shouldBe Some("Coll[Box]")
  }

  test("Infer type for register access with .get") {
    val result = TypeInference.inferType("SELF.R4[Int].get")
    result shouldBe Some("Int")
  }

  test("Infer type for register access without .get") {
    val result = TypeInference.inferType("SELF.R4[Int]")
    result shouldBe Some("Option[Int]")
  }

  test("Infer type for register with Long") {
    val result = TypeInference.inferType("SELF.R5[Long].get")
    result shouldBe Some("Long")
  }

  test("Infer type for register with Coll[Byte]") {
    val result = TypeInference.inferType("SELF.R6[Coll[Byte]].get")
    result shouldBe Some("Coll[Byte]")
  }

  test("Infer type for box properties") {
    TypeInference.inferType("SELF.propositionBytes") shouldBe Some("Coll[Byte]")
    TypeInference.inferType("SELF.bytes") shouldBe Some("Coll[Byte]")
    TypeInference.inferType("SELF.id") shouldBe Some("Coll[Byte]")
    TypeInference.inferType("SELF.tokens") shouldBe Some(
      "Coll[(Coll[Byte], Long)]"
    )
    TypeInference.inferType("SELF.creationInfo") shouldBe Some(
      "(Int, Coll[Byte])"
    )
  }

  test("Infer type for sigmaProp function") {
    val result = TypeInference.inferType("sigmaProp(HEIGHT > 100)")
    result shouldBe Some("SigmaProp")
  }

  test("Infer type for blake2b256 function") {
    val result = TypeInference.inferType("blake2b256(SELF.propositionBytes)")
    result shouldBe Some("Coll[Byte]")
  }

  test("Infer type for sha256 function") {
    val result = TypeInference.inferType("sha256(data)")
    result shouldBe Some("Coll[Byte]")
  }

  test("Infer type for boolean expressions") {
    TypeInference.inferType("HEIGHT > 100") shouldBe Some("Boolean")
    TypeInference.inferType("SELF.value >= 1000") shouldBe Some("Boolean")
    TypeInference.inferType("HEIGHT > 100 && SELF.value > 0") shouldBe Some(
      "Boolean"
    )
    TypeInference.inferType("true && false") shouldBe Some("Boolean")
  }

  test("Infer type for comparison expressions") {
    TypeInference.inferType("x == y") shouldBe Some("Boolean")
    TypeInference.inferType("a != b") shouldBe Some("Boolean")
  }

  test("Infer type for collection size") {
    val result = TypeInference.inferType("OUTPUTS.size")
    result shouldBe Some("Int")
  }

  test("Infer type for collection isEmpty") {
    val result = TypeInference.inferType("OUTPUTS.isEmpty")
    result shouldBe Some("Boolean")
  }

  test("Infer type for collection exists") {
    val result = TypeInference.inferType("OUTPUTS.exists(box => box.value > 0)")
    result shouldBe Some("Boolean")
  }

  test("Infer type for collection forall") {
    val result = TypeInference.inferType("OUTPUTS.forall(box => box.value > 0)")
    result shouldBe Some("Boolean")
  }

  test("Infer type for Option isDefined") {
    val result = TypeInference.inferType("SELF.R4[Int].isDefined")
    result shouldBe Some("Boolean")
  }

  test("Infer type for proveDlog function") {
    val result = TypeInference.inferType("proveDlog(pk)")
    result shouldBe Some("SigmaProp")
  }

  test("Infer type for atLeast function") {
    val result = TypeInference.inferType("atLeast(2, props)")
    result shouldBe Some("SigmaProp")
  }

  test("Infer type for allOf function") {
    val result = TypeInference.inferType("allOf(Coll(true, false))")
    result shouldBe Some("Boolean")
  }

  test("Infer type for anyOf function") {
    val result = TypeInference.inferType("anyOf(Coll(true, false))")
    result shouldBe Some("Boolean")
  }

  test("Infer type with known symbols") {
    val symbols = Map(
      "deadline" -> TypeInference.UserSymbol("deadline", "SELF.R4[Int].get", 0),
      "currentHeight" -> TypeInference.UserSymbol("currentHeight", "HEIGHT", 1)
    )

    TypeInference.inferType("deadline", symbols) shouldBe Some("Int")
    TypeInference.inferType("currentHeight", symbols) shouldBe Some("Int")
  }

  test("Infer type for nested symbol reference") {
    val symbols = Map(
      "boxValue" -> TypeInference.UserSymbol("boxValue", "SELF.value", 0),
      "amount" -> TypeInference.UserSymbol("amount", "boxValue", 1)
    )

    TypeInference.inferType("amount", symbols) shouldBe Some("Long")
  }

  test("Return None for unknown expression") {
    val result = TypeInference.inferType("unknownFunction()")
    result shouldBe None
  }

  test("Create hover info for user symbol") {
    val symbol = TypeInference.UserSymbol("deadline", "SELF.R4[Int].get", 5)
    val hoverInfo = TypeInference.createUserSymbolHoverInfo(symbol, "Int")

    hoverInfo.signature shouldBe Some("val deadline: Int")
    hoverInfo.description should include("User-defined value")
    hoverInfo.description should include("line 6")
    hoverInfo.description should include("Int")
    hoverInfo.description should include("SELF.R4[Int].get")
    hoverInfo.category shouldBe Some("Variable")
  }

  test("Extract symbols with correct line numbers") {
    val document = """val first = 1
      |val second = 2
      |val third = 3""".stripMargin

    val symbols = TypeInference.extractUserDefinedSymbols(document)

    symbols("first").lineNumber shouldBe 0
    symbols("second").lineNumber shouldBe 1
    symbols("third").lineNumber shouldBe 2
  }

  test("Handle complex expressions") {
    TypeInference.inferType("OUTPUTS(0).value") shouldBe Some("Long")
    TypeInference.inferType("INPUTS(1).tokens") shouldBe Some(
      "Coll[(Coll[Byte], Long)]"
    )
  }

  test("Infer type for getOrElse") {
    val result = TypeInference.inferType("SELF.R4[Int].getOrElse(0)")
    result shouldBe Some("Int")
  }

  test("Handle symbols in blocks") {
    val document = """{
      |  val deadline = SELF.R4[Int].get
      |  val condition = HEIGHT > deadline
      |  sigmaProp(condition)
      |}""".stripMargin

    val symbols = TypeInference.extractUserDefinedSymbols(document)

    symbols should contain key "deadline"
    symbols should contain key "condition"

    TypeInference.inferType("deadline", symbols) shouldBe Some("Int")
    TypeInference.inferType("condition", symbols) shouldBe Some("Boolean")
  }

  // Collection method type inference tests
  test("Infer type for filter on collection") {
    val result =
      TypeInference.inferType("Coll(100, 1000).filter({(i: Int) => i > 500})")
    result shouldBe Some("Coll[Int]")
  }

  test("Infer type for filter with curly braces") {
    val result =
      TypeInference.inferType("OUTPUTS.filter { box => box.value > 1000 }")
    result shouldBe Some("Coll[Box]")
  }

  test("Infer type for map on collection") {
    val result = TypeInference.inferType("OUTPUTS.map { box => box.value }")
    result shouldBe Some("Coll[T]")
  }

  test("Infer type for flatMap") {
    val result =
      TypeInference.inferType("OUTPUTS.flatMap { box => box.tokens }")
    result shouldBe Some("Coll[T]")
  }

  test("Infer type for fold") {
    val result = TypeInference.inferType(
      "OUTPUTS.fold(0L) { (acc, box) => acc + box.value }"
    )
    result shouldBe Some("T")
  }

  test("Infer type for zip") {
    val result = TypeInference.inferType("OUTPUTS.zip(INPUTS)")
    result shouldBe Some("Coll[(Box, T)]")
  }

  test("Infer type for chained collection methods") {
    val result = TypeInference.inferType(
      "OUTPUTS.filter { box => box.value > 1000 }.map { box => box.value }"
    )
    // After filter we have Coll[Box], then map returns Coll[T]
    result shouldBe Some("Coll[T]")
  }

  test("Infer type for exists (returns Boolean)") {
    val result =
      TypeInference.inferType("OUTPUTS.exists { box => box.value > 1000 }")
    result shouldBe Some("Boolean")
  }

  test("Infer type for forall (returns Boolean)") {
    val result =
      TypeInference.inferType("OUTPUTS.forall { box => box.value > 0 }")
    result shouldBe Some("Boolean")
  }
}
