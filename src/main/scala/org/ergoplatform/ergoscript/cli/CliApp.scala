package org.ergoplatform.ergoscript.cli

import org.ergoplatform.ergoscript.eip5.JsonSerializer
import scopt.OParser
import java.io.{File, PrintWriter}
import scala.util.{Try, Success, Failure}

case class CliConfig(
    input: Option[String] = None,
    output: Option[String] = None,
    name: String = "UnnamedContract",
    description: String = "",
    network: String = "mainnet",
    treeVersion: Byte = sigma.ast.ErgoTree.VersionFlag,
    script: Option[String] = None
)

object CliApp {

  private val builder = OParser.builder[CliConfig]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("ergoscript-compiler"),
      head("ErgoScript Compiler", "0.1.0"),
      opt[String]('i', "input")
        .action((x, c) => c.copy(input = Some(x)))
        .text("Input ErgoScript file path"),
      opt[String]('s', "script")
        .action((x, c) => c.copy(script = Some(x)))
        .text("Inline ErgoScript code"),
      opt[String]('o', "output")
        .action((x, c) => c.copy(output = Some(x)))
        .text("Output JSON file path (optional, defaults to stdout)"),
      opt[String]('n', "name")
        .action((x, c) => c.copy(name = x))
        .text("Contract name for EIP-5 metadata (default: UnnamedContract)"),
      opt[String]('d', "description")
        .action((x, c) => c.copy(description = x))
        .text("Contract description (default: empty)"),
      opt[String]("network")
        .action((x, c) => c.copy(network = x))
        .validate(x =>
          if (Seq("mainnet", "testnet").contains(x.toLowerCase)) success
          else failure("Network must be either 'mainnet' or 'testnet'")
        )
        .text("Network type: mainnet or testnet (default: mainnet)"),
      opt[Int]("tree-version")
        .action((x, c) => c.copy(treeVersion = x.toByte))
        .text(s"ErgoTree version (default: ${sigma.ast.ErgoTree.VersionFlag})"),
      help("help").text("Show this help message"),
      checkConfig { c =>
        if (c.input.isEmpty && c.script.isEmpty) {
          failure("Either --input or --script must be specified")
        } else if (c.input.isDefined && c.script.isDefined) {
          failure("Only one of --input or --script can be specified")
        } else {
          success
        }
      }
    )
  }

  def run(args: Array[String]): Unit = {
    OParser.parse(parser, args, CliConfig()) match {
      case Some(config) =>
        processCompilation(config) match {
          case Success(_) =>
            System.exit(0)
          case Failure(ex) =>
            System.err.println(s"Error: ${ex.getMessage}")
            System.exit(1)
        }
      case None =>
        // Error message already printed by scopt
        System.exit(1)
    }
  }

  private def processCompilation(config: CliConfig): Try[Unit] = Try {
    val networkPrefix: Byte = config.network.toLowerCase match {
      case "mainnet" => 0x00.toByte
      case "testnet" => 0x10.toByte
      case _         => 0x00.toByte
    }

    val result = config.input match {
      case Some(filePath) =>
        Compiler.compileFromFile(
          filePath,
          config.name,
          config.description,
          networkPrefix
        )
      case None =>
        config.script match {
          case Some(script) =>
            Compiler.compile(
              script,
              config.name,
              config.description,
              networkPrefix
            )
          case None =>
            throw new IllegalStateException("No input provided")
        }
    }

    result match {
      case Right(compilationResult) =>
        val json = compilationResult.template match {
          case Some(template) => JsonSerializer.toJson(template)
          case None           => "{}" // Fallback for non-template compilation
        }

        config.output match {
          case Some(outputPath) =>
            val writer = new PrintWriter(new File(outputPath))
            try {
              writer.write(json)
              System.err.println(s"Successfully compiled to $outputPath")
            } finally {
              writer.close()
            }
          case None =>
            println(json)
        }

      case Left(error) =>
        val errorMsg = error.line match {
          case Some(line) =>
            error.column match {
              case Some(col) =>
                s"Error at line $line, column $col: ${error.message}"
              case None => s"Error at line $line: ${error.message}"
            }
          case None =>
            s"Compilation error: ${error.message}"
        }
        throw new RuntimeException(errorMsg)
    }
  }
}
