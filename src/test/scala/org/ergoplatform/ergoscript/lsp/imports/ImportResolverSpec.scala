package org.ergoplatform.ergoscript.lsp.imports

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Paths}

class ImportResolverSpec extends AnyFlatSpec with Matchers {

  "ImportResolver" should "parse import directives" in {
    val code = """#import lib/utils.es;
      |
      |{
      |  val x = 100
      |}""".stripMargin

    val imports = ImportResolver.parseImports(code)

    imports should have size 1
    imports.head.path shouldBe "lib/utils.es"
    imports.head.line shouldBe 1
  }

  it should "parse multiple import directives" in {
    val code = """#import lib/utils.es;
      |#import lib/helpers.es;
      |
      |{
      |  val x = 100
      |}""".stripMargin

    val imports = ImportResolver.parseImports(code)

    imports should have size 2
    imports.map(_.path) should contain allOf ("lib/utils.es", "lib/helpers.es")
  }

  it should "expand imports correctly" in {
    // Create a temporary file for testing
    val tempDir = Files.createTempDirectory("ergoscript-test")
    val ergoscriptDir = tempDir.resolve("ergoscript")
    Files.createDirectory(ergoscriptDir)

    val libFile = ergoscriptDir.resolve("lib.es")
    Files.writeString(libFile, "def helper(): Boolean = true")

    val code = s"#import lib.es;\n\nval x = helper()"

    val result = ImportResolver.expandImports(
      code,
      None,
      Some(tempDir.toString)
    )

    result.errors shouldBe empty
    result.expandedCode.code should include("def helper(): Boolean = true")
    result.expandedCode.code should include("val x = helper()")
    result.expandedCode.code should not include "#import"

    // Cleanup
    Files.deleteIfExists(libFile)
    Files.deleteIfExists(ergoscriptDir)
    Files.deleteIfExists(tempDir)
  }

  it should "detect circular imports" in {
    val tempDir = Files.createTempDirectory("ergoscript-test")
    val ergoscriptDir = tempDir.resolve("ergoscript")
    Files.createDirectory(ergoscriptDir)

    val file1 = ergoscriptDir.resolve("file1.es")
    val file2 = ergoscriptDir.resolve("file2.es")

    Files.writeString(file1, "#import file2.es;\nval a = 1")
    Files.writeString(file2, "#import file1.es;\nval b = 2")

    val code = Files.readString(file1)

    val result = ImportResolver.expandImports(
      code,
      Some(file1.toString),
      Some(tempDir.toString)
    )

    result.errors should not be empty
    result.errors.head should include("Circular import")

    // Cleanup
    Files.deleteIfExists(file1)
    Files.deleteIfExists(file2)
    Files.deleteIfExists(ergoscriptDir)
    Files.deleteIfExists(tempDir)
  }

  it should "handle missing import files" in {
    val code = "#import nonexistent.es;\nval x = 100"

    val result = ImportResolver.expandImports(code, None, None)

    result.errors should not be empty
    result.errors.head should include("Could not resolve import path")
  }

  it should "get workspace root from URI" in {
    // Create a temporary directory structure with markers
    val tempDir = Files.createTempDirectory("ergoscript-test")
    val ergoscriptDir = tempDir.resolve("ergoscript")
    Files.createDirectory(ergoscriptDir)
    val contractFile = ergoscriptDir.resolve("contract.es")
    Files.writeString(contractFile, "val x = 1")

    val uri = s"file://${contractFile.toString}"
    val workspaceRoot = ImportResolver.getWorkspaceRootFromUri(uri)

    // Should find the directory containing the ergoscript folder
    workspaceRoot shouldBe Some(tempDir.toString)

    // Cleanup
    Files.deleteIfExists(contractFile)
    Files.deleteIfExists(ergoscriptDir)
    Files.deleteIfExists(tempDir)
  }
}
