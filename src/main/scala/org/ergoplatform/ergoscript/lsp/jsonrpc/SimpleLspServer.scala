package org.ergoplatform.ergoscript.lsp.jsonrpc

import io.circe._
import io.circe.syntax._
import com.typesafe.scalalogging.LazyLogging
import org.ergoplatform.ergoscript.lsp.completion.CompletionProvider
import org.ergoplatform.ergoscript.lsp.hover.HoverProvider

import java.io.{InputStream, OutputStream}
import scala.util.{Try, Success, Failure}
import scala.collection.mutable

/** Simple LSP Server Implementation
  *
  * Implements the Language Server Protocol without using lsp4j. This avoids all
  * Scala/Java annotation compatibility issues.
  */
class SimpleLspServer(
    input: InputStream,
    output: OutputStream
) extends LazyLogging {

  import JsonRpcProtocol._
  import LspMessages._

  private var initialized = false
  private val documents = mutable.Map[String, String]()
  private val completionProvider = new CompletionProvider()
  private val hoverProvider = new HoverProvider()

  /** Start the server and process messages.
    */
  def start(): Unit = {
    logger.info("Starting Simple LSP Server via STDIO")

    try {
      while (true) {
        readMessage(input) match {
          case Some(messageText) =>
            logger.debug(s"Received message: ${messageText.take(200)}")

            // Try to parse as JSON first
            io.circe.parser.parse(messageText) match {
              case Right(json) =>
                // Check if it has an 'id' field - if so, it's a request, otherwise a notification
                json.hcursor.downField("id").focus match {
                  case Some(_) =>
                    // It's a request
                    parseRequest(messageText) match {
                      case Right(request) =>
                        handleRequest(request)
                      case Left(error) =>
                        logger.error(
                          s"Failed to parse request: ${error.message}"
                        )
                    }
                  case None =>
                    // It's a notification
                    json.as[NotificationMessage] match {
                      case Right(notification) =>
                        handleNotification(notification)
                      case Left(error) =>
                        logger.error(
                          s"Failed to parse notification: ${error.getMessage}"
                        )
                    }
                }
              case Left(error) =>
                logger.error(s"Failed to parse JSON: ${error.getMessage}")
            }

          case None =>
            logger.info("End of input stream, shutting down")
            return
        }
      }
    } catch {
      case ex: Exception =>
        logger.error("Error in server loop", ex)
    }
  }

  /** Handle a JSON-RPC notification.
    */
  private def handleNotification(notification: NotificationMessage): Unit = {
    logger.info(s"Handling notification: ${notification.method}")

    notification.method match {
      case "initialized" =>
        initialized = true
        logger.info("Client confirmed initialization")

      case "textDocument/didOpen" =>
        notification.params.flatMap(
          _.as[DidOpenTextDocumentParams].toOption
        ) match {
          case Some(params) =>
            val uri = params.textDocument.uri
            val text = params.textDocument.text
            documents(uri) = text
            logger.info(s"Document opened: $uri (${text.length} chars)")
            publishDiagnostics(uri, text)
          case None =>
            logger.error("Failed to parse didOpen params")
        }

      case "textDocument/didChange" =>
        notification.params.flatMap(
          _.as[DidChangeTextDocumentParams].toOption
        ) match {
          case Some(params) =>
            val uri = params.textDocument.uri
            params.contentChanges.lastOption.foreach { change =>
              documents(uri) = change.text
              logger.debug(s"Document changed: $uri")
              publishDiagnostics(uri, change.text)
            }
          case None =>
            logger.error("Failed to parse didChange params")
        }

      case "textDocument/didClose" =>
        notification.params.flatMap(
          _.as[DidCloseTextDocumentParams].toOption
        ) match {
          case Some(params) =>
            val uri = params.textDocument.uri
            documents.remove(uri)
            logger.info(s"Document closed: $uri")
          case None =>
            logger.error("Failed to parse didClose params")
        }

      case "textDocument/didSave" =>
        notification.params.flatMap(
          _.as[DidSaveTextDocumentParams].toOption
        ) match {
          case Some(params) =>
            val uri = params.textDocument.uri
            logger.info(s"Document saved: $uri")
            documents.get(uri).foreach { text =>
              publishDiagnostics(uri, text)
            }
          case None =>
            logger.error("Failed to parse didSave params")
        }

      case "exit" =>
        System.exit(0)

      case _ =>
        logger.warn(s"Unhandled notification: ${notification.method}")
    }
  }

  /** Handle a JSON-RPC request.
    */
  private def handleRequest(request: RequestMessage): Unit = {
    logger.info(s"Handling request: ${request.method}")

    val response = request.method match {
      case "initialize" =>
        handleInitialize(request)

      case "shutdown" =>
        Some(successResponse(request.id, Json.Null))

      case "textDocument/completion" =>
        handleCompletion(request)

      case "textDocument/hover" =>
        handleHover(request)

      case _ =>
        logger.warn(s"Unhandled method: ${request.method}")
        Some(
          errorResponse(
            request.id,
            ResponseError(
              ErrorCodes.MethodNotFound,
              s"Method not found: ${request.method}"
            )
          )
        )
    }

    response.foreach(sendResponse)
  }

  /** Handle initialize request.
    */
  private def handleInitialize(
      request: RequestMessage
  ): Option[ResponseMessage] = {
    request.params.flatMap(_.as[InitializeParams].toOption) match {
      case Some(params) =>
        logger.info(
          s"Initialize request from client, rootUri: ${params.rootUri}"
        )

        val capabilities = ServerCapabilities(
          textDocumentSync = Some(
            TextDocumentSyncOptions(
              openClose = Some(true),
              change = Some(1), // Full sync
              save = Some(SaveOptions(includeText = Some(true)))
            )
          ),
          completionProvider = Some(
            CompletionOptions(
              triggerCharacters = Some(List(".", "(")),
              resolveProvider = Some(false)
            )
          ),
          hoverProvider = Some(true),
          definitionProvider = Some(true),
          referencesProvider = Some(true),
          documentSymbolProvider = Some(true),
          documentFormattingProvider = Some(false), // Not implemented yet
          signatureHelpProvider = Some(
            SignatureHelpOptions(
              triggerCharacters = Some(List("(", ",")),
              retriggerCharacters = None
            )
          )
        )

        val result = InitializeResult(
          capabilities = capabilities,
          serverInfo = Some(
            ServerInfo(
              name = "ErgoScript Language Server",
              version = Some("0.1.0")
            )
          )
        )

        logger.info("Sending initialize response")
        Some(successResponse(request.id, result.asJson))

      case None =>
        logger.error("Failed to parse initialize params")
        Some(
          errorResponse(
            request.id,
            ResponseError(ErrorCodes.InvalidParams, "Invalid initialize params")
          )
        )
    }
  }

  /** Handle textDocument/completion request.
    */
  private def handleCompletion(
      request: RequestMessage
  ): Option[ResponseMessage] = {
    request.params.flatMap(_.as[CompletionParams].toOption) match {
      case Some(params) =>
        val uri = params.textDocument.uri
        val position = params.position
        val triggerCharacter = params.context.flatMap(_.triggerCharacter)

        logger.debug(
          s"Completion request for $uri at ${position.line}:${position.character}"
        )

        // Get the document text
        documents.get(uri) match {
          case Some(documentText) =>
            // Use the completion provider to generate completions
            val result =
              completionProvider.complete(
                documentText,
                position,
                triggerCharacter
              )

            Some(successResponse(request.id, result.asJson))

          case None =>
            logger.warn(s"Document not found: $uri")
            // Return empty completion list if document not found
            val emptyResult = CompletionList(
              isIncomplete = false,
              items = List.empty
            )
            Some(successResponse(request.id, emptyResult.asJson))
        }

      case None =>
        Some(
          errorResponse(
            request.id,
            ResponseError(ErrorCodes.InvalidParams, "Invalid completion params")
          )
        )
    }
  }

  /** Handle textDocument/hover request.
    */
  private def handleHover(request: RequestMessage): Option[ResponseMessage] = {
    request.params.flatMap(_.as[HoverParams].toOption) match {
      case Some(params) =>
        val uri = params.textDocument.uri
        val position = params.position

        logger.debug(
          s"Hover request for $uri at ${position.line}:${position.character}"
        )

        // Get the document text
        documents.get(uri) match {
          case Some(documentText) =>
            // Use the hover provider to generate hover information
            hoverProvider.hover(documentText, position) match {
              case Some(hover) =>
                Some(successResponse(request.id, hover.asJson))
              case None =>
                // No hover information available - return null
                Some(successResponse(request.id, Json.Null))
            }

          case None =>
            logger.warn(s"Document not found: $uri")
            // Return null if document not found
            Some(successResponse(request.id, Json.Null))
        }

      case None =>
        Some(
          errorResponse(
            request.id,
            ResponseError(ErrorCodes.InvalidParams, "Invalid hover params")
          )
        )
    }
  }

  /** Publish diagnostics for a document.
    */
  private def publishDiagnostics(uri: String, text: String): Unit = {
    import org.ergoplatform.ergoscript.cli.Compiler
    import org.ergoplatform.ergoscript.lsp.analysis.UnusedVariableAnalyzer

    logger.debug(s"Running diagnostics for $uri")

    // Compile the script to get errors
    val errorDiagnostics = Compiler.compile(
      script = text,
      name = "DiagnosticsCheck",
      description = "",
      networkPrefix = 0x00.toByte
    ) match {
      case Left(error) =>
        // Compilation failed - create diagnostic
        logger.info(
          s"Compilation error: ${error.message} at line ${error.line}, column ${error.column}"
        )

        val range = Range(
          start = Position(
            line = error.line.getOrElse(1) - 1, // LSP uses 0-based lines
            character =
              error.column.getOrElse(1) - 1 // LSP uses 0-based columns
          ),
          end = Position(
            line = error.line.getOrElse(1) - 1,
            character = error.column.getOrElse(1) + 10 // Highlight ~10 chars
          )
        )

        List(
          Diagnostic(
            range = range,
            severity = Some(1), // Error
            code = None,
            source = Some("ergoscript"),
            message = error.message
          )
        )

      case Right(_) =>
        // Compilation succeeded - no errors
        logger.debug("Compilation successful, no error diagnostics")
        List.empty[Diagnostic]
    }

    // Check for unused variables (warnings)
    val warningDiagnostics =
      UnusedVariableAnalyzer.findUnusedVariables(text).map { unusedVar =>
        val range = Range(
          start = Position(
            line = unusedVar.line,
            character = unusedVar.column
          ),
          end = Position(
            line = unusedVar.line,
            character = unusedVar.column + unusedVar.name.length
          )
        )

        Diagnostic(
          range = range,
          severity = Some(2), // Warning
          code = None,
          source = Some("ergoscript"),
          message = s"Variable '${unusedVar.name}' is declared but never used"
        )
      }

    // Combine error and warning diagnostics
    val diagnostics = errorDiagnostics ++ warningDiagnostics

    val params = PublishDiagnosticsParams(
      uri = uri,
      diagnostics = diagnostics
    )

    sendNotification("textDocument/publishDiagnostics", params.asJson)
  }

  /** Send a response message.
    */
  private def sendResponse(response: ResponseMessage): Unit = {
    val json = serializeResponse(response)
    logger.debug(s"Sending response: ${json.take(200)}")
    writeMessage(output, json)
  }

  /** Send a notification message.
    */
  private def sendNotification(method: String, params: Json): Unit = {
    val notif = notification(method, params)
    val json = serializeNotification(notif)
    logger.debug(s"Sending notification: ${method}")
    writeMessage(output, json)
  }
}
