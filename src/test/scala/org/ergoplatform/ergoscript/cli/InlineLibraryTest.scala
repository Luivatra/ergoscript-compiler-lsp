package org.ergoplatform.ergoscript.cli

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InlineLibraryTest extends AnyFlatSpec with Matchers {

  "Compiler" should "inline library functions into template body" in {
    val script = """def checkHeight(height: Int): Boolean = {
  HEIGHT > height
}

/*
 * Sample ErgoScript contract
 * @param minHeight Minimum blockchain height
 */
@contract def heightLock(minHeight: Int = 100) = {
  HEIGHT > minHeight
}"""

    println("Testing script with library function before @contract")
    val result = Compiler.compile(script, "test", "test")

    result match {
      case Right(r) =>
        println("Compilation SUCCESS")
        println(s"Template: ${r.template}")
      case Left(e) =>
        println(s"Compilation FAILED: ${e.message}")
        println(s"Line: ${e.line}, Column: ${e.column}")
        fail(s"Compilation should succeed: ${e.message}")
    }

    result.isRight shouldBe true
  }
}
