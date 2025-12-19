# ErgoScript LSP Completion Implementation

This document describes the completion functionality implemented for the ErgoScript Language Server.

## Overview

The completion feature provides intelligent code suggestions as you type ErgoScript code. It supports context-aware completions for:

- **Keywords** (`val`, `if`, `true`, `false`)
- **Global constants** (`SELF`, `HEIGHT`, `OUTPUTS`, `INPUTS`, `CONTEXT`)
- **Built-in functions** (`sigmaProp`, `proveDlog`, `blake2b256`, etc.)
- **Data types** (`Boolean`, `Int`, `Long`, `Box`, `SigmaProp`, etc.)
- **Box members** (properties like `value`, `propositionBytes`, registers `R4`-`R9`)
- **Option methods** (`get`, `getOrElse`, `isDefined`)
- **Collection methods** (`map`, `filter`, `exists`, `forall`, etc.)

## Architecture

### Files Created

1. **`src/main/scala/org/ergoplatform/ergoscript/lsp/completion/CompletionProvider.scala`**
   - Main completion logic
   - Context extraction (member access, function call, general)
   - Completion item generation

2. **`src/main/scala/org/ergoplatform/ergoscript/lsp/completion/ErgoScriptSymbols.scala`**
   - Comprehensive symbol definitions
   - All built-in keywords, functions, types, and members
   - Properly formatted completion items with documentation

3. **`src/test/scala/CompletionProviderSpec.scala`**
   - Unit tests for completion functionality
   - 11 test cases covering all completion scenarios

### Integration

The completion provider is integrated into `SimpleLspServer.scala`:
- Line 5: Import statement
- Line 26: Instantiation of `CompletionProvider`
- Lines 253-290: Updated `handleCompletion` method

## Features

### Context-Aware Completion

The completion system analyzes the text before the cursor to determine the appropriate context:

#### 1. **Member Access Context** (triggered by `.`)

When typing `SELF.` or `OUTPUTS(0).`, the system provides members specific to Box types:

```ergoscript
SELF.    // Suggests: value, propositionBytes, R4, R5, tokens, etc.
```

For register access like `SELF.R4[Int].`, it provides Option methods:

```ergoscript
SELF.R4[Int].    // Suggests: get, getOrElse, isDefined
```

#### 2. **Function Call Context** (triggered by `(`)

When opening a function call with `(`, the system provides all available functions:

```ergoscript
sigmaProp(    // Suggests: sigmaProp, proveDlog, blake2b256, etc.
```

#### 3. **General Context**

When typing anywhere else, provides all available symbols:

```ergoscript
// Typing at the start of a line
// Suggests: val, if, SELF, HEIGHT, sigmaProp, Int, Boolean, etc.
```

### Trigger Characters

The LSP server advertises two trigger characters:
- `.` - Triggers member access completions
- `(` - Triggers function call completions

Completions can also be manually invoked at any position.

## Completion Items

Each completion item includes:

- **label**: The text to display
- **kind**: LSP completion item kind (Keyword=14, Function=3, Constant=21, etc.)
- **detail**: Type signature or category
- **documentation**: Helpful description of the symbol
- **insertText**: Text to insert (may include placeholders like `${1:name}`)

### Example Completion Items

```json
{
  "label": "sigmaProp",
  "kind": 3,
  "detail": "Boolean => SigmaProp",
  "documentation": "Convert a boolean condition to a Sigma proposition...",
  "insertText": "sigmaProp(${1:condition})"
}
```

```json
{
  "label": "SELF",
  "kind": 21,
  "detail": "Box",
  "documentation": "The current box being spent. Access its value, registers...",
  "insertText": "SELF"
}
```

## Testing

### Unit Tests

Run the test suite:

```bash
sbt test
```

All 11 tests pass, covering:
- General context completions
- Member access on SELF
- Member access on OUTPUTS
- Register member access
- Function call context
- Multi-line documents
- Partial member access
- Completion item structure
- Edge cases (empty documents, out-of-bounds positions)

### Manual Testing

1. Build the project:
   ```bash
   sbt assembly
   ```

2. Configure your editor to use the LSP:
   - VS Code: Use the ErgoScript extension (if available)
   - Neovim: Configure with `nvim-lspconfig`
   - Any LSP client: Point to `java -jar target/scala-2.13/ergoscript-compiler-lsp-assembly-0.1.0.jar`

3. Try these completions:
   ```ergoscript
   SELF.           // Should show box members
   HEIGHT          // Should complete HEIGHT
   sigma           // Should show sigmaProp
   val deadline =  // Should show all available symbols
   ```

## Symbol Coverage

### Keywords (4)
- `val`, `if`, `true`, `false`

### Global Constants (5)
- `SELF`, `HEIGHT`, `OUTPUTS`, `INPUTS`, `CONTEXT`

### Functions (12)
- `sigmaProp`, `proveDlog`, `proveDHTuple`, `atLeast`
- `allOf`, `anyOf`
- `blake2b256`, `sha256`
- `byteArrayToBigInt`, `longToByteArray`
- `fromBase64`, `toBase64`

### Box Members (13)
- Properties: `value`, `propositionBytes`, `bytes`, `bytesWithoutRef`, `id`, `creationInfo`, `tokens`
- Registers: `R4`, `R5`, `R6`, `R7`, `R8`, `R9`

### Option Methods (3)
- `get`, `getOrElse`, `isDefined`

### Collection Methods (9)
- `size`, `isEmpty`, `nonEmpty`
- `map`, `filter`, `exists`, `forall`
- `fold`, `slice`

### Types (11)
- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `BigInt`
- `GroupElement`, `SigmaProp`, `Box`
- `Coll`, `Option`

**Total: 57+ completion items**

## Future Enhancements

Possible improvements for future versions:

1. **Scope-aware completions**: Track variables declared in the current script
2. **Type inference**: Provide completions based on inferred types
3. **Smart filtering**: Filter completions based on expected types
4. **Documentation from comments**: Extract parameter docs from source
5. **Snippet support**: More sophisticated code templates
6. **Import completions**: If ErgoScript adds import/module support
7. **Context methods**: Provide CONTEXT member completions
8. **Custom function completions**: Parse user-defined functions
9. **Fuzzy matching**: Better matching for partial identifiers
10. **Signature help**: Show parameter info while typing function calls

## Implementation Notes

- The completion provider is stateless and thread-safe
- Context extraction uses regex patterns for efficiency
- All symbols are pre-computed in `ErgoScriptSymbols` object
- The system handles edge cases gracefully (out-of-bounds positions, empty documents)
- Logging is included for debugging completion requests

## LSP Compliance

The implementation follows the LSP specification:
- Uses standard completion item kinds
- Supports trigger characters
- Returns `CompletionList` with `isIncomplete` flag
- Properly handles `CompletionParams` with position and context
- Integrates with existing document synchronization

## Performance

- Context extraction: O(n) where n = characters before cursor
- Symbol lookup: O(1) (pre-computed lists)
- Completion generation: O(k) where k = number of returned items (typically < 100)
- No compilation overhead during completion requests

The completion system is designed to be fast and responsive, suitable for interactive editing.
