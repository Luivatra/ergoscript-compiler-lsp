# ErgoScript LSP Quick Start Guide

Get up and running with the ErgoScript Language Server in 5 minutes!

## Step 1: Build the Server

```bash
cd ergoscript-compiler-lsp
sbt assembly
```

This creates `target/scala-2.13/ergoscript-compiler-lsp.jar` (~40MB).

## Step 2: Test the Server

```bash
# Test that it works
java -jar target/scala-2.13/ergoscript-compiler-lsp.jar --version

# Output should be:
# ErgoScript Compiler LSP v0.1.0
# Built with Scala 2.13.16
# sigmastate-interpreter: 6.0.2
```

## Step 3: Configure Your Editor

### For VS Code

1. Install an LSP client extension (if not already installed)
2. Add to your `settings.json`:

```json
{
  "ergoscript.lsp.command": "java",
  "ergoscript.lsp.args": [
    "-jar",
    "/absolute/path/to/ergoscript-compiler-lsp.jar",
    "lsp"
  ]
}
```

### For Neovim

Add to your Neovim config:

```lua
local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

if not configs.ergoscript then
  configs.ergoscript = {
    default_config = {
      cmd = {'java', '-jar', '/path/to/ergoscript-compiler-lsp.jar', 'lsp'},
      filetypes = {'ergoscript', 'ergo'},
      root_dir = lspconfig.util.root_pattern('.git'),
    },
  }
end

lspconfig.ergoscript.setup{}
```

### For Emacs

Add to your `init.el`:

```elisp
(use-package lsp-mode
  :config
  (lsp-register-client
   (make-lsp-client
    :new-connection (lsp-stdio-connection
                     '("java" "-jar" "/path/to/ergoscript-compiler-lsp.jar" "lsp"))
    :major-modes '(ergoscript-mode)
    :server-id 'ergoscript-lsp)))
```

## Step 4: Write ErgoScript!

1. Create a test file: `test.es`
2. Add some code:

```ergoscript
// Valid code - no errors
sigmaProp(HEIGHT > 100)
```

3. Try invalid code:

```ergoscript
// Invalid - undefined variable
sigmaProp(unknownVar > 100)
```

You should see a red underline with the error: "Cannot assign type for variable 'unknownVar'"

## What You Get

### ✅ Real-Time Error Detection

- Syntax errors appear as you type
- Type errors highlighted immediately
- Undefined variables caught instantly

### ✅ Error Information

- Hover over errors to see details
- Line and column information
- Clear error messages

### ✅ Instant Feedback

- Errors appear within ~300ms
- Updates on every change
- Clears when code is fixed

## Common ErgoScript Examples

### Valid Code Examples

```ergoscript
// Simple proposition
sigmaProp(true)

// Height check
sigmaProp(HEIGHT > 100)

// Box value check
sigmaProp(OUTPUTS(0).value >= 1000000)

// Multiple conditions
sigmaProp(HEIGHT > 100 && OUTPUTS(0).value >= 1000)
```

### Common Errors

```ergoscript
// ❌ Undefined variable
sigmaProp(unknownVar)
// Error: Cannot assign type for variable 'unknownVar'

// ❌ Type error
sigmaProp(123)
// Error: Type mismatch (expecting Boolean)

// ❌ Syntax error
this is not valid
// Error: Parse error
```

## Troubleshooting

### Server not starting?

```bash
# Check Java version (needs 17+)
java -version

# Check logs
tail -f ergoscript-lsp.log
```

### No errors showing?

1. Check the server is connected
2. Try saving the file
3. Check editor LSP client is working
4. Look at `ergoscript-lsp.log`

### Still stuck?

Check the full documentation:
- `README.md` - Complete guide
- `DIAGNOSTICS_IMPLEMENTATION.md` - Technical details
- `examples/` - Editor configuration examples

## Next Steps

Now that you have diagnostics working, explore:

1. **Editor Features**
   - Problems panel (shows all errors)
   - Error navigation (jump between errors)
   - Status bar (shows error count)

2. **Compiler CLI**
   ```bash
   # Compile to EIP-5 JSON
   java -jar ergoscript-compiler-lsp.jar compile -i mycontract.es
   ```

3. **Documentation**
   - Learn ErgoScript: https://docs.ergoplatform.com/dev/scs/
   - EIP-5 Contract Template: https://github.com/ergoplatform/eips

## Summary

You now have a fully working ErgoScript development environment with:

✅ Syntax checking  
✅ Type checking  
✅ Real-time feedback  
✅ IDE integration  

Happy coding! 🎉

---

**Need help?** Check `README.md` or open an issue on GitHub.
