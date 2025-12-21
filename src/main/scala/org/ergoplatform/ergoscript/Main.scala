package org.ergoplatform.ergoscript

import org.ergoplatform.ergoscript.cli.CliApp

object Main {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printUsage()
      sys.exit(1)
    }

    args.head match {
      case "compile" | "test" | "init" | "validate" =>
        // Use new Commands interface with full feature support
        org.ergoplatform.ergoscript.cli.Commands.main(args)

      case "lsp" | "server" =>
        // Use simple JSON-RPC implementation to avoid lsp4j annotation issues
        val server =
          new org.ergoplatform.ergoscript.lsp.jsonrpc.SimpleLspServer(
            System.in,
            System.out
          )
        server.start()

      case "--help" | "-h" =>
        printUsage()
        sys.exit(0)

      case "--version" | "-v" =>
        printVersion()
        sys.exit(0)

      case unknown =>
        System.err.println(s"Unknown command: $unknown")
        printUsage()
        sys.exit(1)
    }
  }

  private def printUsage(): Unit = {
    println("""ErgoScript Compiler and Language Server
        |
        |Usage:
        |  ergoscript-compiler <command> [options]
        |
        |Commands:
        |  compile                                  Compile ErgoScript to EIP-5 JSON
        |  test                                     Run ErgoScript tests
        |  init                                     Initialize a new ErgoScript project
        |  validate                                 Validate project configuration
        |  lsp                                      Start Language Server (LSP mode)
        |  server                                   Start Language Server (alias for lsp)
        |  --help                                   Show this help message
        |  --version                                Show version information
        |
        |Compile Command:
        |  ergoscript-compiler compile [options]
        |  Options:
        |    -i, --input <file>                     Input ErgoScript file path
        |    -s, --script <code>                    Inline ErgoScript code
        |    -o, --output <file>                    Output JSON file (optional, defaults to stdout)
        |    -n, --name <name>                      Contract name (default: UnnamedContract)
        |    -d, --description <text>               Contract description
        |    --network <mainnet|testnet>            Network type (default: mainnet)
        |    --tree-version <byte>                  ErgoTree version
        |
        |Test Command:
        |  ergoscript-compiler test [files...] [options]
        |  Options:
        |    --verbose, -v                          Show detailed output
        |    --filter <pattern>                     Filter tests by name pattern
        |    --network <mainnet|testnet>            Network type (default: mainnet)
        |
        |Init Command:
        |  ergoscript-compiler init [options]
        |  Options:
        |    --name <project-name>                  Project name
        |    --description <text>                   Project description
        |
        |Examples:
        |  # Compile from file
        |  ergoscript-compiler compile -i contract.es -o contract.json -n "MyContract"
        |
        |  # Run tests
        |  ergoscript-compiler test tests/main.test.es --verbose
        |
        |  # Initialize a new project
        |  ergoscript-compiler init --name my-project
        |
        |  # Start LSP server
        |  ergoscript-compiler lsp
        |
        |For more information, visit: https://github.com/ergoplatform/ergoscript-compiler-lsp
        |""".stripMargin)
  }

  private def printVersion(): Unit = {
    println("ErgoScript Compiler LSP v0.1.0")
    println("Built with Scala 2.13.16")
    println("sigmastate-interpreter: 6.0.2")
  }
}
