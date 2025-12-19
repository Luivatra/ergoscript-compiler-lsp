package org.ergoplatform.ergoscript.lsp.completion

import org.ergoplatform.ergoscript.lsp.jsonrpc.LspMessages.CompletionItem

/** Defines built-in symbols, keywords, and types for ErgoScript.
  *
  * This object provides completion items for all built-in language features.
  */
object ErgoScriptSymbols {

  // LSP Completion Item Kinds
  private val KeywordKind = 14
  private val FunctionKind = 3
  private val ConstantKind = 21
  private val PropertyKind = 10
  private val MethodKind = 2
  private val TypeKind = 7

  /** ErgoScript keywords */
  val keywords: List[CompletionItem] = List(
    CompletionItem(
      label = "val",
      kind = Some(KeywordKind),
      detail = Some("keyword"),
      documentation = Some("Declare an immutable value binding"),
      insertText = Some("val ${1:name} = ${2:value}")
    ),
    CompletionItem(
      label = "def",
      kind = Some(KeywordKind),
      detail = Some("keyword"),
      documentation = Some("Define a function"),
      insertText =
        Some("def ${1:name}(${2:params}): ${3:ReturnType} = ${4:body}")
    ),
    CompletionItem(
      label = "if",
      kind = Some(KeywordKind),
      detail = Some("keyword"),
      documentation = Some("Conditional expression"),
      insertText = Some("if (${1:condition}) ${2:trueCase} else ${3:falseCase}")
    ),
    CompletionItem(
      label = "true",
      kind = Some(KeywordKind),
      detail = Some("Boolean"),
      documentation = Some("Boolean true value"),
      insertText = Some("true")
    ),
    CompletionItem(
      label = "false",
      kind = Some(KeywordKind),
      detail = Some("Boolean"),
      documentation = Some("Boolean false value"),
      insertText = Some("false")
    )
  )

  /** Global constants available in ErgoScript */
  val globalConstants: List[CompletionItem] = List(
    CompletionItem(
      label = "SELF",
      kind = Some(ConstantKind),
      detail = Some("Box"),
      documentation = Some(
        "The current box being spent. Access its value, registers, and other properties."
      ),
      insertText = Some("SELF")
    ),
    CompletionItem(
      label = "HEIGHT",
      kind = Some(ConstantKind),
      detail = Some("Int"),
      documentation = Some(
        "Current blockchain height. Used for time-locked contracts."
      ),
      insertText = Some("HEIGHT")
    ),
    CompletionItem(
      label = "OUTPUTS",
      kind = Some(ConstantKind),
      detail = Some("Coll[Box]"),
      documentation = Some(
        "Collection of output boxes in the transaction. Access via OUTPUTS(index)."
      ),
      insertText = Some("OUTPUTS(${1:index})")
    ),
    CompletionItem(
      label = "INPUTS",
      kind = Some(ConstantKind),
      detail = Some("Coll[Box]"),
      documentation = Some(
        "Collection of input boxes in the transaction. Access via INPUTS(index)."
      ),
      insertText = Some("INPUTS(${1:index})")
    ),
    CompletionItem(
      label = "CONTEXT",
      kind = Some(ConstantKind),
      detail = Some("Context"),
      documentation = Some(
        "Transaction context containing blockchain state and transaction info."
      ),
      insertText = Some("CONTEXT")
    )
  )

  /** Built-in functions */
  val allFunctions: List[CompletionItem] = List(
    // Boolean logic functions
    CompletionItem(
      label = "sigmaProp",
      kind = Some(FunctionKind),
      detail = Some("Boolean => SigmaProp"),
      documentation = Some(
        "Convert a boolean condition to a Sigma proposition. This is the fundamental building block for ErgoScript contracts."
      ),
      insertText = Some("sigmaProp(${1:condition})")
    ),
    CompletionItem(
      label = "allOf",
      kind = Some(FunctionKind),
      detail = Some("Coll[Boolean] => Boolean"),
      documentation = Some(
        "Returns true if all boolean values in the collection are true."
      ),
      insertText = Some("allOf(${1:conditions})")
    ),
    CompletionItem(
      label = "anyOf",
      kind = Some(FunctionKind),
      detail = Some("Coll[Boolean] => Boolean"),
      documentation = Some(
        "Returns true if any boolean value in the collection is true."
      ),
      insertText = Some("anyOf(${1:conditions})")
    ),
    CompletionItem(
      label = "xorOf",
      kind = Some(FunctionKind),
      detail = Some("Coll[Boolean] => Boolean"),
      documentation = Some(
        "Returns true if an odd number of values in the collection are true (XOR)."
      ),
      insertText = Some("xorOf(${1:conditions})")
    ),

    // Cryptographic functions
    CompletionItem(
      label = "proveDlog",
      kind = Some(FunctionKind),
      detail = Some("GroupElement => SigmaProp"),
      documentation = Some(
        "Create a Sigma proposition requiring proof of discrete logarithm knowledge."
      ),
      insertText = Some("proveDlog(${1:groupElement})")
    ),
    CompletionItem(
      label = "proveDHTuple",
      kind = Some(FunctionKind),
      detail = Some(
        "(GroupElement, GroupElement, GroupElement, GroupElement) => SigmaProp"
      ),
      documentation = Some(
        "Create a Sigma proposition requiring proof of Diffie-Hellman tuple."
      ),
      insertText = Some("proveDHTuple(${1:g}, ${2:h}, ${3:u}, ${4:v})")
    ),
    CompletionItem(
      label = "atLeast",
      kind = Some(FunctionKind),
      detail = Some("(Int, Coll[SigmaProp]) => SigmaProp"),
      documentation = Some(
        "Create a threshold Sigma proposition requiring at least k of n signatures."
      ),
      insertText = Some("atLeast(${1:k}, ${2:props})")
    ),
    CompletionItem(
      label = "blake2b256",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => Coll[Byte]"),
      documentation = Some(
        "Compute BLAKE2b-256 hash of the input bytes."
      ),
      insertText = Some("blake2b256(${1:bytes})")
    ),
    CompletionItem(
      label = "sha256",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => Coll[Byte]"),
      documentation = Some(
        "Compute SHA-256 hash of the input bytes."
      ),
      insertText = Some("sha256(${1:bytes})")
    ),

    // Type conversion functions
    CompletionItem(
      label = "byteArrayToBigInt",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => BigInt"),
      documentation = Some(
        "Convert a byte array to a big integer."
      ),
      insertText = Some("byteArrayToBigInt(${1:bytes})")
    ),
    CompletionItem(
      label = "byteArrayToLong",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => Long"),
      documentation = Some(
        "Convert a byte array to a long value."
      ),
      insertText = Some("byteArrayToLong(${1:bytes})")
    ),
    CompletionItem(
      label = "longToByteArray",
      kind = Some(FunctionKind),
      detail = Some("Long => Coll[Byte]"),
      documentation = Some(
        "Convert a long value to a byte array."
      ),
      insertText = Some("longToByteArray(${1:value})")
    ),
    CompletionItem(
      label = "decodePoint",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => GroupElement"),
      documentation = Some(
        "Decode bytes to a group element (elliptic curve point)."
      ),
      insertText = Some("decodePoint(${1:bytes})")
    ),

    // Encoding/decoding functions
    CompletionItem(
      label = "fromBase16",
      kind = Some(FunctionKind),
      detail = Some("String => Coll[Byte]"),
      documentation = Some(
        "Decode a Base16 (hexadecimal) string to bytes."
      ),
      insertText = Some("fromBase16(${1:string})")
    ),
    CompletionItem(
      label = "fromBase58",
      kind = Some(FunctionKind),
      detail = Some("String => Coll[Byte]"),
      documentation = Some(
        "Decode a Base58 string to bytes."
      ),
      insertText = Some("fromBase58(${1:string})")
    ),
    CompletionItem(
      label = "fromBase64",
      kind = Some(FunctionKind),
      detail = Some("String => Coll[Byte]"),
      documentation = Some(
        "Decode a Base64 string to bytes."
      ),
      insertText = Some("fromBase64(${1:string})")
    ),
    CompletionItem(
      label = "PK",
      kind = Some(FunctionKind),
      detail = Some("String => SigmaProp"),
      documentation = Some(
        "Create a SigmaProp from a Base58-encoded public key string."
      ),
      insertText = Some("PK(${1:base58PublicKey})")
    ),

    // Serialization functions
    CompletionItem(
      label = "serialize",
      kind = Some(FunctionKind),
      detail = Some("T => Coll[Byte]"),
      documentation = Some(
        "Serialize a value to bytes."
      ),
      insertText = Some("serialize(${1:value})")
    ),
    CompletionItem(
      label = "deserializeTo",
      kind = Some(FunctionKind),
      detail = Some("Coll[Byte] => T"),
      documentation = Some(
        "Deserialize bytes to a value of the specified type."
      ),
      insertText = Some("deserializeTo[${1:Type}](${2:bytes})")
    ),

    // Utility functions
    CompletionItem(
      label = "getVar",
      kind = Some(FunctionKind),
      detail = Some("Int => Option[T]"),
      documentation = Some(
        "Get a context variable by its tag/index."
      ),
      insertText = Some("getVar[${1:Type}](${2:tag})")
    ),
    CompletionItem(
      label = "xor",
      kind = Some(FunctionKind),
      detail = Some("(Coll[Byte], Coll[Byte]) => Coll[Byte]"),
      documentation = Some(
        "Bitwise XOR of two byte collections."
      ),
      insertText = Some("xor(${1:left}, ${2:right})")
    ),
    CompletionItem(
      label = "groupGenerator",
      kind = Some(FunctionKind),
      detail = Some("GroupElement"),
      documentation = Some(
        "The generator of the cryptographic group."
      ),
      insertText = Some("groupGenerator")
    ),
    CompletionItem(
      label = "bigInt",
      kind = Some(FunctionKind),
      detail = Some("String => BigInt"),
      documentation = Some(
        "Create a BigInt from a Base16-encoded string."
      ),
      insertText = Some("bigInt(${1:base16String})")
    ),
    CompletionItem(
      label = "unsignedBigInt",
      kind = Some(FunctionKind),
      detail = Some("String => UnsignedBigInt"),
      documentation = Some(
        "Create an UnsignedBigInt from a Base16-encoded string."
      ),
      insertText = Some("unsignedBigInt(${1:base16String})")
    ),
    CompletionItem(
      label = "substConstants",
      kind = Some(FunctionKind),
      detail = Some("(Coll[Byte], Coll[Int], Coll[T]) => Coll[Byte]"),
      documentation = Some(
        "Substitute constants in a script bytecode at specified positions."
      ),
      insertText =
        Some("substConstants(${1:scriptBytes}, ${2:positions}, ${3:newValues})")
    )
  )

  /** Box type members (used for SELF, INPUTS(...), OUTPUTS(...)) */
  val boxMembers: List[CompletionItem] = List(
    CompletionItem(
      label = "value",
      kind = Some(PropertyKind),
      detail = Some("Long"),
      documentation = Some(
        "The amount of ERG (in nanoERGs) contained in this box."
      ),
      insertText = Some("value")
    ),
    CompletionItem(
      label = "propositionBytes",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some(
        "The serialized guard script (ErgoTree) of this box."
      ),
      insertText = Some("propositionBytes")
    ),
    CompletionItem(
      label = "bytes",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some(
        "The serialized representation of the entire box."
      ),
      insertText = Some("bytes")
    ),
    CompletionItem(
      label = "bytesWithoutRef",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some(
        "The serialized box without transaction reference."
      ),
      insertText = Some("bytesWithoutRef")
    ),
    CompletionItem(
      label = "id",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some(
        "The unique identifier (hash) of this box."
      ),
      insertText = Some("id")
    ),
    CompletionItem(
      label = "creationInfo",
      kind = Some(PropertyKind),
      detail = Some("(Int, Coll[Byte])"),
      documentation = Some(
        "Tuple of (height, txId) when this box was created."
      ),
      insertText = Some("creationInfo")
    ),
    CompletionItem(
      label = "tokens",
      kind = Some(PropertyKind),
      detail = Some("Coll[(Coll[Byte], Long)]"),
      documentation = Some(
        "Collection of token (tokenId, amount) pairs stored in this box."
      ),
      insertText = Some("tokens")
    ),
    CompletionItem(
      label = "R4",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R4. Access with type parameter, e.g., R4[Int].get"
      ),
      insertText = Some("R4[${1:Type}]")
    ),
    CompletionItem(
      label = "R5",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R5. Access with type parameter, e.g., R5[Long].get"
      ),
      insertText = Some("R5[${1:Type}]")
    ),
    CompletionItem(
      label = "R6",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R6. Access with type parameter, e.g., R6[Coll[Byte]].get"
      ),
      insertText = Some("R6[${1:Type}]")
    ),
    CompletionItem(
      label = "R7",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R7. Access with type parameter."
      ),
      insertText = Some("R7[${1:Type}]")
    ),
    CompletionItem(
      label = "R8",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R8. Access with type parameter."
      ),
      insertText = Some("R8[${1:Type}]")
    ),
    CompletionItem(
      label = "R9",
      kind = Some(PropertyKind),
      detail = Some("Option[T]"),
      documentation = Some(
        "Register R9. Access with type parameter."
      ),
      insertText = Some("R9[${1:Type}]")
    )
  )

  /** Context type members */
  val contextMembers: List[CompletionItem] = List(
    CompletionItem(
      label = "dataInputs",
      kind = Some(PropertyKind),
      detail = Some("Coll[Box]"),
      documentation = Some(
        "Read-only input boxes (data inputs) that can be referenced without being spent."
      ),
      insertText = Some("dataInputs")
    ),
    CompletionItem(
      label = "headers",
      kind = Some(PropertyKind),
      detail = Some("Coll[Header]"),
      documentation = Some(
        "Collection of previous block headers."
      ),
      insertText = Some("headers")
    ),
    CompletionItem(
      label = "preHeader",
      kind = Some(PropertyKind),
      detail = Some("PreHeader"),
      documentation = Some(
        "Information about the block being mined."
      ),
      insertText = Some("preHeader")
    ),
    CompletionItem(
      label = "minerPubKey",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some(
        "Public key of the miner who created the current block."
      ),
      insertText = Some("minerPubKey")
    ),
    CompletionItem(
      label = "LastBlockUtxoRootHash",
      kind = Some(PropertyKind),
      detail = Some("AvlTree"),
      documentation = Some(
        "Root hash of the UTXO set from the previous block."
      ),
      insertText = Some("LastBlockUtxoRootHash")
    ),
    CompletionItem(
      label = "getVar",
      kind = Some(MethodKind),
      detail = Some("Byte => Option[T]"),
      documentation = Some(
        "Get a context variable by ID."
      ),
      insertText = Some("getVar[${1:Type}](${2:id})")
    )
  )

  /** Members for Option types (like register access results) */
  val optionMembers: List[CompletionItem] = List(
    CompletionItem(
      label = "get",
      kind = Some(MethodKind),
      detail = Some("=> T"),
      documentation = Some(
        "Extract the value from an Option. Throws exception if None."
      ),
      insertText = Some("get")
    ),
    CompletionItem(
      label = "getOrElse",
      kind = Some(MethodKind),
      detail = Some("T => T"),
      documentation = Some(
        "Get the value if present, otherwise return the default value."
      ),
      insertText = Some("getOrElse(${1:default})")
    ),
    CompletionItem(
      label = "isDefined",
      kind = Some(MethodKind),
      detail = Some("=> Boolean"),
      documentation = Some(
        "Returns true if the Option contains a value."
      ),
      insertText = Some("isDefined")
    ),
    CompletionItem(
      label = "isEmpty",
      kind = Some(MethodKind),
      detail = Some("=> Boolean"),
      documentation = Some(
        "Returns true if the Option is None."
      ),
      insertText = Some("isEmpty")
    ),
    CompletionItem(
      label = "map",
      kind = Some(MethodKind),
      detail = Some("(T => R) => Option[R]"),
      documentation = Some(
        "Transform the value inside the Option using the given function."
      ),
      insertText = Some("map { ${1:x} => ${2:transformation} }")
    ),
    CompletionItem(
      label = "filter",
      kind = Some(MethodKind),
      detail = Some("(T => Boolean) => Option[T]"),
      documentation = Some(
        "Return the Option if it satisfies the predicate, otherwise None."
      ),
      insertText = Some("filter { ${1:x} => ${2:condition} }")
    )
  )

  /** Collection methods (for Coll[T] types) */
  val collectionMembers: List[CompletionItem] = List(
    // Properties
    CompletionItem(
      label = "size",
      kind = Some(PropertyKind),
      detail = Some("Int"),
      documentation = Some("The number of elements in the collection."),
      insertText = Some("size")
    ),
    CompletionItem(
      label = "indices",
      kind = Some(PropertyKind),
      detail = Some("Coll[Int]"),
      documentation = Some(
        "A collection of valid indices for this collection (0 until size)."
      ),
      insertText = Some("indices")
    ),

    // Predicates
    CompletionItem(
      label = "isEmpty",
      kind = Some(MethodKind),
      detail = Some("=> Boolean"),
      documentation = Some("Returns true if the collection is empty."),
      insertText = Some("isEmpty")
    ),
    CompletionItem(
      label = "nonEmpty",
      kind = Some(MethodKind),
      detail = Some("=> Boolean"),
      documentation = Some("Returns true if the collection is not empty."),
      insertText = Some("nonEmpty")
    ),
    CompletionItem(
      label = "startsWith",
      kind = Some(MethodKind),
      detail = Some("Coll[T] => Boolean"),
      documentation = Some(
        "Returns true if this collection starts with the given collection."
      ),
      insertText = Some("startsWith(${1:prefix})")
    ),
    CompletionItem(
      label = "endsWith",
      kind = Some(MethodKind),
      detail = Some("Coll[T] => Boolean"),
      documentation =
        Some("Returns true if this collection ends with the given collection."),
      insertText = Some("endsWith(${1:suffix})")
    ),

    // Transformation methods
    CompletionItem(
      label = "map",
      kind = Some(MethodKind),
      detail = Some("(T => R) => Coll[R]"),
      documentation = Some("Transform each element using the given function."),
      insertText = Some("map { ${1:x} => ${2:transformation} }")
    ),
    CompletionItem(
      label = "flatMap",
      kind = Some(MethodKind),
      detail = Some("(T => Coll[R]) => Coll[R]"),
      documentation =
        Some("Map each element to a collection and flatten the results."),
      insertText = Some("flatMap { ${1:x} => ${2:collectionExpression} }")
    ),
    CompletionItem(
      label = "filter",
      kind = Some(MethodKind),
      detail = Some("(T => Boolean) => Coll[T]"),
      documentation = Some("Keep only elements that satisfy the predicate."),
      insertText = Some("filter { ${1:x} => ${2:condition} }")
    ),
    CompletionItem(
      label = "fold",
      kind = Some(MethodKind),
      detail = Some("(R, (R, T) => R) => R"),
      documentation = Some(
        "Fold the collection from left to right with an accumulator."
      ),
      insertText =
        Some("fold(${1:initial}) { (${2:acc}, ${3:x}) => ${4:body} }")
    ),

    // Predicates on elements
    CompletionItem(
      label = "exists",
      kind = Some(MethodKind),
      detail = Some("(T => Boolean) => Boolean"),
      documentation =
        Some("Returns true if any element satisfies the predicate."),
      insertText = Some("exists { ${1:x} => ${2:condition} }")
    ),
    CompletionItem(
      label = "forall",
      kind = Some(MethodKind),
      detail = Some("(T => Boolean) => Boolean"),
      documentation =
        Some("Returns true if all elements satisfy the predicate."),
      insertText = Some("forall { ${1:x} => ${2:condition} }")
    ),

    // Access methods
    CompletionItem(
      label = "apply",
      kind = Some(MethodKind),
      detail = Some("Int => T"),
      documentation = Some("Get element at index (same as coll(i))."),
      insertText = Some("apply(${1:index})")
    ),
    CompletionItem(
      label = "get",
      kind = Some(MethodKind),
      detail = Some("Int => Option[T]"),
      documentation = Some("Safely get element at index, returning Option."),
      insertText = Some("get(${1:index})")
    ),
    CompletionItem(
      label = "getOrElse",
      kind = Some(MethodKind),
      detail = Some("(Int, T) => T"),
      documentation =
        Some("Get element at index or return default if out of bounds."),
      insertText = Some("getOrElse(${1:index}, ${2:default})")
    ),
    CompletionItem(
      label = "indexOf",
      kind = Some(MethodKind),
      detail = Some("(T, Int) => Int"),
      documentation = Some(
        "Find the index of first occurrence of element, starting from given index."
      ),
      insertText = Some("indexOf(${1:elem}, ${2:from})")
    ),

    // Manipulation methods
    CompletionItem(
      label = "slice",
      kind = Some(MethodKind),
      detail = Some("(Int, Int) => Coll[T]"),
      documentation =
        Some("Extract a sub-collection from index 'from' to index 'until'."),
      insertText = Some("slice(${1:from}, ${2:until})")
    ),
    CompletionItem(
      label = "append",
      kind = Some(MethodKind),
      detail = Some("Coll[T] => Coll[T]"),
      documentation =
        Some("Concatenate this collection with another (same as ++)."),
      insertText = Some("append(${1:other})")
    ),
    CompletionItem(
      label = "zip",
      kind = Some(MethodKind),
      detail = Some("Coll[B] => Coll[(T, B)]"),
      documentation =
        Some("Combine two collections into a collection of pairs."),
      insertText = Some("zip(${1:other})")
    ),
    CompletionItem(
      label = "patch",
      kind = Some(MethodKind),
      detail = Some("(Int, Coll[T], Int) => Coll[T]"),
      documentation = Some(
        "Replace 'replaced' elements starting at 'from' with elements from 'patch'."
      ),
      insertText = Some("patch(${1:from}, ${2:patch}, ${3:replaced})")
    ),
    CompletionItem(
      label = "updated",
      kind = Some(MethodKind),
      detail = Some("(Int, T) => Coll[T]"),
      documentation =
        Some("Create a new collection with element at index replaced."),
      insertText = Some("updated(${1:index}, ${2:elem})")
    ),
    CompletionItem(
      label = "updateMany",
      kind = Some(MethodKind),
      detail = Some("(Coll[Int], Coll[T]) => Coll[T]"),
      documentation = Some("Update multiple elements at specified indices."),
      insertText = Some("updateMany(${1:indexes}, ${2:values})")
    )
  )

  /** Numeric type methods (for Byte, Short, Int, Long, BigInt) */
  val numericMembers: List[CompletionItem] = List(
    // Type conversions
    CompletionItem(
      label = "toByte",
      kind = Some(MethodKind),
      detail = Some("=> Byte"),
      documentation = Some("Convert to Byte type."),
      insertText = Some("toByte")
    ),
    CompletionItem(
      label = "toShort",
      kind = Some(MethodKind),
      detail = Some("=> Short"),
      documentation = Some("Convert to Short type."),
      insertText = Some("toShort")
    ),
    CompletionItem(
      label = "toInt",
      kind = Some(MethodKind),
      detail = Some("=> Int"),
      documentation = Some("Convert to Int type."),
      insertText = Some("toInt")
    ),
    CompletionItem(
      label = "toLong",
      kind = Some(MethodKind),
      detail = Some("=> Long"),
      documentation = Some("Convert to Long type."),
      insertText = Some("toLong")
    ),
    CompletionItem(
      label = "toBigInt",
      kind = Some(MethodKind),
      detail = Some("=> BigInt"),
      documentation = Some("Convert to BigInt type."),
      insertText = Some("toBigInt")
    ),
    CompletionItem(
      label = "toBytes",
      kind = Some(MethodKind),
      detail = Some("=> Coll[Byte]"),
      documentation = Some("Convert to byte collection."),
      insertText = Some("toBytes")
    ),
    CompletionItem(
      label = "toBits",
      kind = Some(MethodKind),
      detail = Some("=> Coll[Boolean]"),
      documentation = Some("Convert to bit collection."),
      insertText = Some("toBits")
    ),

    // Bitwise operations
    CompletionItem(
      label = "bitwiseInverse",
      kind = Some(MethodKind),
      detail = Some("=> T"),
      documentation = Some("Bitwise NOT operation (~)."),
      insertText = Some("bitwiseInverse")
    ),
    CompletionItem(
      label = "bitwiseOr",
      kind = Some(MethodKind),
      detail = Some("T => T"),
      documentation = Some("Bitwise OR operation (|)."),
      insertText = Some("bitwiseOr(${1:other})")
    ),
    CompletionItem(
      label = "bitwiseAnd",
      kind = Some(MethodKind),
      detail = Some("T => T"),
      documentation = Some("Bitwise AND operation (&)."),
      insertText = Some("bitwiseAnd(${1:other})")
    ),
    CompletionItem(
      label = "bitwiseXor",
      kind = Some(MethodKind),
      detail = Some("T => T"),
      documentation = Some("Bitwise XOR operation (^)."),
      insertText = Some("bitwiseXor(${1:other})")
    ),
    CompletionItem(
      label = "shiftLeft",
      kind = Some(MethodKind),
      detail = Some("Int => T"),
      documentation =
        Some("Shift bits left by specified number of positions (<<)."),
      insertText = Some("shiftLeft(${1:bits})")
    ),
    CompletionItem(
      label = "shiftRight",
      kind = Some(MethodKind),
      detail = Some("Int => T"),
      documentation =
        Some("Shift bits right by specified number of positions (>>)."),
      insertText = Some("shiftRight(${1:bits})")
    )
  )

  /** AvlTree type members */
  val avlTreeMembers: List[CompletionItem] = List(
    CompletionItem(
      label = "digest",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some("The digest (root hash) of the AvlTree."),
      insertText = Some("digest")
    ),
    CompletionItem(
      label = "enabledOperations",
      kind = Some(PropertyKind),
      detail = Some("Byte"),
      documentation =
        Some("Bit flags indicating which operations are enabled."),
      insertText = Some("enabledOperations")
    ),
    CompletionItem(
      label = "keyLength",
      kind = Some(PropertyKind),
      detail = Some("Int"),
      documentation = Some("Length of keys in bytes."),
      insertText = Some("keyLength")
    ),
    CompletionItem(
      label = "valueLengthOpt",
      kind = Some(PropertyKind),
      detail = Some("Option[Int]"),
      documentation = Some("Optional fixed length of values in bytes."),
      insertText = Some("valueLengthOpt")
    ),
    CompletionItem(
      label = "isInsertAllowed",
      kind = Some(PropertyKind),
      detail = Some("Boolean"),
      documentation = Some("True if insert operations are allowed."),
      insertText = Some("isInsertAllowed")
    ),
    CompletionItem(
      label = "isUpdateAllowed",
      kind = Some(PropertyKind),
      detail = Some("Boolean"),
      documentation = Some("True if update operations are allowed."),
      insertText = Some("isUpdateAllowed")
    ),
    CompletionItem(
      label = "isRemoveAllowed",
      kind = Some(PropertyKind),
      detail = Some("Boolean"),
      documentation = Some("True if remove operations are allowed."),
      insertText = Some("isRemoveAllowed")
    ),
    CompletionItem(
      label = "contains",
      kind = Some(MethodKind),
      detail = Some("(Coll[Byte], Coll[Byte]) => Boolean"),
      documentation = Some("Check if tree contains a key with given proof."),
      insertText = Some("contains(${1:key}, ${2:proof})")
    ),
    CompletionItem(
      label = "get",
      kind = Some(MethodKind),
      detail = Some("(Coll[Byte], Coll[Byte]) => Option[Coll[Byte]]"),
      documentation = Some("Get value for key with given proof."),
      insertText = Some("get(${1:key}, ${2:proof})")
    ),
    CompletionItem(
      label = "getMany",
      kind = Some(MethodKind),
      detail =
        Some("(Coll[Coll[Byte]], Coll[Byte]) => Coll[Option[Coll[Byte]]]"),
      documentation = Some("Get multiple values with a single proof."),
      insertText = Some("getMany(${1:keys}, ${2:proof})")
    ),
    CompletionItem(
      label = "insert",
      kind = Some(MethodKind),
      detail =
        Some("(Coll[(Coll[Byte], Coll[Byte])], Coll[Byte]) => Option[AvlTree]"),
      documentation = Some("Insert key-value pairs with proof."),
      insertText = Some("insert(${1:operations}, ${2:proof})")
    ),
    CompletionItem(
      label = "update",
      kind = Some(MethodKind),
      detail =
        Some("(Coll[(Coll[Byte], Coll[Byte])], Coll[Byte]) => Option[AvlTree]"),
      documentation = Some("Update key-value pairs with proof."),
      insertText = Some("update(${1:operations}, ${2:proof})")
    ),
    CompletionItem(
      label = "remove",
      kind = Some(MethodKind),
      detail = Some("(Coll[Coll[Byte]], Coll[Byte]) => Option[AvlTree]"),
      documentation = Some("Remove keys with proof."),
      insertText = Some("remove(${1:keys}, ${2:proof})")
    )
  )

  /** SigmaProp type members */
  val sigmaPropMembers: List[CompletionItem] = List(
    CompletionItem(
      label = "propBytes",
      kind = Some(PropertyKind),
      detail = Some("Coll[Byte]"),
      documentation = Some("Serialized bytes of the Sigma proposition."),
      insertText = Some("propBytes")
    )
  )

  /** Common members that might apply to multiple types */
  val commonMembers: List[CompletionItem] =
    optionMembers ::: collectionMembers

  /** Legacy getter members (for backward compatibility) */
  val getterMembers: List[CompletionItem] = optionMembers

  /** ErgoScript data types */
  val types: List[CompletionItem] = List(
    CompletionItem(
      label = "Unit",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Unit type with single value ()"),
      insertText = Some("Unit")
    ),
    CompletionItem(
      label = "Boolean",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Boolean type (true or false)"),
      insertText = Some("Boolean")
    ),
    CompletionItem(
      label = "Byte",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("8-bit signed integer"),
      insertText = Some("Byte")
    ),
    CompletionItem(
      label = "Short",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("16-bit signed integer"),
      insertText = Some("Short")
    ),
    CompletionItem(
      label = "Int",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("32-bit signed integer"),
      insertText = Some("Int")
    ),
    CompletionItem(
      label = "Long",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("64-bit signed integer"),
      insertText = Some("Long")
    ),
    CompletionItem(
      label = "BigInt",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Arbitrary precision signed integer"),
      insertText = Some("BigInt")
    ),
    CompletionItem(
      label = "UnsignedBigInt",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation =
        Some("Arbitrary precision unsigned integer (ErgoTree v3+)"),
      insertText = Some("UnsignedBigInt")
    ),
    CompletionItem(
      label = "GroupElement",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Elliptic curve point (group element)"),
      insertText = Some("GroupElement")
    ),
    CompletionItem(
      label = "SigmaProp",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Sigma proposition (proof specification)"),
      insertText = Some("SigmaProp")
    ),
    CompletionItem(
      label = "AvlTree",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Authenticated dictionary using AVL tree"),
      insertText = Some("AvlTree")
    ),
    CompletionItem(
      label = "Box",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("UTXO box containing value and data"),
      insertText = Some("Box")
    ),
    CompletionItem(
      label = "Header",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Block header type"),
      insertText = Some("Header")
    ),
    CompletionItem(
      label = "PreHeader",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Pre-header information for block being mined"),
      insertText = Some("PreHeader")
    ),
    CompletionItem(
      label = "Context",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Transaction and blockchain context"),
      insertText = Some("Context")
    ),
    CompletionItem(
      label = "Coll",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Collection type, e.g., Coll[Byte], Coll[Int]"),
      insertText = Some("Coll[${1:Type}]")
    ),
    CompletionItem(
      label = "Option",
      kind = Some(TypeKind),
      detail = Some("type"),
      documentation = Some("Optional value, may be Some(value) or None"),
      insertText = Some("Option[${1:Type}]")
    )
  )
}
