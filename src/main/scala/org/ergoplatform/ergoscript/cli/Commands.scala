package org.ergoplatform.ergoscript.cli

import org.ergoplatform.ergoscript.testing.{
  TestRunner,
  TestSuiteResult,
  TraceConfig,
  TraceFormatter
}
import org.ergoplatform.ergoscript.project.ProjectConfigParser
import scopt.OParser
import java.nio.file.{Files, Path, Paths}
import scala.util.{Try, Success, Failure}

/** CLI commands configuration.
  */
sealed trait Command
case class CompileCommand(config: CliConfig) extends Command
case class TestCommand(
    files: List[String] = List.empty,
    verbose: Boolean = false,
    filter: Option[String] = None,
    network: String = "mainnet",
    trace: Boolean = false,
    traceFormat: String = "tree",
    traceOnFailureOnly: Boolean = true
) extends Command
case class InitCommand(
    name: String = "my-ergo-project",
    description: String = "An ErgoScript project",
    template: Option[String] = None
) extends Command
case class ValidateCommand() extends Command

/** Enhanced CLI application with test support.
  */
object Commands {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printUsage()
      System.exit(1)
    }

    val command = args(0).toLowerCase
    val commandArgs = args.drop(1)

    command match {
      case "compile" =>
        // Use existing CliApp for compilation
        CliApp.run(commandArgs)

      case "test" =>
        runTests(commandArgs)

      case "init" =>
        runInit(commandArgs)

      case "validate" =>
        runValidate()

      case "help" | "--help" | "-h" =>
        printUsage()
        System.exit(0)

      case _ =>
        System.err.println(s"Unknown command: $command")
        printUsage()
        System.exit(1)
    }
  }

  private def printUsage(): Unit = {
    println("""
      |ErgoScript Compiler & Test Framework
      |
      |Usage: ergoscript <command> [options]
      |
      |Commands:
      |  compile     Compile ErgoScript contracts
      |  test        Run ErgoScript tests
      |  init        Initialize a new ErgoScript project
      |  validate    Validate project configuration
      |  help        Show this help message
      |
      |Compile Command:
      |  ergoscript compile -i <file> [-o <output>] [-n <name>] [--network mainnet|testnet]
      |
      |Test Command:
      |  ergoscript test [files...] [--verbose] [--filter <pattern>] [--network mainnet|testnet]
      |                  [--trace] [--trace-format tree|json|compact] [--trace-all]
      |
      |Init Command:
      |  ergoscript init [--name <project-name>] [--description <desc>]
      |
      |Examples:
      |  ergoscript compile -i src/main.es -o build/main.json
      |  ergoscript test tests/main.test.es --verbose
      |  ergoscript test --filter "testHeight*"
      |  ergoscript test --trace                    # Show trace on failed tests
      |  ergoscript test --trace --trace-all        # Show trace on all tests
      |  ergoscript test --trace --trace-format json
      |  ergoscript init --name my-project
      |
      """.stripMargin)
  }

  private def runTests(args: Array[String]): Unit = {
    val testConfig = parseTestArgs(args)

    val networkPrefix: Byte = testConfig.network.toLowerCase match {
      case "mainnet" => 0x00.toByte
      case "testnet" => 0x10.toByte
      case _         => 0x00.toByte
    }

    // Determine test files first
    val testFiles = if (testConfig.files.nonEmpty) {
      testConfig.files.map(Paths.get(_))
    } else {
      // Find workspace root from current directory for auto-discovery
      val workspaceRoot = findWorkspaceRoot()

      // Find all .test.es files in tests/ directory
      workspaceRoot match {
        case Some(root) =>
          val testsDir = Paths.get(root, "tests")
          if (Files.exists(testsDir) && Files.isDirectory(testsDir)) {
            findTestFiles(testsDir)
          } else {
            println(
              "No tests directory found. Looking for *.test.es files in current directory..."
            )
            findTestFiles(Paths.get("."))
          }
        case None =>
          println(
            "No project root found. Looking for *.test.es files in current directory..."
          )
          findTestFiles(Paths.get("."))
      }
    }

    if (testFiles.isEmpty) {
      println("No test files found.")
      System.exit(0)
    }

    println(s"Running ${testFiles.length} test file(s)...\n")

    // Find workspace root from the first test file's location
    // This ensures imports are resolved correctly when running with absolute paths
    import org.ergoplatform.ergoscript.lsp.imports.ImportResolver
    val workspaceRoot = testFiles.headOption
      .flatMap { testFile =>
        val absolutePath = testFile.toAbsolutePath
        ImportResolver.findProjectRoot(absolutePath).map(_.toString)
      }
      .orElse(findWorkspaceRoot())

    // Create trace config
    val traceConfig = TraceConfig(
      enabled = testConfig.trace,
      format = testConfig.traceFormat,
      onFailureOnly = testConfig.traceOnFailureOnly
    )

    // Run tests
    val runner = new TestRunner()
    val results =
      runner.runTestFiles(
        testFiles.toList,
        workspaceRoot,
        networkPrefix,
        traceConfig
      )

    // Print results
    printTestResults(
      results,
      testConfig.verbose,
      testConfig.trace,
      testConfig.traceFormat
    )

    // Exit with appropriate code
    val allPassed = results.forall(_.failed == 0)
    System.exit(if (allPassed) 0 else 1)
  }

  private def parseTestArgs(args: Array[String]): TestCommand = {
    var files = List.empty[String]
    var verbose = false
    var filter: Option[String] = None
    var network = "mainnet"
    var trace = false
    var traceFormat = "tree"
    var traceOnFailureOnly = true

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--verbose" | "-v" =>
          verbose = true
        case "--filter" | "-f" =>
          if (i + 1 < args.length) {
            filter = Some(args(i + 1))
            i += 1
          }
        case "--network" =>
          if (i + 1 < args.length) {
            network = args(i + 1)
            i += 1
          }
        case "--trace" =>
          trace = true
        case "--trace-format" =>
          if (i + 1 < args.length) {
            traceFormat = args(i + 1).toLowerCase
            if (!Set("tree", "json", "compact").contains(traceFormat)) {
              System.err.println(
                s"Unknown trace format: $traceFormat. Using 'tree'."
              )
              traceFormat = "tree"
            }
            i += 1
          }
        case "--trace-all" =>
          traceOnFailureOnly = false
        case arg if !arg.startsWith("-") =>
          files = files :+ arg
        case _ =>
          System.err.println(s"Unknown option: ${args(i)}")
      }
      i += 1
    }

    TestCommand(
      files,
      verbose,
      filter,
      network,
      trace,
      traceFormat,
      traceOnFailureOnly
    )
  }

  private def findWorkspaceRoot(): Option[String] = {
    import org.ergoplatform.ergoscript.lsp.imports.ImportResolver
    val currentPath = Paths.get(".").toAbsolutePath
    ImportResolver.findProjectRoot(currentPath).map(_.toString)
  }

  private def findTestFiles(dir: Path): List[Path] = {
    import scala.jdk.CollectionConverters._

    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
      return List.empty
    }

    Files
      .walk(dir)
      .iterator()
      .asScala
      .filter(p =>
        Files.isRegularFile(p) && p.getFileName.toString.endsWith(".test.es")
      )
      .toList
  }

  private def printTestResults(
      results: List[TestSuiteResult],
      verbose: Boolean,
      showTrace: Boolean = false,
      traceFormat: String = "tree"
  ): Unit = {
    results.foreach { suite =>
      println(s"${suite.file}:")

      suite.tests.foreach { test =>
        val status = if (test.passed) "✓" else "✗"
        val duration = f"${test.duration}ms"

        println(s"  $status ${test.name} ($duration)")

        // Show assertions if verbose, test failed, or we have traces to show
        val shouldShowAssertions =
          verbose || !test.passed || (showTrace && test.assertions.exists(
            _.trace.isDefined
          ))

        if (shouldShowAssertions) {
          test.assertions.foreach { assertion =>
            println(s"    ${assertion.message}")

            // Show trace if available and tracing is enabled
            if (showTrace && assertion.trace.isDefined) {
              val trace = assertion.trace.get
              println(
                s"    Cost: ${trace.totalCost}, Operations: ${trace.operationCount}"
              )
              println()
              println("    Evaluation Trace:")

              val formattedTrace = traceFormat.toLowerCase match {
                case "json"    => TraceFormatter.formatAsJson(trace.rootTrace)
                case "compact" => TraceFormatter.formatCompact(trace.rootTrace)
                case _         => TraceFormatter.formatAsTree(trace.rootTrace)
              }

              // Indent the trace output
              formattedTrace.split("\n").foreach { line =>
                println(s"      $line")
              }
              println()
            }
          }
        }

        test.error.foreach { err =>
          println(s"    Error: $err")
        }
      }

      println(
        s"  ${suite.passed} passed, ${suite.failed} failed (${suite.duration}ms)\n"
      )
    }

    val totalPassed = results.map(_.passed).sum
    val totalFailed = results.map(_.failed).sum
    val totalDuration = results.map(_.duration).sum

    println(
      s"Total: $totalPassed passed, $totalFailed failed (${totalDuration}ms)"
    )
  }

  private def runInit(args: Array[String]): Unit = {
    var name = "my-ergo-project"
    var description = "An ErgoScript project"
    var template: Option[String] = None

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--name" =>
          if (i + 1 < args.length) {
            name = args(i + 1)
            i += 1
          }
        case "--description" =>
          if (i + 1 < args.length) {
            description = args(i + 1)
            i += 1
          }
        case "--template" =>
          if (i + 1 < args.length) {
            template = Some(args(i + 1))
            i += 1
          }
        case _ =>
          System.err.println(s"Unknown option: ${args(i)}")
      }
      i += 1
    }

    // Create project structure
    createProjectStructure(name, description, template)
  }

  private def createProjectStructure(
      name: String,
      description: String,
      template: Option[String]
  ): Unit = {
    import java.io.PrintWriter

    println(s"Initializing ErgoScript project: $name")

    // Create directories
    val dirs = List("src", "lib", "tests", "build")
    dirs.foreach { dir =>
      val path = Paths.get(dir)
      if (!Files.exists(path)) {
        Files.createDirectories(path)
        println(s"  Created $dir/")
      }
    }

    // Create ergo.json
    val config = ProjectConfigParser.defaultConfig(name, description)
    val configJson = ProjectConfigParser.toJson(config, pretty = true)

    val configPath = Paths.get("ergo.json")
    if (!Files.exists(configPath)) {
      val writer = new PrintWriter(configPath.toFile)
      try {
        writer.write(configJson)
        println("  Created ergo.json")
      } finally {
        writer.close()
      }
    } else {
      println("  ergo.json already exists, skipping")
    }

    // Create sample contract
    val sampleContract = """// Sample ErgoScript contract
// Simple height-lock contract that checks if current height is above 100
{
  HEIGHT > 100
}
"""

    val contractPath = Paths.get("src/main.es")
    if (!Files.exists(contractPath)) {
      val writer = new PrintWriter(contractPath.toFile)
      try {
        writer.write(sampleContract)
        println("  Created src/main.es")
      } finally {
        writer.close()
      }
    }

    // Create sample test
    val sampleTest = """#import src:main.es;

@test def testHeightAbove100Passes() = {
  @context {
    HEIGHT = 150
    SELF = Box { value = 1000000L }
    INPUTS = [SELF]
    OUTPUTS = [Box { value = 900000L }]
  }

  @assert true == true
}

@test def testHeightBelow100Fails() = {
  @context {
    HEIGHT = 50
    SELF = Box { value = 1000000L }
    INPUTS = [SELF]
    OUTPUTS = [Box { value = 900000L }]
  }

  @assert true == true
}
"""

    val testPath = Paths.get("tests/main.test.es")
    if (!Files.exists(testPath)) {
      val writer = new PrintWriter(testPath.toFile)
      try {
        writer.write(sampleTest)
        println("  Created tests/main.test.es")
      } finally {
        writer.close()
      }
    }

    println("\nProject initialized successfully!")
    println("\nNext steps:")
    println("  1. Edit src/main.es to define your contract")
    println("  2. Edit tests/main.test.es to test your contract")
    println("  3. Run tests: ergoscript test")
    println(
      "  4. Compile: ergoscript compile -i src/main.es -o build/main.json"
    )
  }

  private def runValidate(): Unit = {
    val workspaceRoot = findWorkspaceRoot()

    workspaceRoot match {
      case Some(root) =>
        val configPath = Paths.get(root, "ergo.json")
        if (Files.exists(configPath)) {
          ProjectConfigParser.parseFile(configPath) match {
            case Right(config) =>
              println("✓ Project configuration is valid")

              // Validate constants
              import org.ergoplatform.ergoscript.project.ConstantTypes
              val errors = ConstantTypes.validateConstants(config)
              if (errors.nonEmpty) {
                println("\nConstant validation errors:")
                errors.foreach(err => println(s"  ✗ $err"))
                System.exit(1)
              } else {
                println("✓ All constants are valid")
              }

            case Left(error) =>
              println(s"✗ Invalid project configuration: $error")
              System.exit(1)
          }
        } else {
          println("✗ No ergo.json found in project root")
          System.exit(1)
        }
      case None =>
        println("✗ No project root found")
        System.exit(1)
    }
  }
}
