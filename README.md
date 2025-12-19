# ErgoScript Compiler and Language Server

A command-line compiler and Language Server Protocol (LSP) implementation for ErgoScript, the smart contract language of the Ergo blockchain.

## Features

### Compiler
- Compile ErgoScript source code to EIP-5 JSON format
- Support for both file input and inline scripts
- Network-specific compilation (mainnet/testnet)
- Detailed error reporting with line and column information

### Language Server (LSP)
- **Custom LSP implementation** built from scratch using Circe (no lsp4j dependencies)
- Full compatibility with Java 17, 21, and 25
- Text document synchronization (open, change, close, save)
- **Real-time diagnostics** with ErgoScript compiler integration ✅
  - Syntax errors with line/column information
  - Type errors and semantic analysis
  - Undefined variable detection
  - Clears errors automatically when code is fixed
- **Code completion** with intelligent context awareness ✅
  - 57+ completion items (keywords, functions, types, constants)
  - Context-aware suggestions (member access, function calls, general)
  - Trigger characters: `.` for members, `(` for functions
  - Snippets with placeholders for quick editing
  - See [COMPLETION.md](COMPLETION.md) for details
- **Hover information** with rich documentation ✅
  - 70+ documented symbols with type signatures
  - Detailed descriptions and usage examples
  - Related symbols for cross-reference
  - Markdown-formatted content
  - See [HOVER.md](HOVER.md) for details
- Go to definition (ready for implementation)
- Find references (ready for implementation)
- Document symbols (ready for implementation)
- Signature help (ready for implementation)

## Installation

### Prerequisites
- Java 17 or higher
- SBT (Scala Build Tool)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/ergoplatform/ergoscript-compiler-lsp.git
cd ergoscript-compiler-lsp

# Build the JAR
sbt assembly

# The executable JAR will be at:
# target/scala-2.13/ergoscript-compiler-lsp.jar
```

## Usage

### Command-Line Compiler

```bash
# Compile from file
java -jar ergoscript-compiler-lsp.jar compile -i contract.es -o contract.json -n "MyContract"

# Compile inline script
java -jar ergoscript-compiler-lsp.jar compile -s "sigmaProp(HEIGHT > 100)" -n "SimpleHeight"

# Show help
java -jar ergoscript-compiler-lsp.jar compile --help
```

#### Compiler Options

- `-i, --input <file>` - Input ErgoScript file path
- `-s, --script <code>` - Inline ErgoScript code
- `-o, --output <file>` - Output JSON file (optional, defaults to stdout)
- `-n, --name <name>` - Contract name (default: UnnamedContract)
- `-d, --description <text>` - Contract description
- `--network <mainnet|testnet>` - Network type (default: mainnet)
- `--tree-version <byte>` - ErgoTree version

### Language Server

Start the LSP server:

```bash
java -jar ergoscript-compiler-lsp.jar lsp
# or
java -jar ergoscript-compiler-lsp.jar server
```

The server communicates via stdio using the Language Server Protocol.

## Editor Integration

### Visual Studio Code

Create or update your workspace/user `settings.json`:

```json
{
  "ergoscript.lsp.command": "java",
  "ergoscript.lsp.args": [
    "-jar",
    "/path/to/ergoscript-compiler-lsp.jar",
    "lsp"
  ],
  "files.associations": {
    "*.es": "ergoscript",
    "*.ergo": "ergoscript"
  }
}
```

See `examples/vscode-settings.json` for a complete example.

### Neovim

Add to your Neovim configuration (e.g., `~/.config/nvim/lua/lsp/ergoscript.lua`):

```lua
local configs = require('lspconfig.configs')

if not configs.ergoscript then
  configs.ergoscript = {
    default_config = {
      cmd = {'java', '-jar', '/path/to/ergoscript-compiler-lsp.jar', 'lsp'},
      filetypes = {'ergoscript', 'ergo'},
      root_dir = require('lspconfig.util').root_pattern('.git', 'ergo.json'),
      single_file_support = true,
    },
  }
end

require('lspconfig').ergoscript.setup{}
```

See `examples/neovim-config.lua` for a complete configuration with key mappings.

### Emacs

Add to your Emacs configuration:

```elisp
(use-package lsp-mode
  :config
  (lsp-register-client
   (make-lsp-client
    :new-connection (lsp-stdio-connection
                     '("java" "-jar" "/path/to/ergoscript-compiler-lsp.jar" "lsp"))
    :major-modes '(ergoscript-mode)
    :server-id 'ergoscript-lsp)))

(define-derived-mode ergoscript-mode prog-mode "ErgoScript"
  "Major mode for editing ErgoScript files.")

(add-to-list 'auto-mode-alist '("\\.es\\'" . ergoscript-mode))
```

See `examples/emacs-config.el` for a complete configuration.

## Architecture

### Custom LSP Implementation

This project implements the Language Server Protocol **from scratch** without using lsp4j, avoiding Scala/Java annotation compatibility issues.

```
Client (Editor)
    |
    | JSON-RPC over stdio
    | (Content-Length headers + JSON)
    |
    v
SimpleLspServer
    |
    +-- JsonRpcProtocol (JSON-RPC 2.0 parsing/serialization)
    |
    +-- LspMessages (LSP message type definitions)
    |
    +-- Request handlers:
        |-- initialize()
        |-- textDocument/didOpen
        |-- textDocument/didChange
        |-- textDocument/completion
        |-- textDocument/hover
        |-- shutdown()
        |-- exit()
```

### Components

- **JsonRpcProtocol.scala** - Pure Scala JSON-RPC 2.0 protocol implementation
  - Message parsing and serialization
  - Content-Length header handling
  - Request/Response/Notification types
  - Error handling

- **LspMessages.scala** - LSP message type definitions
  - Scala case classes for all LSP protocol messages
  - Circe auto-derived JSON codecs
  - Type-safe message handling

- **SimpleLspServer.scala** - Main LSP server
  - Lifecycle management (initialize, shutdown, exit)
  - Document synchronization
  - Feature implementations (completion, hover, etc.)
  - Diagnostic publishing

### Dependencies

- **Scala** 2.13.16
- **sigmastate-interpreter** 6.0.2 (ErgoScript compiler)
- **Circe** 0.14.6 (JSON serialization)
- **scopt** 4.1.0 (CLI argument parsing)
- **Logback** 1.4.11 (Logging)

## Testing

Run the test script:

```bash
./test-lsp.sh
```

This tests:
1. LSP initialize request/response
2. Version command
3. Help command

## Development

### Project Structure

```
src/main/scala/org/ergoplatform/ergoscript/
├── Main.scala                    # Entry point
├── cli/
│   ├── CliApp.scala             # CLI argument handling
│   └── Compiler.scala           # Compilation logic
├── eip5/
│   ├── ContractTemplate.scala   # EIP-5 data structures
│   └── JsonSerializer.scala     # JSON serialization
└── lsp/
    └── jsonrpc/
        ├── JsonRpcProtocol.scala  # JSON-RPC 2.0 implementation
        ├── LspMessages.scala       # LSP message types
        └── SimpleLspServer.scala   # LSP server implementation
```

### Building and Running

```bash
# Compile
sbt compile

# Run tests
sbt test

# Build JAR
sbt assembly

# Run compiler
sbt "run compile -s 'sigmaProp(true)'"

# Run LSP server
sbt "run lsp"
```

### Logging

All logs are directed to **stderr** and the log file `ergoscript-lsp.log` to avoid corrupting the LSP protocol on stdout.

Configure logging in `src/main/resources/logback.xml`.

## Troubleshooting

### Server won't start

Check Java version:
```bash
java -version
# Should be 17 or higher
```

### No diagnostics showing in editor

1. Check server is running and connected
2. Verify editor LSP client configuration
3. Check logs in `ergoscript-lsp.log`
4. Try opening/saving the file to trigger diagnostics
5. Enable DEBUG logging in `logback.xml` to see compilation output

### Compilation errors

Ensure your ErgoScript syntax is valid:
```bash
java -jar ergoscript-compiler-lsp.jar compile -s "your code here"
```

### Diagnostics not updating

The server runs diagnostics on:
- File open (`textDocument/didOpen`)
- File change (`textDocument/didChange`)
- File save (`textDocument/didSave`)

If diagnostics seem stale, try saving the file.

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

[Add your license here]

## References

- [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [ErgoScript Documentation](https://docs.ergoplatform.com/dev/scs/)
- [EIP-5: Contract Template](https://github.com/ergoplatform/eips/blob/master/eip-0005.md)
- [Circe JSON Library](https://circe.github.io/circe/)

## Acknowledgments

Built with the [sigmastate-interpreter](https://github.com/ScorexFoundation/sigmastate-interpreter) library for ErgoScript compilation.

---

**Note:** This LSP implementation uses a custom JSON-RPC protocol layer instead of lsp4j to ensure full compatibility with Scala and all Java versions (17, 21, 25+). See [CUSTOM_LSP_IMPLEMENTATION.md](CUSTOM_LSP_IMPLEMENTATION.md) for technical details.
