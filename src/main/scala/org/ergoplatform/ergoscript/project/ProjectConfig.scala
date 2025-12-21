package org.ergoplatform.ergoscript.project

/** ErgoScript project configuration.
  *
  * @param name
  *   Project name
  * @param version
  *   Project version
  * @param description
  *   Project description
  * @param ergoscript
  *   ErgoScript-specific settings
  * @param directories
  *   Directory configuration
  * @param constants
  *   Project-level constants
  * @param compile
  *   Compilation configuration
  * @param test
  *   Test configuration
  */
case class ProjectConfig(
    name: String,
    version: String,
    description: Option[String] = None,
    ergoscript: ErgoScriptConfig = ErgoScriptConfig(),
    directories: DirectoryConfig = DirectoryConfig(),
    constants: Map[String, ConstantDefinition] = Map.empty,
    compile: Option[CompileConfig] = None,
    test: Option[TestConfig] = None
)

/** ErgoScript-specific configuration.
  *
  * @param version
  *   ErgoScript/Sigma version (e.g., "6.0")
  * @param network
  *   Network type (mainnet/testnet)
  */
case class ErgoScriptConfig(
    version: String = "6.0",
    network: String = "mainnet"
) {
  def networkPrefix: Byte = network.toLowerCase match {
    case "mainnet" => 0x00.toByte
    case "testnet" => 0x10.toByte
    case _         => 0x00.toByte
  }
}

/** Directory configuration for project structure.
  *
  * @param source
  *   Source files directory
  * @param lib
  *   Library files directory
  * @param output
  *   Compiled output directory
  * @param tests
  *   Test files directory
  */
case class DirectoryConfig(
    source: String = "src",
    lib: String = "lib",
    output: String = "build",
    tests: String = "tests"
)

/** Constant definition with type information.
  *
  * @param constantType
  *   The ErgoScript type (Long, Int, Coll[Byte], Address, etc.)
  * @param value
  *   The constant value (can be literal or env:VAR_NAME)
  * @param description
  *   Optional description
  */
case class ConstantDefinition(
    constantType: String,
    value: String,
    description: Option[String] = None
)

/** Compilation configuration.
  *
  * @param contracts
  *   List of contracts to compile
  */
case class CompileConfig(
    contracts: List[ContractCompileTarget] = List.empty
)

/** Contract compilation target.
  *
  * @param name
  *   Contract name
  * @param source
  *   Source file path (relative to project root)
  * @param output
  *   Output file path (relative to output directory)
  */
case class ContractCompileTarget(
    name: String,
    source: String,
    output: String
)

/** Test configuration.
  *
  * @param enabled
  *   Whether tests are enabled
  * @param parallel
  *   Whether to run tests in parallel
  * @param timeout
  *   Test timeout in milliseconds
  */
case class TestConfig(
    enabled: Boolean = true,
    parallel: Boolean = false,
    timeout: Long = 30000L
)
