package org.ergoplatform.ergoscript.lsp.completion

import org.ergoplatform.ergoscript.lsp.jsonrpc.LspMessages._
import com.typesafe.scalalogging.LazyLogging

/** Provides code completion for ErgoScript.
  *
  * This class analyzes the document context at a given position and generates
  * appropriate completion suggestions based on the language semantics.
  */
class CompletionProvider extends LazyLogging {

  /** Generate completion items for the given document and position.
    *
    * @param documentText
    *   The full text of the document
    * @param position
    *   The cursor position (0-based line and character)
    * @param triggerCharacter
    *   The character that triggered completion, if any
    * @return
    *   A list of completion items
    */
  def complete(
      documentText: String,
      position: Position,
      triggerCharacter: Option[String]
  ): CompletionList = {
    logger.debug(
      s"Completion requested at ${position.line}:${position.character}, trigger: $triggerCharacter"
    )

    val context =
      extractContext(documentText, position, triggerCharacter)

    val items = context match {
      case MemberAccessContext(prefix) =>
        logger.debug(s"Member access context for: $prefix")
        getMemberCompletions(prefix)

      case FunctionCallContext =>
        logger.debug("Function call context")
        getFunctionCompletions()

      case GeneralContext =>
        logger.debug("General context")
        getGeneralCompletions()
    }

    CompletionList(
      isIncomplete = false,
      items = items
    )
  }

  /** Extract the completion context from the document.
    *
    * Determines what kind of completion is needed based on the text before the
    * cursor position.
    */
  private def extractContext(
      documentText: String,
      position: Position,
      triggerCharacter: Option[String]
  ): CompletionContext = {
    val lines = documentText.split("\n")

    if (position.line >= lines.length) {
      return GeneralContext
    }

    val currentLine = lines(position.line)
    val textBeforeCursor = currentLine.take(position.character)

    logger.debug(s"Text before cursor: '$textBeforeCursor'")

    // Check if this is a member access (triggered by '.')
    if (triggerCharacter.contains(".")) {
      // Extract the identifier before the dot
      val memberPattern = """(\w+)\s*\.\s*$""".r
      memberPattern.findFirstMatchIn(textBeforeCursor) match {
        case Some(m) =>
          return MemberAccessContext(m.group(1))
        case None =>
          // Check for chained member access like SELF.R4[Int].get.
          val chainedPattern = """(\w+(?:\.\w+|\[\w+\]|\(.*?\))*)\.\s*$""".r
          chainedPattern.findFirstMatchIn(textBeforeCursor) match {
            case Some(m) =>
              return MemberAccessContext(m.group(1))
            case None =>
              logger.debug("Could not extract member access prefix")
          }
      }
    }

    // Check if this is a function call context (triggered by '(')
    if (triggerCharacter.contains("(")) {
      return FunctionCallContext
    }

    // Check if we're in the middle of typing an identifier that might be member access
    val partialMemberPattern = """(\w+)\s*\.\s*(\w*)$""".r
    partialMemberPattern.findFirstMatchIn(textBeforeCursor) match {
      case Some(m) =>
        return MemberAccessContext(m.group(1))
      case None =>
        // Default to general context
        GeneralContext
    }
  }

  /** Get completions for member access (e.g., SELF., OUTPUTS(0)., etc.)
    */
  private def getMemberCompletions(prefix: String): List[CompletionItem] = {
    prefix match {
      // CONTEXT has its own members
      case "CONTEXT" => ErgoScriptSymbols.contextMembers

      // SELF is a Box
      case "SELF" => ErgoScriptSymbols.boxMembers

      // OUTPUTS(...) and INPUTS(...) return Box
      case s if s.startsWith("OUTPUTS(") || s.startsWith("INPUTS(") =>
        ErgoScriptSymbols.boxMembers

      // Register access returns Option (e.g., SELF.R4[Int])
      case s if s.contains(".R") && s.contains("[") =>
        ErgoScriptSymbols.optionMembers

      // AvlTree members (e.g., CONTEXT.LastBlockUtxoRootHash.)
      case s if s.contains("LastBlockUtxoRootHash") || s.contains("AvlTree") =>
        ErgoScriptSymbols.avlTreeMembers

      // SigmaProp members
      case s
          if s.toLowerCase
            .contains("sigmaprop") || s.toLowerCase.contains("prop") =>
        ErgoScriptSymbols.sigmaPropMembers

      // Numeric types get numeric members
      case s
          if s.matches(
            ".*\\d+[LB]?"
          ) => // Ends with a number (possibly with L or B suffix)
        ErgoScriptSymbols.numericMembers

      // Default: provide common members (collections and options)
      case _ =>
        ErgoScriptSymbols.commonMembers
    }
  }

  /** Get completions for function call context
    */
  private def getFunctionCompletions(): List[CompletionItem] = {
    // For now, just return all functions
    // In a more sophisticated implementation, we could analyze the function
    // being called and provide parameter hints
    ErgoScriptSymbols.allFunctions
  }

  /** Get general completions (keywords, functions, global values)
    */
  private def getGeneralCompletions(): List[CompletionItem] = {
    ErgoScriptSymbols.keywords :::
      ErgoScriptSymbols.globalConstants :::
      ErgoScriptSymbols.allFunctions :::
      ErgoScriptSymbols.types
  }

  /** Sealed trait representing different completion contexts
    */
  private sealed trait CompletionContext
  private case class MemberAccessContext(prefix: String)
      extends CompletionContext
  private case object FunctionCallContext extends CompletionContext
  private case object GeneralContext extends CompletionContext
}
