# ErgoScript Compiler LSP - Implementation Plan

## Executive Summary

This document outlines the implementation plan for three major features to bring the ErgoScript Compiler LSP project to the next level:

1. **Fix the Import System** - Address current issues with import resolution
2. **ErgoScript Project File** - Define a project configuration format with constants and metadata
3. **Testing Framework** - Implement `@test` structures for testing ErgoScript contracts

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Phase 1: Fix Import System](#2-phase-1-fix-import-system)
3. [Phase 2: ErgoScript Project File](#3-phase-2-ergoscript-project-file)
4. [Phase 3: Testing Framework](#4-phase-3-testing-framework)
5. [Implementation Timeline](#5-implementation-timeline)
6. [Technical Architecture](#6-technical-architecture)
7. [File Format Specifications](#7-file-format-specifications)
8. [Risk Assessment](#8-risk-assessment)

---

## 1. Current State Analysis

### 1.1 Existing Import System

**Location:** `src/main/scala/org/ergoplatform/ergoscript/lsp/imports/ImportResolver.scala`

**Current Syntax:**
```ergoscript
#import path/to/library.es;
```

**Current Issues Identified:**

1. **Workspace Root Detection**: The `getWorkspaceRootFromUri` method looks for an `ergoscript/` folder which may not always exist
2. **Path Resolution Order**: 
   - First: `workspaceRoot/ergoscript/importPath`
   - Second: Relative to current file
   - Third: Absolute path
   This order may not match user expectations.

3. **No Standard Library Location**: No defined location for standard/shared libraries
4. **LSP Integration**: Import errors are reported as compilation errors but line numbers may be incorrect after expansion
5. **No IDE Support**: No go-to-definition or auto-complete for imports

### 1.2 Existing Contract System

**EIP-5 Template Syntax:**
```ergoscript
/*
 * Contract description
 * @param paramName Description
 */
@contract def contractName(paramName: Type = defaultValue) = {
  // contract body
}
```

The system uses `SigmaTemplateCompiler` from `sigma-state` library for compilation.

### 1.3 Current Architecture

```
src/main/scala/org/ergoplatform/ergoscript/
├── Main.scala
├── cli/
│   ├── CliApp.scala
│   └── Compiler.scala
├── eip5/
│   └── JsonSerializer.scala
└── lsp/
    ├── analysis/
    │   └── UnusedVariableAnalyzer.scala
    ├── completion/
    │   ├── CompletionProvider.scala
    │   └── ErgoScriptSymbols.scala
    ├── hover/
    │   ├── HoverProvider.scala
    │   ├── HoverSymbols.scala
    │   └── TypeInference.scala
    ├── imports/
    │   └── ImportResolver.scala
    └── jsonrpc/
        ├── JsonRpcProtocol.scala
        ├── LspMessages.scala
        └── SimpleLspServer.scala
```

---

## 2. Phase 1: Fix Import System

### 2.1 Issues to Address

| Issue | Priority | Complexity |
|-------|----------|------------|
| Workspace root detection fails without `ergoscript/` folder | High | Low |
| Import path resolution not intuitive | High | Medium |
| No support for library packages | Medium | Medium |
| Error line numbers incorrect after expansion | High | High |
| No IDE features for imports | Medium | Medium |

### 2.2 Proposed Changes

#### 2.2.1 New Import Syntax (Backward Compatible)

```ergoscript
// Current syntax (still supported)
#import relative/path/to/file.es;

// New: Import from project lib folder
#import lib:utils/helpers.es;

// New: Import from project source
#import src:contracts/common.es;
```

#### 2.2.2 Path Resolution Strategy

**New Resolution Order:**
1. If prefixed with `lib:` → Look in project's `lib/` folder
2. If prefixed with `src:` → Look in project's `src/` folder  
3. If relative path (starts with `./` or `../`) → Resolve relative to current file
4. Otherwise → Look in project root, then relative to current file

#### 2.2.3 Source Map for Error Reporting

Create a source map during import expansion to track:
- Original file and line number
- Expanded position
- Import chain (for circular import debugging)

**New Data Structure:**
```scala
case class SourceLocation(
  originalFile: String,
  originalLine: Int,
  originalColumn: Int,
  importChain: List[String]  // For error messages
)

case class ExpandedCode(
  code: String,
  sourceMap: Map[Int, SourceLocation]  // expandedLine -> original location
)
```

#### 2.2.4 Improved Workspace Detection

```scala
def findProjectRoot(startPath: Path): Option[Path] = {
  // Look for these markers in order:
  val markers = List(
    "ergo.json",      // New project file
    "ergoproject.json",
    ".ergoscript",    // Hidden marker file
    "ergoscript/",    // Legacy folder
    ".git"            // Fallback to git root
  )
  
  var current = startPath.getParent
  while (current != null) {
    if (markers.exists(m => Files.exists(current.resolve(m)))) {
      return Some(current)
    }
    current = current.getParent
  }
  None
}
```

### 2.3 Implementation Tasks

| Task | File(s) to Modify/Create | Estimated Effort |
|------|--------------------------|------------------|
| Create SourceMap class | `lsp/imports/SourceMap.scala` | 2 hours |
| Update ImportResolver with new resolution strategy | `lsp/imports/ImportResolver.scala` | 4 hours |
| Add source map tracking | `lsp/imports/ImportResolver.scala` | 3 hours |
| Update Compiler to use source maps | `cli/Compiler.scala` | 2 hours |
| Update SimpleLspServer for correct error positions | `lsp/jsonrpc/SimpleLspServer.scala` | 2 hours |
| Add import-aware go-to-definition | `lsp/definition/DefinitionProvider.scala` (new) | 4 hours |
| Add import path completion | `lsp/completion/CompletionProvider.scala` | 3 hours |
| Write tests | `src/test/scala/...` | 4 hours |

---

## 3. Phase 2: ErgoScript Project File

### 3.1 Project File Format

**Filename:** `ergo.json` (primary) or `ergoproject.json` (alternative)

**Complete Schema:**

```json
{
  "$schema": "https://ergoscript.org/schema/ergo-project-v1.json",
  "name": "my-ergo-project",
  "version": "1.0.0",
  "description": "A sample ErgoScript project",
  
  "ergoscript": {
    "version": "6.0",
    "network": "mainnet"
  },
  
  "directories": {
    "source": "src",
    "lib": "lib",
    "output": "build",
    "tests": "tests"
  },
  
  "constants": {
    "minBoxValue": {
      "type": "Long",
      "value": 1000000,
      "description": "Minimum box value in nanoErgs"
    },
    "feeAddress": {
      "type": "Address",
      "value": "9f5ZKbECVTm25JTRQHDHGM5ehC8tUw5g1fCBQ4aaE792rWBFrjK",
      "description": "Fee collection address"
    },
    "tokenId": {
      "type": "Coll[Byte]",
      "value": "0x1234567890abcdef...",
      "description": "Token identifier"
    },
    "oraclePoolNft": {
      "type": "Coll[Byte]",
      "value": "env:ORACLE_POOL_NFT",
      "description": "Oracle pool NFT (from environment)"
    }
  },
  
  "compile": {
    "contracts": [
      {
        "name": "MainContract",
        "source": "src/main.es",
        "output": "build/main.json"
      }
    ]
  },
  
  "test": {
    "enabled": true,
    "parallel": false,
    "timeout": 30000
  }
}
```

### 3.2 Constants System

#### 3.2.1 Supported Types

| Type | JSON Representation | ErgoScript Type |
|------|---------------------|-----------------|
| `Boolean` | `true` / `false` | `Boolean` |
| `Byte` | number | `Byte` |
| `Short` | number | `Short` |
| `Int` | number | `Int` |
| `Long` | number / string | `Long` |
| `BigInt` | string | `BigInt` |
| `Coll[Byte]` | hex string ("0x...") | `Coll[Byte]` |
| `Address` | base58 string | Compiles to script hash |
| `GroupElement` | hex string | `GroupElement` |
| `SigmaProp` | PK("...") expression | `SigmaProp` |

#### 3.2.2 Constant Resolution

Constants defined in `ergo.json` become available in ErgoScript files:

**ergo.json:**
```json
{
  "constants": {
    "MIN_VALUE": { "type": "Long", "value": 1000000 }
  }
}
```

**contract.es:**
```ergoscript
@contract def myContract() = {
  val minValue = $MIN_VALUE  // Replaced with 1000000L at compile time
  OUTPUTS(0).value >= minValue
}
```

#### 3.2.3 Environment Variable Support

For sensitive values or environment-specific configuration:

```json
{
  "constants": {
    "privateKey": {
      "type": "GroupElement",
      "value": "env:MY_PRIVATE_KEY"
    }
  }
}
```

### 3.3 Implementation Tasks

| Task | File(s) to Create/Modify | Estimated Effort |
|------|--------------------------|------------------|
| Create ProjectConfig case classes | `project/ProjectConfig.scala` | 3 hours |
| Create JSON parser for project file | `project/ProjectConfigParser.scala` | 4 hours |
| Create constant type system | `project/ConstantTypes.scala` | 4 hours |
| Implement constant substitution in compiler | `cli/Compiler.scala` | 4 hours |
| Update ImportResolver to use project config | `lsp/imports/ImportResolver.scala` | 2 hours |
| Add project file validation | `project/ProjectValidator.scala` | 3 hours |
| LSP: Project file watching | `lsp/jsonrpc/SimpleLspServer.scala` | 3 hours |
| LSP: Constant completion | `lsp/completion/CompletionProvider.scala` | 2 hours |
| CLI: Project commands | `cli/CliApp.scala` | 3 hours |
| Write tests | `src/test/scala/...` | 4 hours |

### 3.4 New File Structure

```
project/
├── ergo.json              # Project configuration
├── lib/                   # Shared libraries
│   └── utils.es
├── src/                   # Contract source files
│   ├── main.es
│   └── helpers.es
├── tests/                 # Test files
│   └── main.test.es
└── build/                 # Compiled output
    └── main.json
```

---

## 4. Phase 3: Testing Framework

### 4.1 Test Syntax Design

#### 4.1.1 Basic Test Structure

```ergoscript
/*
 * A simple height-locked contract
 */
@contract def heightLock(minHeight: Int = 100) = {
  HEIGHT > minHeight
}

@test def testHeightLockPasses() = {
  @context {
    HEIGHT = 150
    SELF = Box {
      value = 1000000L
      tokens = []
      registers = {}
    }
    INPUTS = [SELF]
    OUTPUTS = [
      Box { value = 900000L }
    ]
  }
  
  @assert heightLock(minHeight = 100) == true
}

@test def testHeightLockFails() = {
  @context {
    HEIGHT = 50
    SELF = Box { value = 1000000L }
    INPUTS = [SELF]
    OUTPUTS = []
  }
  
  @assert heightLock(minHeight = 100) == false
}
```

#### 4.1.2 Context Definition

```ergoscript
@context {
  // Block context
  HEIGHT: Int = 500000
  
  // Transaction inputs (SELF must be one of them)
  INPUTS = [
    Box {
      id = "0x1234..."           // Optional: auto-generated if not provided
      value = 1000000000L        // In nanoErgs
      propositionBytes = heightLock(100)  // Can reference contracts!
      tokens = [
        Token { id = "0xabc...", amount = 100 }
      ]
      registers = {
        R4 = 42,                 // Int register
        R5 = "0xdeadbeef",       // Coll[Byte]
        R6 = (1, 2, 3),          // Tuple
        R7 = PK("9f5ZK...")      // SigmaProp
      }
    }
  ]
  
  // SELF reference (must match one of INPUTS)
  SELF = INPUTS(0)
  
  // Transaction outputs
  OUTPUTS = [
    Box { value = 900000L }
  ]
  
  // Data inputs (read-only boxes)
  DATA_INPUTS = [
    Box { 
      value = 1000000L
      registers = {
        R4 = 1000L  // e.g., oracle price
      }
    }
  ]
  
  // Transaction context
  CONTEXT = {
    dataInputs = DATA_INPUTS
    headers = []  // Optional: block headers for NIPoPoW
    preHeader = {
      version = 2
      parentId = "0x..."
      timestamp = 1640000000000L
      nBits = 117440512L
      height = HEIGHT
      minerPk = GroupElement("0x...")
      votes = "0x000000"
    }
  }
}
```

#### 4.1.3 Assertions

```ergoscript
@test def testWithAssertions() = {
  @context { ... }
  
  // Basic assertion
  @assert heightLock(100) == true
  
  // Named assertion (for better error messages)
  @assert("Height check should pass") heightLock(100) == true
  
  // Multiple assertions
  @assert OUTPUTS.size == 1
  @assert OUTPUTS(0).value >= 900000L
  
  // Assert contract evaluates to specific SigmaProp
  @assert heightLock(100) === sigmaProp(true)
  
  // Assert provable (can be proven with given secrets)
  @provable heightLock(100) with {
    secrets = []  // No secrets needed for height check
  }
}
```

#### 4.1.4 Test Fixtures and Helpers

```ergoscript
// Define reusable fixtures
@fixture def standardBox(value: Long = 1000000L) = Box {
  value = value
  tokens = []
  registers = {}
}

@fixture def oracleBox(price: Long) = Box {
  value = 1000000L
  registers = {
    R4 = price
  }
}

@test def testWithFixture() = {
  @context {
    HEIGHT = 100
    SELF = standardBox(2000000L)
    INPUTS = [SELF]
    OUTPUTS = [standardBox(1000000L)]
    DATA_INPUTS = [oracleBox(1000L)]
  }
  
  @assert myContract() == true
}
```

### 4.2 Test Execution Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Test Runner                             │
├─────────────────────────────────────────────────────────────┤
│  1. Parse .es files for @test blocks                        │
│  2. Parse @context into MockContext                         │
│  3. Compile referenced @contract definitions                 │
│  4. Create ErgoLikeContext from MockContext                 │
│  5. Evaluate contract in context                            │
│  6. Run assertions                                           │
│  7. Report results                                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   sigma-state library                        │
├─────────────────────────────────────────────────────────────┤
│  - SigmaCompiler: Compile ErgoScript                        │
│  - ErgoLikeContext: Transaction context                     │
│  - Interpreter: Evaluate ErgoTree                           │
│  - Prover: Prove spending conditions                        │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 Implementation Components

#### 4.3.1 Test Parser

```scala
// New file: testing/TestParser.scala

case class TestDefinition(
  name: String,
  context: TestContext,
  assertions: List[TestAssertion],
  line: Int,
  column: Int
)

case class TestContext(
  height: Long,
  self: MockBox,
  inputs: List[MockBox],
  outputs: List[MockBox],
  dataInputs: List[MockBox],
  preHeader: Option[MockPreHeader]
)

case class MockBox(
  value: Long,
  propositionBytes: Option[String],  // Contract reference or raw bytes
  tokens: List[MockToken],
  registers: Map[String, Any]
)

case class TestAssertion(
  description: Option[String],
  expression: String,
  assertionType: AssertionType  // Equals, Provable, etc.
)
```

#### 4.3.2 Context Builder

```scala
// New file: testing/ContextBuilder.scala

object ContextBuilder {
  def build(testContext: TestContext): ErgoLikeContext = {
    // Convert MockBox to ErgoBox
    val inputs = testContext.inputs.map(convertToErgoBox)
    val outputs = testContext.outputs.map(convertToErgoBox)
    val dataInputs = testContext.dataInputs.map(convertToErgoBox)
    
    // Build ErgoLikeContext
    new ErgoLikeContext(
      lastBlockUtxoRoot = AvlTreeData.dummy,
      headers = IndexedSeq.empty,
      preHeader = buildPreHeader(testContext),
      dataBoxes = dataInputs.toIndexedSeq,
      boxesToSpend = inputs.toIndexedSeq,
      spendingTransaction = buildTransaction(inputs, outputs),
      selfIndex = findSelfIndex(testContext),
      extension = ContextExtension.empty,
      validationSettings = ValidationRules.currentSettings,
      costLimit = Long.MaxValue,
      initCost = 0L,
      activatedScriptVersion = 2.toByte
    )
  }
}
```

#### 4.3.3 Test Runner

```scala
// New file: testing/TestRunner.scala

case class TestResult(
  name: String,
  passed: Boolean,
  duration: Long,
  error: Option[String],
  assertions: List[AssertionResult]
)

case class TestSuiteResult(
  file: String,
  tests: List[TestResult],
  passed: Int,
  failed: Int,
  duration: Long
)

class TestRunner {
  def runTests(files: List[Path]): TestSuiteResult = {
    files.flatMap(parseTestFile).map(runTest)
  }
  
  def runTest(test: TestDefinition): TestResult = {
    val context = ContextBuilder.build(test.context)
    
    test.assertions.map { assertion =>
      assertion.assertionType match {
        case AssertionType.Equals =>
          evaluateEquals(assertion, context)
        case AssertionType.Provable =>
          evaluateProvable(assertion, context)
      }
    }
  }
}
```

### 4.4 CLI Integration

```bash
# Run all tests
ergoscript test

# Run tests in specific file
ergoscript test tests/main.test.es

# Run with verbose output
ergoscript test --verbose

# Run specific test by name
ergoscript test --filter "testHeightLock*"

# Watch mode (re-run on file changes)
ergoscript test --watch
```

### 4.5 LSP Integration

- **Test Discovery**: Find and list all tests in workspace
- **Run Test CodeLens**: Clickable "Run Test" above each `@test`
- **Test Results**: Show pass/fail inline
- **Debug Support**: Step through test execution (future enhancement)

### 4.6 Implementation Tasks

| Task | File(s) to Create | Estimated Effort |
|------|-------------------|------------------|
| Design test grammar | `testing/TestGrammar.scala` | 4 hours |
| Implement test parser | `testing/TestParser.scala` | 8 hours |
| Create MockBox/MockContext types | `testing/MockTypes.scala` | 4 hours |
| Implement ContextBuilder | `testing/ContextBuilder.scala` | 8 hours |
| Implement TestRunner | `testing/TestRunner.scala` | 6 hours |
| Implement assertion evaluator | `testing/AssertionEvaluator.scala` | 6 hours |
| CLI integration | `cli/CliApp.scala` | 4 hours |
| Result formatter (console) | `testing/ResultFormatter.scala` | 3 hours |
| LSP test discovery | `lsp/testing/TestDiscovery.scala` | 4 hours |
| LSP CodeLens for tests | `lsp/testing/TestCodeLens.scala` | 4 hours |
| Write tests for testing framework | `src/test/scala/...` | 8 hours |

---

## 5. Implementation Timeline

### Phase 1: Import System Fix (Week 1-2)

```
Week 1:
├── Day 1-2: SourceMap implementation
├── Day 3-4: ImportResolver updates
└── Day 5: Testing & debugging

Week 2:
├── Day 1-2: Compiler integration
├── Day 3: LSP error position fixes
├── Day 4: Import path completion
└── Day 5: Testing & documentation
```

### Phase 2: Project File (Week 3-4)

```
Week 3:
├── Day 1-2: ProjectConfig & parser
├── Day 3-4: Constant type system
└── Day 5: Constant substitution

Week 4:
├── Day 1-2: LSP integration
├── Day 3: CLI commands
├── Day 4: Project validation
└── Day 5: Testing & documentation
```

### Phase 3: Testing Framework (Week 5-8)

```
Week 5:
├── Day 1-3: Test grammar & parser
└── Day 4-5: MockTypes implementation

Week 6:
├── Day 1-3: ContextBuilder
└── Day 4-5: Initial TestRunner

Week 7:
├── Day 1-3: Assertion evaluator
├── Day 4: Result formatter
└── Day 5: CLI integration

Week 8:
├── Day 1-2: LSP test discovery
├── Day 3: CodeLens implementation
├── Day 4-5: Testing & documentation
```

---

## 6. Technical Architecture

### 6.1 New Package Structure

```
src/main/scala/org/ergoplatform/ergoscript/
├── Main.scala
├── cli/
│   ├── CliApp.scala
│   ├── Compiler.scala
│   └── Commands.scala              # NEW: CLI command definitions
├── eip5/
│   └── JsonSerializer.scala
├── project/                        # NEW
│   ├── ProjectConfig.scala         # Project configuration model
│   ├── ProjectConfigParser.scala   # JSON parser
│   ├── ProjectValidator.scala      # Validation logic
│   ├── ConstantTypes.scala         # Constant type system
│   └── ConstantSubstitution.scala  # Compile-time substitution
├── testing/                        # NEW
│   ├── TestParser.scala            # Parse @test blocks
│   ├── TestGrammar.scala           # Test syntax definitions
│   ├── MockTypes.scala             # MockBox, MockContext, etc.
│   ├── ContextBuilder.scala        # Build ErgoLikeContext
│   ├── TestRunner.scala            # Execute tests
│   ├── AssertionEvaluator.scala    # Evaluate assertions
│   └── ResultFormatter.scala       # Format test results
└── lsp/
    ├── analysis/
    │   └── UnusedVariableAnalyzer.scala
    ├── completion/
    │   ├── CompletionProvider.scala
    │   ├── ErgoScriptSymbols.scala
    │   └── ImportCompletion.scala  # NEW: Import path completion
    ├── definition/                 # NEW
    │   └── DefinitionProvider.scala
    ├── hover/
    │   ├── HoverProvider.scala
    │   ├── HoverSymbols.scala
    │   └── TypeInference.scala
    ├── imports/
    │   ├── ImportResolver.scala    # Updated
    │   └── SourceMap.scala         # NEW: Source mapping
    ├── testing/                    # NEW
    │   ├── TestDiscovery.scala
    │   └── TestCodeLens.scala
    └── jsonrpc/
        ├── JsonRpcProtocol.scala
        ├── LspMessages.scala
        └── SimpleLspServer.scala   # Updated
```

### 6.2 Dependencies

Current dependencies in `build.sbt` are sufficient. The testing framework will leverage existing sigma-state APIs:

- `sigma.ast.ErgoTree` - Compiled contract representation
- `org.ergoplatform.ErgoLikeContext` - Transaction context
- `org.ergoplatform.ErgoBox` - Box representation
- `sigmastate.interpreter.Interpreter` - Contract evaluation

---

## 7. File Format Specifications

### 7.1 ErgoScript Source Files (`.es`)

**MIME Type:** `text/x-ergoscript`

**Structure:**
```ergoscript
// Imports (optional, at top of file)
#import lib:utils.es;
#import ./helpers.es;

// Constants (from project file, referenced with $)
// $CONSTANT_NAME

// Contract definitions
@contract def contractName(params) = { ... }

// Test definitions (typically in separate .test.es files)
@test def testName() = { ... }

// Fixtures (for tests)
@fixture def fixtureName(params) = { ... }
```

### 7.2 Project File (`ergo.json`)

See Section 3.1 for complete schema.

### 7.3 Compiled Output (EIP-5 JSON)

Following EIP-5 specification:
```json
{
  "name": "ContractName",
  "description": "...",
  "constTypes": ["0x..."],
  "constValues": ["0x..."],
  "parameters": [...],
  "expressionTree": "0x..."
}
```

---

## 8. Risk Assessment

### 8.1 Technical Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| sigma-state API changes | High | Low | Pin to specific version, add compatibility layer |
| Complex test context construction | Medium | Medium | Thorough testing, incremental feature addition |
| Source map accuracy | High | Medium | Extensive testing with various import patterns |
| Performance with large projects | Medium | Low | Lazy loading, caching |

### 8.2 Compatibility Considerations

- **Backward Compatibility**: Existing `.es` files without project file must continue to work
- **sigma-state Version**: Currently using 6.0.2, ensure compatibility maintained
- **Editor Support**: New features should degrade gracefully in editors without full LSP support

### 8.3 Testing Strategy

1. **Unit Tests**: For each new component (parser, resolver, evaluator)
2. **Integration Tests**: End-to-end compilation and test execution
3. **Regression Tests**: Ensure existing functionality preserved
4. **Example Projects**: Create sample projects demonstrating all features

---

## Appendix A: Example Project

```
my-ergo-project/
├── ergo.json
├── lib/
│   └── common.es
├── src/
│   ├── main.es
│   └── oracle/
│       └── connector.es
├── tests/
│   ├── main.test.es
│   └── oracle/
│       └── connector.test.es
└── build/
    ├── main.json
    └── oracle/
        └── connector.json
```

**ergo.json:**
```json
{
  "name": "my-ergo-project",
  "version": "1.0.0",
  "constants": {
    "MIN_BOX_VALUE": { "type": "Long", "value": 1000000 },
    "ORACLE_NFT": { "type": "Coll[Byte]", "value": "0x..." }
  }
}
```

**src/main.es:**
```ergoscript
#import lib:common.es;

@contract def main(deadline: Int = 1000) = {
  val minValue = $MIN_BOX_VALUE
  OUTPUTS(0).value >= minValue && HEIGHT > deadline
}
```

**tests/main.test.es:**
```ergoscript
#import src:main.es;

@test def testMainContractPasses() = {
  @context {
    HEIGHT = 1500
    SELF = Box { value = 2000000L }
    INPUTS = [SELF]
    OUTPUTS = [Box { value = 1500000L }]
  }
  
  @assert main(deadline = 1000) == true
}

@test def testMainContractFailsBeforeDeadline() = {
  @context {
    HEIGHT = 500
    SELF = Box { value = 2000000L }
    INPUTS = [SELF]
    OUTPUTS = [Box { value = 1500000L }]
  }
  
  @assert main(deadline = 1000) == false
}
```

---

## Appendix B: CLI Commands Reference

```bash
# Project management
ergoscript init                    # Create new ergo.json
ergoscript init --template=basic   # Use template

# Compilation
ergoscript compile                 # Compile all contracts
ergoscript compile src/main.es     # Compile specific file
ergoscript compile --watch         # Watch mode

# Testing
ergoscript test                    # Run all tests
ergoscript test tests/main.test.es # Run specific test file
ergoscript test --filter="*Oracle*" # Filter tests by name
ergoscript test --verbose          # Verbose output
ergoscript test --watch            # Watch mode

# LSP
ergoscript lsp                     # Start LSP server

# Utilities
ergoscript validate                # Validate ergo.json
ergoscript check                   # Type-check without compiling
```

---

*Document Version: 1.0*
*Created: 2025*
*Author: ErgoScript Compiler LSP Team*