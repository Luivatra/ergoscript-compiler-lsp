package org.ergoplatform.ergoscript.lsp.jsonrpc

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.semiauto._

import java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream}
import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}

/** JSON-RPC 2.0 Protocol Implementation for LSP
  *
  * Implements the Language Server Protocol transport layer without using lsp4j.
  * This avoids all Scala/Java annotation compatibility issues.
  */
object JsonRpcProtocol {

  // JSON-RPC message types
  sealed trait Message

  case class RequestMessage(
      jsonrpc: String = "2.0",
      id: Either[Long, String],
      method: String,
      params: Option[Json]
  ) extends Message

  case class ResponseMessage(
      jsonrpc: String = "2.0",
      id: Either[Long, String],
      result: Option[Json],
      error: Option[ResponseError]
  ) extends Message

  case class NotificationMessage(
      jsonrpc: String = "2.0",
      method: String,
      params: Option[Json]
  ) extends Message

  case class ResponseError(
      code: Int,
      message: String,
      data: Option[Json] = None
  )

  // Error codes from JSON-RPC spec
  object ErrorCodes {
    val ParseError = -32700
    val InvalidRequest = -32600
    val MethodNotFound = -32601
    val InvalidParams = -32602
    val InternalError = -32603
    val ServerNotInitialized = -32002
    val UnknownErrorCode = -32001
  }

  // Circe codecs
  // Custom codec for Either[Long, String] (request/response id)
  implicit val idEncoder: Encoder[Either[Long, String]] = Encoder.instance {
    case Left(num)  => Json.fromLong(num)
    case Right(str) => Json.fromString(str)
  }

  implicit val idDecoder: Decoder[Either[Long, String]] = Decoder.instance {
    cursor =>
      cursor.as[Long].map(Left(_)).orElse(cursor.as[String].map(Right(_)))
  }

  implicit val responseErrorEncoder: Encoder[ResponseError] = deriveEncoder
  implicit val responseErrorDecoder: Decoder[ResponseError] = deriveDecoder

  implicit val requestMessageEncoder: Encoder[RequestMessage] = deriveEncoder
  implicit val requestMessageDecoder: Decoder[RequestMessage] = deriveDecoder

  implicit val responseMessageEncoder: Encoder[ResponseMessage] = deriveEncoder
  implicit val responseMessageDecoder: Decoder[ResponseMessage] = deriveDecoder

  implicit val notificationMessageEncoder: Encoder[NotificationMessage] =
    deriveEncoder
  implicit val notificationMessageDecoder: Decoder[NotificationMessage] =
    deriveDecoder

  /** Read a JSON-RPC message from input stream. LSP uses Content-Length header
    * followed by message body.
    */
  def readMessage(input: InputStream): Option[String] = {
    val reader = new BufferedReader(
      new InputStreamReader(input, StandardCharsets.UTF_8)
    )

    Try {
      // Read headers
      var contentLength = 0
      var line = ""

      while ({
        line = reader.readLine()
        line != null && line.trim.nonEmpty
      }) {
        if (line.startsWith("Content-Length:")) {
          contentLength = line.substring("Content-Length:".length).trim.toInt
        }
        // Ignore other headers (Content-Type, etc.)
      }

      if (contentLength == 0) {
        return None
      }

      // Read message body
      val buffer = new Array[Char](contentLength)
      var totalRead = 0

      while (totalRead < contentLength) {
        val read = reader.read(buffer, totalRead, contentLength - totalRead)
        if (read == -1) {
          return None
        }
        totalRead += read
      }

      new String(buffer)
    }.toOption
  }

  /** Write a JSON-RPC message to output stream.
    */
  def writeMessage(output: OutputStream, message: String): Unit = {
    val bytes = message.getBytes(StandardCharsets.UTF_8)
    val header = s"Content-Length: ${bytes.length}\r\n\r\n"

    output.write(header.getBytes(StandardCharsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  /** Parse a JSON-RPC request message.
    */
  def parseRequest(json: String): Either[ResponseError, RequestMessage] = {
    parse(json) match {
      case Left(error) =>
        Left(
          ResponseError(ErrorCodes.ParseError, s"Parse error: ${error.message}")
        )

      case Right(jsonObject) =>
        jsonObject.as[RequestMessage] match {
          case Left(error) =>
            Left(
              ResponseError(
                ErrorCodes.InvalidRequest,
                s"Invalid request: ${error.getMessage}"
              )
            )
          case Right(request) =>
            Right(request)
        }
    }
  }

  /** Create a success response.
    */
  def successResponse(
      id: Either[Long, String],
      result: Json
  ): ResponseMessage = {
    ResponseMessage(
      jsonrpc = "2.0",
      id = id,
      result = Some(result),
      error = None
    )
  }

  /** Create an error response.
    */
  def errorResponse(
      id: Either[Long, String],
      error: ResponseError
  ): ResponseMessage = {
    ResponseMessage(
      jsonrpc = "2.0",
      id = id,
      result = None,
      error = Some(error)
    )
  }

  /** Serialize a response to JSON string.
    */
  def serializeResponse(response: ResponseMessage): String = {
    response.asJson.noSpaces
  }

  /** Create a notification message.
    */
  def notification(method: String, params: Json): NotificationMessage = {
    NotificationMessage(
      jsonrpc = "2.0",
      method = method,
      params = Some(params)
    )
  }

  /** Serialize a notification to JSON string.
    */
  def serializeNotification(notification: NotificationMessage): String = {
    notification.asJson.noSpaces
  }
}
