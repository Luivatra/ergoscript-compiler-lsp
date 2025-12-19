package org.ergoplatform.ergoscript.lsp.jsonrpc

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

/** LSP Message Types
  *
  * Defines the core LSP protocol messages we need to support. These are
  * simplified versions focusing on what ErgoScript LSP needs.
  */
object LspMessages {

  // Initialize Request/Response
  case class InitializeParams(
      processId: Option[Long],
      rootUri: Option[String],
      capabilities: ClientCapabilities,
      initializationOptions: Option[Json] = None,
      trace: Option[String] = None,
      workspaceFolders: Option[List[WorkspaceFolder]] = None
  )

  case class ClientCapabilities(
      workspace: Option[Json] = None,
      textDocument: Option[Json] = None,
      window: Option[Json] = None,
      general: Option[Json] = None,
      experimental: Option[Json] = None
  )

  case class WorkspaceFolder(
      uri: String,
      name: String
  )

  case class InitializeResult(
      capabilities: ServerCapabilities,
      serverInfo: Option[ServerInfo] = None
  )

  case class ServerInfo(
      name: String,
      version: Option[String] = None
  )

  case class ServerCapabilities(
      textDocumentSync: Option[TextDocumentSyncOptions] = None,
      completionProvider: Option[CompletionOptions] = None,
      hoverProvider: Option[Boolean] = None,
      definitionProvider: Option[Boolean] = None,
      referencesProvider: Option[Boolean] = None,
      documentSymbolProvider: Option[Boolean] = None,
      documentFormattingProvider: Option[Boolean] = None,
      signatureHelpProvider: Option[SignatureHelpOptions] = None
  )

  case class TextDocumentSyncOptions(
      openClose: Option[Boolean] = None,
      change: Option[Int] =
        None, // TextDocumentSyncKind: 0=None, 1=Full, 2=Incremental
      save: Option[SaveOptions] = None
  )

  case class SaveOptions(
      includeText: Option[Boolean] = None
  )

  case class CompletionOptions(
      triggerCharacters: Option[List[String]] = None,
      resolveProvider: Option[Boolean] = None
  )

  case class SignatureHelpOptions(
      triggerCharacters: Option[List[String]] = None,
      retriggerCharacters: Option[List[String]] = None
  )

  // Text Document Notifications
  case class DidOpenTextDocumentParams(
      textDocument: TextDocumentItem
  )

  case class TextDocumentItem(
      uri: String,
      languageId: String,
      version: Int,
      text: String
  )

  case class DidChangeTextDocumentParams(
      textDocument: VersionedTextDocumentIdentifier,
      contentChanges: List[TextDocumentContentChangeEvent]
  )

  case class VersionedTextDocumentIdentifier(
      uri: String,
      version: Int
  )

  case class TextDocumentContentChangeEvent(
      text: String,
      range: Option[Range] = None,
      rangeLength: Option[Int] = None
  )

  case class Range(
      start: Position,
      end: Position
  )

  case class Position(
      line: Int,
      character: Int
  )

  case class DidCloseTextDocumentParams(
      textDocument: TextDocumentIdentifier
  )

  case class DidSaveTextDocumentParams(
      textDocument: TextDocumentIdentifier,
      text: Option[String] = None
  )

  case class TextDocumentIdentifier(
      uri: String
  )

  // Diagnostics
  case class PublishDiagnosticsParams(
      uri: String,
      diagnostics: List[Diagnostic]
  )

  case class Diagnostic(
      range: Range,
      severity: Option[Int] = None, // 1=Error, 2=Warning, 3=Info, 4=Hint
      code: Option[String] = None,
      source: Option[String] = None,
      message: String
  )

  // Completion
  case class CompletionParams(
      textDocument: TextDocumentIdentifier,
      position: Position,
      context: Option[CompletionContext] = None
  )

  case class CompletionContext(
      triggerKind: Int, // 1=Invoked, 2=TriggerCharacter, 3=TriggerForIncompleteCompletions
      triggerCharacter: Option[String] = None
  )

  case class CompletionList(
      isIncomplete: Boolean,
      items: List[CompletionItem]
  )

  case class CompletionItem(
      label: String,
      kind: Option[Int] = None,
      detail: Option[String] = None,
      documentation: Option[String] = None,
      insertText: Option[String] = None
  )

  // Hover
  case class HoverParams(
      textDocument: TextDocumentIdentifier,
      position: Position
  )

  case class Hover(
      contents: String, // Simplified - normally MarkupContent or MarkedString
      range: Option[Range] = None
  )

  // Circe codecs - auto-derivation
  implicit val positionEncoder: Encoder[Position] = deriveEncoder
  implicit val positionDecoder: Decoder[Position] = deriveDecoder

  implicit val rangeEncoder: Encoder[Range] = deriveEncoder
  implicit val rangeDecoder: Decoder[Range] = deriveDecoder

  implicit val workspaceFolderEncoder: Encoder[WorkspaceFolder] = deriveEncoder
  implicit val workspaceFolderDecoder: Decoder[WorkspaceFolder] = deriveDecoder

  implicit val clientCapabilitiesEncoder: Encoder[ClientCapabilities] =
    deriveEncoder
  implicit val clientCapabilitiesDecoder: Decoder[ClientCapabilities] =
    deriveDecoder

  implicit val initializeParamsEncoder: Encoder[InitializeParams] =
    deriveEncoder
  implicit val initializeParamsDecoder: Decoder[InitializeParams] =
    deriveDecoder

  implicit val saveOptionsEncoder: Encoder[SaveOptions] = deriveEncoder
  implicit val saveOptionsDecoder: Decoder[SaveOptions] = deriveDecoder

  implicit val textDocumentSyncOptionsEncoder
      : Encoder[TextDocumentSyncOptions] = deriveEncoder
  implicit val textDocumentSyncOptionsDecoder
      : Decoder[TextDocumentSyncOptions] = deriveDecoder

  implicit val completionOptionsEncoder: Encoder[CompletionOptions] =
    deriveEncoder
  implicit val completionOptionsDecoder: Decoder[CompletionOptions] =
    deriveDecoder

  implicit val signatureHelpOptionsEncoder: Encoder[SignatureHelpOptions] =
    deriveEncoder
  implicit val signatureHelpOptionsDecoder: Decoder[SignatureHelpOptions] =
    deriveDecoder

  implicit val serverCapabilitiesEncoder: Encoder[ServerCapabilities] =
    deriveEncoder
  implicit val serverCapabilitiesDecoder: Decoder[ServerCapabilities] =
    deriveDecoder

  implicit val serverInfoEncoder: Encoder[ServerInfo] = deriveEncoder
  implicit val serverInfoDecoder: Decoder[ServerInfo] = deriveDecoder

  implicit val initializeResultEncoder: Encoder[InitializeResult] =
    deriveEncoder
  implicit val initializeResultDecoder: Decoder[InitializeResult] =
    deriveDecoder

  implicit val textDocumentItemEncoder: Encoder[TextDocumentItem] =
    deriveEncoder
  implicit val textDocumentItemDecoder: Decoder[TextDocumentItem] =
    deriveDecoder

  implicit val didOpenTextDocumentParamsEncoder
      : Encoder[DidOpenTextDocumentParams] = deriveEncoder
  implicit val didOpenTextDocumentParamsDecoder
      : Decoder[DidOpenTextDocumentParams] = deriveDecoder

  implicit val textDocumentIdentifierEncoder: Encoder[TextDocumentIdentifier] =
    deriveEncoder
  implicit val textDocumentIdentifierDecoder: Decoder[TextDocumentIdentifier] =
    deriveDecoder

  implicit val versionedTextDocumentIdentifierEncoder
      : Encoder[VersionedTextDocumentIdentifier] = deriveEncoder
  implicit val versionedTextDocumentIdentifierDecoder
      : Decoder[VersionedTextDocumentIdentifier] = deriveDecoder

  implicit val textDocumentContentChangeEventEncoder
      : Encoder[TextDocumentContentChangeEvent] = deriveEncoder
  implicit val textDocumentContentChangeEventDecoder
      : Decoder[TextDocumentContentChangeEvent] = deriveDecoder

  implicit val didChangeTextDocumentParamsEncoder
      : Encoder[DidChangeTextDocumentParams] = deriveEncoder
  implicit val didChangeTextDocumentParamsDecoder
      : Decoder[DidChangeTextDocumentParams] = deriveDecoder

  implicit val didCloseTextDocumentParamsEncoder
      : Encoder[DidCloseTextDocumentParams] = deriveEncoder
  implicit val didCloseTextDocumentParamsDecoder
      : Decoder[DidCloseTextDocumentParams] = deriveDecoder

  implicit val didSaveTextDocumentParamsEncoder
      : Encoder[DidSaveTextDocumentParams] = deriveEncoder
  implicit val didSaveTextDocumentParamsDecoder
      : Decoder[DidSaveTextDocumentParams] = deriveDecoder

  implicit val diagnosticEncoder: Encoder[Diagnostic] = deriveEncoder
  implicit val diagnosticDecoder: Decoder[Diagnostic] = deriveDecoder

  implicit val publishDiagnosticsParamsEncoder
      : Encoder[PublishDiagnosticsParams] = deriveEncoder
  implicit val publishDiagnosticsParamsDecoder
      : Decoder[PublishDiagnosticsParams] = deriveDecoder

  implicit val completionContextEncoder: Encoder[CompletionContext] =
    deriveEncoder
  implicit val completionContextDecoder: Decoder[CompletionContext] =
    deriveDecoder

  implicit val completionParamsEncoder: Encoder[CompletionParams] =
    deriveEncoder
  implicit val completionParamsDecoder: Decoder[CompletionParams] =
    deriveDecoder

  implicit val completionItemEncoder: Encoder[CompletionItem] = deriveEncoder
  implicit val completionItemDecoder: Decoder[CompletionItem] = deriveDecoder

  implicit val completionListEncoder: Encoder[CompletionList] = deriveEncoder
  implicit val completionListDecoder: Decoder[CompletionList] = deriveDecoder

  implicit val hoverParamsEncoder: Encoder[HoverParams] = deriveEncoder
  implicit val hoverParamsDecoder: Decoder[HoverParams] = deriveDecoder

  implicit val hoverEncoder: Encoder[Hover] = deriveEncoder
  implicit val hoverDecoder: Decoder[Hover] = deriveDecoder
}
