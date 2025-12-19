# EIP-5 Contract Template Examples

This directory contains example contracts that demonstrate EIP-5 (Contract Template) support in the ErgoScript compiler.

## What is EIP-5?

EIP-5 establishes standardized serialization formats and metadata for reusable contract templates. It allows contracts to be:
- Compiled once with named parameters
- Reused across different platforms and applications
- Instantiated with different parameter values without recompilation

## Contract Template Syntax

EIP-5 contracts use the `@contract` annotation with a preceding docstring comment block. The docstring must use `/* */` style comments (not `//`) and include `@param` tags for each parameter:

```scala
/*
 * Contract description
 *
 * @param paramName Description of this parameter
 * @param anotherParam Description of another parameter
 */
@contract def contractName(
  paramName: ParamType = defaultValue,
  anotherParam: AnotherType = anotherDefault
) = {
  // Contract logic here
}
```

**Important:** 
- Use `/* */` comments, not `/** */` or `//`
- Each parameter must have a corresponding `@param` tag in the docstring
- Parameters must have type annotations and default values

## Examples

### 1. Simple Height Lock (`eip5-simple-contract.es`)

A basic contract that locks funds until a specific block height:

```scala
/*
 * Simple Height Lock Contract
 *
 * @param minHeight The minimum block height required to spend the funds
 */
@contract def heightLock(minHeight: Int = 100) = {
  HEIGHT > minHeight
}
```

**Parameters:**
- `minHeight`: The minimum block height required to spend the funds (default: 100)

### 2. Multi-Signature (`eip5-multi-sig.es`)

Requires N-of-M signatures to spend funds:

```scala
/*
 * Multi-Signature Contract
 *
 * @param threshold Number of signatures required
 * @param publicKeys Collection of public keys that can sign
 */
@contract def multiSig(
  threshold: Int = 2,
  publicKeys: Coll[SigmaProp] = Coll(...)
) = {
  atLeast(threshold, publicKeys)
}
```

**Parameters:**
- `threshold`: Number of signatures required (default: 2)
- `publicKeys`: Collection of public keys that can sign

### 3. Payment Channel (`eip5-payment-channel.es`)

A simple payment channel with timeout:

```scala
/*
 * Payment Channel Contract
 *
 * @param senderPK Public key of the sender who can reclaim after timeout
 * @param recipientPK Public key of the recipient who can spend with sender's signature
 * @param timeout Block height after which sender can reclaim funds
 */
@contract def paymentChannel(
  senderPK: SigmaProp = ...,
  recipientPK: SigmaProp = ...,
  timeout: Int = 1000
) = {
  val recipientPath = recipientPK && senderPK
  val timeoutPath = senderPK && (HEIGHT > timeout)
  recipientPath || timeoutPath
}
```

**Parameters:**
- `senderPK`: Public key of the sender
- `recipientPK`: Public key of the recipient
- `timeout`: Block height after which sender can reclaim funds

## Compiling EIP-5 Templates

Use the compiler with EIP-5 templates:

```bash
# Compile a template
./ergoscript-compiler-lsp compile examples/eip5-simple-contract.es --output template.json

# The output will be a JSON file following the EIP-5 specification
```

## JSON Output Format

The compiled template follows this structure:

```json
{
  "name": "heightLock",
  "description": "...",
  "constTypes": ["0x04"],
  "constValues": ["0x64"],
  "parameters": [
    {
      "name": "minHeight",
      "description": "The minimum block height required",
      "constantIndex": 0
    }
  ],
  "expressionTree": "0x..."
}
```

## Using Templates

Templates can be instantiated with different parameter values:

1. **Compile the template** once to generate the JSON
2. **Store or share** the template JSON
3. **Instantiate** with specific parameter values by updating `constValues` at the appropriate `constantIndex`
4. **Generate ErgoTree** from the instantiated template

This allows contract templates to be created in one environment (e.g., with ErgoScala) and used in another (e.g., mobile apps, web browsers) without requiring a full compiler.

## References

- [EIP-5 Specification](https://github.com/ergoplatform/eips/blob/master/eip-0005.md)
- [SigmaState Interpreter](https://github.com/ergoplatform/sigmastate-interpreter/issues/852)
- [ErgoScript Language Specification](https://github.com/ergoplatform/sigmastate-interpreter/blob/develop/docs/LangSpec.md)
