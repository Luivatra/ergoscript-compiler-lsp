package org.ergoplatform.ergoscript

import org.ergoplatform.ergoscript.cli.CliApp

object Main {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printUsage()
      sys.exit(1)
    }

    args.head match {
      case "compile" =>
        CliApp.run(args.tail)

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
        |  ergoscript-compiler compile [options]    Compile ErgoScript to EIP-5 JSON
        |  ergoscript-compiler lsp                  Start Language Server (LSP mode)
        |  ergoscript-compiler server               Start Language Server (alias for lsp)
        |  ergoscript-compiler --help               Show this help message
        |  ergoscript-compiler --version            Show version information
        |
        |Compile Options:
        |  -i, --input <file>                       Input ErgoScript file path
        |  -s, --script <code>                      Inline ErgoScript code
        |  -o, --output <file>                      Output JSON file (optional, defaults to stdout)
        |  -n, --name <name>                        Contract name (default: UnnamedContract)
        |  -d, --description <text>                 Contract description
        |  --network <mainnet|testnet>              Network type (default: mainnet)
        |  --tree-version <byte>                    ErgoTree version
        |  --help                                   Show compile help
        |
        |Examples:
        |  # Compile from file
        |  ergoscript-compiler compile -i contract.es -o contract.json -n "MyContract"
        |
        |  # Compile inline script
        |  ergoscript-compiler compile -s "sigmaProp(HEIGHT > 100)" -n "SimpleHeight"
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
