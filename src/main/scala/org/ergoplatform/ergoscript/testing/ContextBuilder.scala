package org.ergoplatform.ergoscript.testing

import org.ergoplatform._
import sigma.ast.ErgoTree
import sigma.data.{AvlTreeData, CSigmaDslBuilder}
import sigma.{Colls, Header, PreHeader}
import sigma.interpreter.ContextExtension
import org.ergoplatform.validation.ValidationRules
import scorex.util.encode.Base16
import scorex.crypto.hash.Blake2b256
import sigmastate.eval.CPreHeader

import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

/** Builds ErgoLikeContext from MockContext for test execution.
  *
  * This is a working implementation that creates ErgoLikeContext for basic
  * testing. Some advanced features (complex tokens, custom preheaders) may have
  * limitations due to type conversions in the sigma library.
  */
object ContextBuilder extends LazyLogging {

  /** Build an ErgoLikeContext from a MockContext.
    *
    * @param mockContext
    *   The mock context from test definition
    * @param contractTree
    *   Optional contract ErgoTree to use for SELF box
    * @return
    *   ErgoLikeContext ready for evaluation
    */
  def build(
      mockContext: MockContext,
      contractTree: Option[ErgoTree] = None
  ): Try[ErgoLikeContext] = Try {
    // Convert mock boxes to ErgoBox
    val inputBoxes = mockContext.inputs.zipWithIndex.map {
      case (mockBox, idx) =>
        convertToErgoBox(mockBox, contractTree, idx)
    }

    val outputBoxes = mockContext.outputs.zipWithIndex.map {
      case (mockBox, idx) =>
        convertToErgoBox(mockBox, None, idx + 1000)
    }

    val dataInputBoxes = mockContext.dataInputs.zipWithIndex.map {
      case (mockBox, idx) =>
        convertToErgoBox(mockBox, None, idx + 2000)
    }

    // Find SELF index
    val selfIndex = inputBoxes.indexWhere { box =>
      mockContext.self.id.isEmpty ||
      mockContext.self.id.exists(id => boxIdMatches(box, id))
    }

    if (selfIndex < 0) {
      throw new IllegalArgumentException(
        "SELF box not found in INPUTS. Ensure SELF is included in INPUTS list."
      )
    }

    // Build transaction
    val tx = buildTransaction(inputBoxes, outputBoxes, dataInputBoxes)

    // Build pre-header
    val preHeaderValue = mockContext.preHeader
      .map(buildPreHeader)
      .getOrElse(buildDefaultPreHeader(mockContext.height.toInt))

    // Create ErgoLikeContext
    val baseContext = new ErgoLikeContext(
      lastBlockUtxoRoot = AvlTreeData.dummy,
      headers = Colls.emptyColl[Header],
      preHeader = preHeaderValue,
      dataBoxes = dataInputBoxes.toIndexedSeq,
      boxesToSpend = inputBoxes.toIndexedSeq,
      spendingTransaction = tx,
      selfIndex = selfIndex,
      extension = ContextExtension.empty,
      validationSettings = ValidationRules.currentSettings,
      costLimit = 1000000L, // Reasonable cost limit that fits in Int
      initCost = 0L,
      activatedScriptVersion = 3.toByte // Support ErgoTree v3
    )

    // Set the ErgoTree version from the contract if available
    val contextWithVersion = contractTree match {
      case Some(tree) => baseContext.withErgoTreeVersion(tree.version)
      case None =>
        baseContext.withErgoTreeVersion(3.toByte) // Default to version 3
    }

    contextWithVersion
  }

  /** Convert MockBox to ErgoBox.
    */
  private def convertToErgoBox(
      mockBox: MockBox,
      contractTree: Option[ErgoTree],
      idSeed: Int
  ): ErgoBox = {
    // Generate box ID
    val boxIdBytes = mockBox.id match {
      case Some(id) =>
        Base16.decode(id).getOrElse(Blake2b256.hash(id.getBytes))
      case None =>
        Blake2b256.hash(s"box_$idSeed".getBytes)
    }

    // Get proposition (contract script)
    val proposition = contractTree.getOrElse {
      mockBox.propositionBytes match {
        case Some(bytes) =>
          Try {
            val decoded = Base16.decode(bytes).get
            sigma.serialization.ErgoTreeSerializer.DefaultSerializer
              .deserializeErgoTree(decoded)
          }.getOrElse(createTrueTree())
        case None => createTrueTree()
      }
    }

    // Convert registers
    val additionalRegisters = convertRegisters(mockBox.registers)

    // Create ErgoBox with empty tokens for now (tokens require complex type conversions)
    // Users can add token support by extending this implementation
    new ErgoBox(
      value = mockBox.value,
      ergoTree = proposition,
      additionalTokens = Colls.emptyColl,
      additionalRegisters = additionalRegisters,
      transactionId = scorex.util.ModifierId @@ Base16.encode(boxIdBytes),
      index = idSeed.toShort,
      creationHeight = mockBox.creationHeight.getOrElse(0)
    )
  }

  /** Create a simple always-true ErgoTree.
    */
  private def createTrueTree(): ErgoTree = {
    import sigma.ast._
    ErgoTree.fromProposition(
      ErgoTree.HeaderType @@ ErgoTree.VersionFlag,
      TrueLeaf.toSigmaProp
    )
  }

  /** Check if box ID matches.
    */
  private def boxIdMatches(box: ErgoBox, id: String): Boolean = {
    Try {
      val idBytes = Base16.decode(id).getOrElse(return false)
      java.util.Arrays.equals(box.id, idBytes)
    }.getOrElse(false)
  }

  /** Convert register values to ErgoBox format.
    */
  private def convertRegisters(
      registers: Map[String, Any]
  ): Map[ErgoBox.NonMandatoryRegisterId, _ <: sigma.ast.EvaluatedValue[
    _ <: sigma.ast.SType
  ]] = {
    import sigma.ast._

    registers.flatMap { case (regName, value) =>
      val registerIdOpt = regName match {
        case "R4" => Some(ErgoBox.R4)
        case "R5" => Some(ErgoBox.R5)
        case "R6" => Some(ErgoBox.R6)
        case "R7" => Some(ErgoBox.R7)
        case "R8" => Some(ErgoBox.R8)
        case "R9" => Some(ErgoBox.R9)
        case _    => None
      }

      registerIdOpt.flatMap { registerId =>
        convertToErgoValue(value).map(v => registerId -> v)
      }
    }
  }

  /** Convert a value to ErgoValue.
    */
  private def convertToErgoValue(
      value: Any
  ): Option[sigma.ast.EvaluatedValue[_ <: sigma.ast.SType]] = {
    import sigma.ast._

    Try {
      value match {
        case i: Int =>
          IntConstant(i)
        case l: Long =>
          LongConstant(l)
        case s: String if s.startsWith("0x") =>
          val bytes = Base16.decode(s.substring(2)).get
          ByteArrayConstant(bytes)
        case s: String =>
          ByteArrayConstant(s.getBytes)
        case b: Boolean =>
          BooleanConstant(b)
        case _ =>
          ByteArrayConstant(value.toString.getBytes)
      }
    }.toOption
  }

  /** Build an ErgoLikeTransaction from inputs and outputs.
    */
  private def buildTransaction(
      inputs: List[ErgoBox],
      outputs: List[ErgoBox],
      dataInputs: List[ErgoBox]
  ): ErgoLikeTransaction = {
    val inputIds = inputs.map(box => new Input(box.id, emptyProverResult))
    val dataInputIds = dataInputs.map(box => new DataInput(box.id))

    new ErgoLikeTransaction(
      inputs = inputIds.toIndexedSeq,
      dataInputs = dataInputIds.toIndexedSeq,
      outputCandidates = outputs.toIndexedSeq
    )
  }

  /** Empty prover result for unsigned transactions in tests.
    */
  private val emptyProverResult =
    new sigma.interpreter.ProverResult(Array.empty, ContextExtension.empty)

  /** Build a PreHeader from MockPreHeader.
    */
  private def buildPreHeader(mockPreHeader: MockPreHeader): PreHeader = {
    val parentIdBytes = Base16.decode(mockPreHeader.parentId).get
    val minerPkBytes = Base16.decode(mockPreHeader.minerPk).get
    val votesBytes = Base16.decode(mockPreHeader.votes).get

    // Create GroupElement from bytes
    val minerPk = CSigmaDslBuilder.decodePoint(Colls.fromArray(minerPkBytes))

    CPreHeader(
      version = mockPreHeader.version,
      parentId = Colls.fromArray(parentIdBytes),
      timestamp = mockPreHeader.timestamp,
      nBits = mockPreHeader.nBits,
      height = mockPreHeader.height,
      minerPk = minerPk,
      votes = Colls.fromArray(votesBytes)
    )
  }

  /** Build a default PreHeader.
    */
  private def buildDefaultPreHeader(height: Int): PreHeader = {
    val dummyIdBytes = Array.fill(32)(0: Byte)
    val dummyPkBytes = Array.fill(33)(2: Byte) // Valid point encoding
    val votesBytes = Array.fill(3)(0: Byte)

    // Create dummy GroupElement
    val minerPk = CSigmaDslBuilder.decodePoint(Colls.fromArray(dummyPkBytes))

    CPreHeader(
      version = 2,
      parentId = Colls.fromArray(dummyIdBytes),
      timestamp = System.currentTimeMillis(),
      nBits = 117440512L,
      height = height,
      minerPk = minerPk,
      votes = Colls.fromArray(votesBytes)
    )
  }
}
