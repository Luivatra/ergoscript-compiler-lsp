package org.ergoplatform.ergoscript.lsp.hover

/** Defines hover information for all ErgoScript built-in symbols.
  *
  * This object provides comprehensive documentation for keywords, functions,
  * constants, types, and methods.
  */
object HoverSymbols {

  /** Lookup hover information for a symbol by name.
    */
  def getHoverInfo(symbol: String): Option[HoverInfo] = {
    allSymbols.get(symbol)
  }

  /** Map of symbol names to their hover information.
    */
  private val allSymbols: Map[String, HoverInfo] = Map(
    // Keywords
    "val" -> HoverInfo(
      signature = Some("val name = value"),
      description =
        "Declares an immutable value binding. Once assigned, the value cannot be changed.",
      category = Some("Keyword"),
      examples = List(
        "val deadline = 100000",
        "val amount = SELF.value"
      )
    ),
    "@contract" -> HoverInfo(
      signature = Some(
        "/* docstring */ @contract def name(param: Type = default, ...) = { body }"
      ),
      description =
        "EIP-5 contract template annotation. Defines a reusable contract with named parameters that can be instantiated with different values. Must be preceded by a /* */ comment block with @param tags for each parameter. Parameters must have type annotations and default values.",
      category = Some("Annotation"),
      examples = List(
        "/* @param minHeight Minimum block height */ @contract def heightLock(minHeight: Int = 100) = { HEIGHT > minHeight }",
        "/* @param threshold Required signatures @param keys Public keys */ @contract def multiSig(threshold: Int = 2, keys: Coll[SigmaProp]) = { atLeast(threshold, keys) }"
      ),
      related = List("def", "val")
    ),
    "if" -> HoverInfo(
      signature = Some("if (condition) trueCase else falseCase"),
      description =
        "Conditional expression that evaluates to different values based on a boolean condition.",
      category = Some("Keyword"),
      examples = List(
        "if (HEIGHT > 100) sigmaProp(true) else sigmaProp(false)",
        "val result = if (x > 0) x else -x"
      )
    ),
    "true" -> HoverInfo(
      signature = Some("true: Boolean"),
      description = "Boolean constant representing logical truth.",
      category = Some("Constant")
    ),
    "false" -> HoverInfo(
      signature = Some("false: Boolean"),
      description = "Boolean constant representing logical falsehood.",
      category = Some("Constant")
    ),

    // Global Constants
    "SELF" -> HoverInfo(
      signature = Some("SELF: Box"),
      description =
        "The box that is currently being spent by this script. Provides access to the box's value, registers, tokens, and other properties.",
      category = Some("Global Constant"),
      examples = List(
        "val deadline = SELF.R4[Int].get",
        "val boxValue = SELF.value",
        "val tokens = SELF.tokens"
      ),
      related = List("Box", "value", "R4", "tokens")
    ),
    "HEIGHT" -> HoverInfo(
      signature = Some("HEIGHT: Int"),
      description =
        "The current blockchain height. This is the height of the block being validated. Useful for implementing time-locked contracts and deadlines.",
      category = Some("Global Constant"),
      examples = List(
        "sigmaProp(HEIGHT > 100000)",
        "val deadline = SELF.R4[Int].get\nsigmaProp(HEIGHT > deadline)"
      ),
      related = List("SELF", "sigmaProp")
    ),
    "OUTPUTS" -> HoverInfo(
      signature = Some("OUTPUTS: Coll[Box]"),
      description =
        "Collection of output boxes in the current transaction. Access individual outputs using OUTPUTS(index). Used to enforce constraints on transaction outputs.",
      category = Some("Global Constant"),
      examples = List(
        "val firstOutput = OUTPUTS(0)",
        "sigmaProp(OUTPUTS(0).value >= 1000000)",
        "val recipientBox = OUTPUTS(0)"
      ),
      related = List("INPUTS", "Box", "SELF")
    ),
    "INPUTS" -> HoverInfo(
      signature = Some("INPUTS: Coll[Box]"),
      description =
        "Collection of input boxes in the current transaction. Access individual inputs using INPUTS(index).",
      category = Some("Global Constant"),
      examples = List(
        "val firstInput = INPUTS(0)",
        "val totalInput = INPUTS.fold(0L, (acc, box) => acc + box.value)"
      ),
      related = List("OUTPUTS", "Box", "SELF")
    ),
    "CONTEXT" -> HoverInfo(
      signature = Some("CONTEXT: Context"),
      description =
        "The transaction context containing blockchain state information and transaction details.",
      category = Some("Global Constant"),
      related = List("HEIGHT", "SELF")
    ),

    // Built-in Functions
    "sigmaProp" -> HoverInfo(
      signature = Some("def sigmaProp(condition: Boolean): SigmaProp"),
      description =
        "Converts a boolean condition into a Sigma proposition. This is the fundamental building block for ErgoScript contracts. The returned SigmaProp represents a condition that must be satisfied for the transaction to be valid.",
      category = Some("Function"),
      examples = List(
        "sigmaProp(true)",
        "sigmaProp(HEIGHT > 100000)",
        "sigmaProp(OUTPUTS(0).value >= 1000000)"
      ),
      related = List("proveDlog", "SigmaProp")
    ),
    "proveDlog" -> HoverInfo(
      signature = Some("def proveDlog(value: GroupElement): SigmaProp"),
      description =
        "Creates a Sigma proposition that requires proof of knowledge of the discrete logarithm for the given group element. This is used for public key cryptography and signature verification.",
      category = Some("Function"),
      examples = List(
        "proveDlog(publicKey)"
      ),
      related = List("sigmaProp", "proveDHTuple", "GroupElement")
    ),
    "proveDHTuple" -> HoverInfo(
      signature = Some(
        "def proveDHTuple(g: GroupElement, h: GroupElement, u: GroupElement, v: GroupElement): SigmaProp"
      ),
      description =
        "Creates a Sigma proposition requiring proof that the tuple (g, h, u, v) forms a valid Diffie-Hellman tuple, i.e., log_g(u) = log_h(v).",
      category = Some("Function"),
      related = List("proveDlog", "GroupElement")
    ),
    "atLeast" -> HoverInfo(
      signature =
        Some("def atLeast(k: Int, propositions: Coll[SigmaProp]): SigmaProp"),
      description =
        "Creates a threshold Sigma proposition that requires at least k of the n provided propositions to be satisfied. This enables k-of-n multi-signature schemes.",
      category = Some("Function"),
      examples = List(
        "atLeast(2, Coll(proveDlog(pk1), proveDlog(pk2), proveDlog(pk3)))"
      ),
      related = List("allOf", "anyOf", "SigmaProp")
    ),
    "allOf" -> HoverInfo(
      signature = Some("def allOf(conditions: Coll[Boolean]): Boolean"),
      description =
        "Returns true if all boolean values in the collection are true. Short-circuits evaluation on the first false value.",
      category = Some("Function"),
      examples = List(
        "allOf(Coll(HEIGHT > 100, OUTPUTS(0).value >= 1000))"
      ),
      related = List("anyOf", "atLeast")
    ),
    "anyOf" -> HoverInfo(
      signature = Some("def anyOf(conditions: Coll[Boolean]): Boolean"),
      description =
        "Returns true if any boolean value in the collection is true. Short-circuits evaluation on the first true value.",
      category = Some("Function"),
      examples = List(
        "anyOf(Coll(HEIGHT > 100, OUTPUTS(0).value >= 1000))"
      ),
      related = List("allOf", "atLeast")
    ),
    "blake2b256" -> HoverInfo(
      signature = Some("def blake2b256(input: Coll[Byte]): Coll[Byte]"),
      description =
        "Computes the BLAKE2b-256 hash of the input bytes. Returns a 32-byte hash. BLAKE2b is a cryptographic hash function that is faster than SHA-256 while providing similar security.",
      category = Some("Function"),
      examples = List(
        "val hash = blake2b256(SELF.propositionBytes)"
      ),
      related = List("sha256")
    ),
    "sha256" -> HoverInfo(
      signature = Some("def sha256(input: Coll[Byte]): Coll[Byte]"),
      description =
        "Computes the SHA-256 hash of the input bytes. Returns a 32-byte hash.",
      category = Some("Function"),
      related = List("blake2b256")
    ),
    "byteArrayToBigInt" -> HoverInfo(
      signature = Some("def byteArrayToBigInt(bytes: Coll[Byte]): BigInt"),
      description =
        "Converts a byte array to a BigInt value using big-endian encoding.",
      category = Some("Function"),
      related = List("longToByteArray", "BigInt")
    ),
    "longToByteArray" -> HoverInfo(
      signature = Some("def longToByteArray(value: Long): Coll[Byte]"),
      description =
        "Converts a Long value to a byte array using big-endian encoding.",
      category = Some("Function"),
      related = List("byteArrayToBigInt")
    ),
    "fromBase64" -> HoverInfo(
      signature = Some("def fromBase64(string: String): Coll[Byte]"),
      description = "Decodes a Base64-encoded string to bytes.",
      category = Some("Function"),
      related = List("toBase64")
    ),
    "toBase64" -> HoverInfo(
      signature = Some("def toBase64(bytes: Coll[Byte]): String"),
      description = "Encodes bytes to a Base64 string.",
      category = Some("Function"),
      related = List("fromBase64")
    ),

    // Box Members
    "value" -> HoverInfo(
      signature = Some("value: Long"),
      description =
        "The amount of ERG (in nanoERGs) contained in the box. 1 ERG = 1,000,000,000 nanoERGs.",
      category = Some("Property"),
      examples = List(
        "val boxValue = SELF.value",
        "sigmaProp(OUTPUTS(0).value >= 1000000000L)"
      ),
      related = List("Box", "SELF")
    ),
    "propositionBytes" -> HoverInfo(
      signature = Some("propositionBytes: Coll[Byte]"),
      description =
        "The serialized guard script (ErgoTree) of the box. This is the script that must be satisfied to spend the box.",
      category = Some("Property"),
      related = List("Box", "bytes")
    ),
    "bytes" -> HoverInfo(
      signature = Some("bytes: Coll[Byte]"),
      description =
        "The complete serialized representation of the box, including all its contents.",
      category = Some("Property"),
      related = List("bytesWithoutRef", "Box")
    ),
    "bytesWithoutRef" -> HoverInfo(
      signature = Some("bytesWithoutRef: Coll[Byte]"),
      description =
        "The serialized box without the transaction reference. Used for creating box identifiers.",
      category = Some("Property"),
      related = List("bytes", "id")
    ),
    "id" -> HoverInfo(
      signature = Some("id: Coll[Byte]"),
      description =
        "The unique identifier (hash) of the box. This is a 32-byte value computed from the box contents.",
      category = Some("Property"),
      related = List("Box", "bytes")
    ),
    "creationInfo" -> HoverInfo(
      signature = Some("creationInfo: (Int, Coll[Byte])"),
      description =
        "A tuple containing the height and transaction ID when this box was created. Format: (height, txId).",
      category = Some("Property"),
      examples = List(
        "val (creationHeight, txId) = SELF.creationInfo"
      ),
      related = List("Box", "HEIGHT")
    ),
    "tokens" -> HoverInfo(
      signature = Some("tokens: Coll[(Coll[Byte], Long)]"),
      description =
        "Collection of token (tokenId, amount) pairs stored in the box. TokenId is a 32-byte identifier, and amount is the quantity of that token.",
      category = Some("Property"),
      examples = List(
        "val boxTokens = SELF.tokens",
        "val hasToken = SELF.tokens.exists((t) => t._1 == tokenId)"
      ),
      related = List("Box", "SELF")
    ),
    "R4" -> HoverInfo(
      signature = Some("R4[T]: Option[T]"),
      description =
        "Register R4 of the box. Boxes have registers R4-R9 available for storing arbitrary typed data. Access with a type parameter.",
      category = Some("Property"),
      examples = List(
        "val deadline = SELF.R4[Int].get",
        "val data = SELF.R4[Coll[Byte]].getOrElse(Coll[Byte]())"
      ),
      related = List("R5", "R6", "Box", "get", "getOrElse")
    ),
    "R5" -> HoverInfo(
      signature = Some("R5[T]: Option[T]"),
      description =
        "Register R5 of the box. Use with a type parameter to access typed data stored in this register.",
      category = Some("Property"),
      examples = List(
        "val metadata = SELF.R5[Coll[Byte]].get"
      ),
      related = List("R4", "R6", "Box")
    ),
    "R6" -> HoverInfo(
      signature = Some("R6[T]: Option[T]"),
      description = "Register R6 of the box.",
      category = Some("Property"),
      related = List("R4", "R5", "R7", "Box")
    ),
    "R7" -> HoverInfo(
      signature = Some("R7[T]: Option[T]"),
      description = "Register R7 of the box.",
      category = Some("Property"),
      related = List("R6", "R8", "Box")
    ),
    "R8" -> HoverInfo(
      signature = Some("R8[T]: Option[T]"),
      description = "Register R8 of the box.",
      category = Some("Property"),
      related = List("R7", "R9", "Box")
    ),
    "R9" -> HoverInfo(
      signature = Some("R9[T]: Option[T]"),
      description =
        "Register R9 of the box. This is the last available register for custom data.",
      category = Some("Property"),
      related = List("R8", "Box")
    ),

    // Option Methods
    "get" -> HoverInfo(
      signature = Some("def get: T"),
      description =
        "Extracts the value from an Option[T]. Throws an exception if the Option is None. Use with caution - prefer getOrElse or isDefined for safety.",
      category = Some("Method"),
      examples = List(
        "val deadline = SELF.R4[Int].get"
      ),
      related = List("getOrElse", "isDefined", "Option")
    ),
    "getOrElse" -> HoverInfo(
      signature = Some("def getOrElse(default: T): T"),
      description =
        "Returns the value if the Option is defined, otherwise returns the provided default value. This is the safe way to extract Option values.",
      category = Some("Method"),
      examples = List(
        "val deadline = SELF.R4[Int].getOrElse(0)",
        "val data = SELF.R5[Coll[Byte]].getOrElse(Coll[Byte]())"
      ),
      related = List("get", "isDefined", "Option")
    ),
    "isDefined" -> HoverInfo(
      signature = Some("def isDefined: Boolean"),
      description =
        "Returns true if the Option contains a value (Some), false if it is empty (None).",
      category = Some("Method"),
      examples = List(
        "if (SELF.R4[Int].isDefined) { /* ... */ }"
      ),
      related = List("get", "getOrElse", "Option")
    ),

    // Collection Methods
    "size" -> HoverInfo(
      signature = Some("size: Int"),
      description = "Returns the number of elements in the collection.",
      category = Some("Property"),
      examples = List(
        "val numTokens = SELF.tokens.size"
      ),
      related = List("isEmpty", "nonEmpty", "Coll")
    ),
    "isEmpty" -> HoverInfo(
      signature = Some("def isEmpty: Boolean"),
      description = "Returns true if the collection contains no elements.",
      category = Some("Method"),
      related = List("nonEmpty", "size", "Coll")
    ),
    "nonEmpty" -> HoverInfo(
      signature = Some("def nonEmpty: Boolean"),
      description =
        "Returns true if the collection contains at least one element.",
      category = Some("Method"),
      related = List("isEmpty", "size", "Coll")
    ),
    "map" -> HoverInfo(
      signature = Some("def map[R](f: T => R): Coll[R]"),
      description =
        "Transforms each element of the collection using the provided function, returning a new collection with the transformed elements.",
      category = Some("Method"),
      examples = List(
        "val values = INPUTS.map((box) => box.value)"
      ),
      related = List("filter", "fold", "Coll")
    ),
    "filter" -> HoverInfo(
      signature = Some("def filter(p: T => Boolean): Coll[T]"),
      description =
        "Returns a new collection containing only the elements that satisfy the predicate function.",
      category = Some("Method"),
      examples = List(
        "val largeBoxes = OUTPUTS.filter((box) => box.value > 1000000)"
      ),
      related = List("map", "exists", "forall", "Coll")
    ),
    "exists" -> HoverInfo(
      signature = Some("def exists(p: T => Boolean): Boolean"),
      description =
        "Returns true if at least one element in the collection satisfies the predicate. Short-circuits on the first match.",
      category = Some("Method"),
      examples = List(
        "val hasToken = SELF.tokens.exists((t) => t._1 == tokenId)"
      ),
      related = List("forall", "filter", "Coll")
    ),
    "forall" -> HoverInfo(
      signature = Some("def forall(p: T => Boolean): Boolean"),
      description =
        "Returns true if all elements in the collection satisfy the predicate. Short-circuits on the first non-match.",
      category = Some("Method"),
      examples = List(
        "val allLargeBoxes = OUTPUTS.forall((box) => box.value >= 1000)"
      ),
      related = List("exists", "filter", "Coll")
    ),
    "fold" -> HoverInfo(
      signature = Some("def fold[R](initial: R, f: (R, T) => R): R"),
      description =
        "Folds (reduces) the collection from left to right using the provided function and initial accumulator value.",
      category = Some("Method"),
      examples = List(
        "val totalValue = INPUTS.fold(0L, (acc, box) => acc + box.value)"
      ),
      related = List("map", "filter", "Coll")
    ),
    "slice" -> HoverInfo(
      signature = Some("def slice(from: Int, until: Int): Coll[T]"),
      description =
        "Returns a sub-collection from index 'from' (inclusive) to index 'until' (exclusive).",
      category = Some("Method"),
      examples = List(
        "val firstThree = OUTPUTS.slice(0, 3)"
      ),
      related = List("Coll")
    ),

    // Types
    "Boolean" -> HoverInfo(
      signature = Some("type Boolean"),
      description =
        "Boolean type representing true or false values. Used for conditions and logical operations.",
      category = Some("Type"),
      examples = List(
        "val condition: Boolean = HEIGHT > 100"
      ),
      related = List("true", "false", "if")
    ),
    "Byte" -> HoverInfo(
      signature = Some("type Byte"),
      description = "8-bit signed integer type. Range: -128 to 127.",
      category = Some("Type"),
      related = List("Int", "Short", "Long")
    ),
    "Short" -> HoverInfo(
      signature = Some("type Short"),
      description = "16-bit signed integer type. Range: -32,768 to 32,767.",
      category = Some("Type"),
      related = List("Byte", "Int", "Long")
    ),
    "Int" -> HoverInfo(
      signature = Some("type Int"),
      description =
        "32-bit signed integer type. Range: -2,147,483,648 to 2,147,483,647.",
      category = Some("Type"),
      examples = List(
        "val deadline: Int = 100000"
      ),
      related = List("Long", "Short", "HEIGHT")
    ),
    "Long" -> HoverInfo(
      signature = Some("type Long"),
      description =
        "64-bit signed integer type. Used for ERG amounts (nanoERGs) and large numbers.",
      category = Some("Type"),
      examples = List(
        "val amount: Long = 1000000000L"
      ),
      related = List("Int", "BigInt", "value")
    ),
    "BigInt" -> HoverInfo(
      signature = Some("type BigInt"),
      description =
        "Arbitrary precision integer type. Can represent integers of any size, limited only by available memory.",
      category = Some("Type"),
      related = List("Long", "Int", "byteArrayToBigInt")
    ),
    "GroupElement" -> HoverInfo(
      signature = Some("type GroupElement"),
      description =
        "Represents a point on an elliptic curve. Used in cryptographic operations and for public keys.",
      category = Some("Type"),
      related = List("proveDlog", "proveDHTuple")
    ),
    "SigmaProp" -> HoverInfo(
      signature = Some("type SigmaProp"),
      description =
        "Represents a Sigma protocol proposition - a specification of what cryptographic proofs are required. ErgoScript contracts must evaluate to a SigmaProp.",
      category = Some("Type"),
      examples = List(
        "val prop: SigmaProp = sigmaProp(HEIGHT > 100)"
      ),
      related = List("sigmaProp", "proveDlog", "atLeast")
    ),
    "Box" -> HoverInfo(
      signature = Some("type Box"),
      description =
        "Represents a UTXO box in Ergo. Boxes contain value (ERG), tokens, and arbitrary data in registers. Every box has a guard script that must be satisfied to spend it.",
      category = Some("Type"),
      examples = List(
        "val myBox: Box = SELF",
        "val output: Box = OUTPUTS(0)"
      ),
      related = List("SELF", "OUTPUTS", "INPUTS", "value", "tokens")
    ),
    "Coll" -> HoverInfo(
      signature = Some("type Coll[T]"),
      description =
        "Collection type representing an immutable sequence of elements of type T. Provides functional operations like map, filter, fold.",
      category = Some("Type"),
      examples = List(
        "val boxes: Coll[Box] = OUTPUTS",
        "val bytes: Coll[Byte] = blake2b256(data)"
      ),
      related = List("map", "filter", "fold", "exists")
    ),
    "Option" -> HoverInfo(
      signature = Some("type Option[T]"),
      description =
        "Represents an optional value - either Some(value) or None. Used for box registers and values that may or may not be present.",
      category = Some("Type"),
      examples = List(
        "val maybeDeadline: Option[Int] = SELF.R4[Int]"
      ),
      related = List("get", "getOrElse", "isDefined", "R4")
    )
  )
}
