package org.ergoplatform.ergoscript.cli

import org.ergoplatform.sdk.{ContractTemplate, Parameter}
import sigma.ast.{ErgoTree, SourceContext}
import sigma.serialization.ErgoTreeSerializer
import sigma.compiler.{SigmaCompiler, SigmaTemplateCompiler}
import sigma.compiler.ir.IRContext
import sigma.SigmaException
import sigmastate.lang.{ContractParser, ParsedContractTemplate}
import scorex.util.encode.Base16
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Try, Success, Failure}

case class CompilationResult(
    ergoTree: ErgoTree,
    template: ContractTemplate
)

case class CompilationError(
    message: String,
    line: Option[Int] = None,
    column: Option[Int] = None
)

object Compiler extends LazyLogging {

  // Implicit IRContext required by SigmaCompiler
  implicit val IR: IRContext = new sigma.compiler.ir.CompiletimeIRContext()

  /** Compile ErgoScript with automatic template detection. If the script
    * contains @contract annotations, uses SigmaTemplateCompiler for EIP-5
    * compliant template generation. Otherwise, uses standard compilation.
    */
  def compile(
      script: String,
      name: String,
      description: String,
      networkPrefix: Byte = 0x00, // Mainnet
      treeVersion: Byte = ErgoTree.VersionFlag
  ): Either[CompilationError, CompilationResult] = {
    // Check if script contains EIP-5 annotations
    if (script.contains("@contract") || script.contains("@param")) {
      compileTemplate(script, networkPrefix, treeVersion)
    } else {
      compileStandard(script, name, description, networkPrefix, treeVersion)
    }
  }

  /** Compile using SigmaTemplateCompiler for EIP-5 template support. The script
    * should follow EIP-5 contract template syntax with @contract decorator.
    */
  private def compileTemplate(
      script: String,
      networkPrefix: Byte,
      treeVersion: Byte
  ): Either[CompilationError, CompilationResult] = {
    Try {
      val templateCompiler = SigmaTemplateCompiler(networkPrefix)
      val template = templateCompiler.compile(script)

      // Build ErgoTree from the template's expression tree (which is a SigmaProp)
      val ergoTree = ErgoTree.fromProposition(
        ErgoTree.HeaderType @@ treeVersion,
        template.expressionTree
      )

      CompilationResult(ergoTree, template)
    } match {
      case Success(result) => Right(result)
      case Failure(ex) =>
        logger.error("Template compilation failed", ex)
        // Calculate line offset for EIP-5 format to adjust error positions
        val lineOffset = calculateEIP5LineOffset(script)
        Left(extractCompilationError(ex, lineOffset, Some(script)))
    }
  }

  /** Standard compilation without EIP-5 template features.
    */
  private def compileStandard(
      script: String,
      name: String,
      description: String,
      networkPrefix: Byte,
      treeVersion: Byte
  ): Either[CompilationError, CompilationResult] = {
    Try {
      // Use SigmaCompiler to compile ErgoScript
      val compiler = SigmaCompiler(networkPrefix)
      val env = Map.empty[String, Any]
      val compilerResult = compiler.compile(env, script)

      // Build the ErgoTree from the CompilerResult
      val value = compilerResult.buildTree
      val ergoTree = ErgoTree.fromProposition(
        ErgoTree.HeaderType @@ treeVersion,
        value.toSigmaProp
      )

      // Extract constants for SDK ContractTemplate format
      val constants = ergoTree.constants
      val constTypes = constants.map(_.tpe).toIndexedSeq
      val constValues = if (constants.nonEmpty) {
        Some(constants.map(c => Some(c.value)).toIndexedSeq)
      } else {
        None
      }

      // Create basic template without parameters (standard compilation)
      val template = ContractTemplate(
        name = name,
        description = description,
        constTypes = constTypes,
        constValues = constValues,
        parameters = IndexedSeq.empty[Parameter],
        expressionTree = value.toSigmaProp
      )

      CompilationResult(ergoTree, template)

    } match {
      case Success(result) => Right(result)
      case Failure(ex) =>
        logger.error("Compilation failed", ex)
        Left(extractCompilationError(ex, 0, Some(script)))
    }
  }

  /** Extract structured error information from various exception types. Uses
    * reflection to access the `source` field from SigmaException and its
    * subclasses.
    *
    * @param ex
    *   The exception to extract error information from
    * @param lineOffset
    *   Line offset to add to reported line numbers (for EIP-5 format)
    * @param script
    *   Optional original script source for improved column position detection
    */
  private def extractCompilationError(
      ex: Throwable,
      lineOffset: Int = 0,
      script: Option[String] = None
  ): CompilationError = {
    val message = Option(ex.getMessage).getOrElse("Unknown compilation error")

    // Try to extract SourceContext from sigma exceptions using reflection
    // This works for TyperException, BinderException, CompilerException, ParserException, etc.
    // Note: ParserException extends CompilerException which extends SigmaException
    val sourceContext: Option[SourceContext] = ex match {
      case se: SigmaException =>
        extractSourceContext(se)
      case _ =>
        // Fall back to regex parsing for unknown exception types
        None
    }

    sourceContext match {
      case Some(ctx) =>
        // Clean up the error message - extract just the meaningful part
        val cleanMessage = cleanErrorMessage(message)

        // Adjust column for method call errors in chains
        val adjustedColumn = adjustColumnForMethodCall(
          message,
          ctx.line + lineOffset,
          ctx.column,
          script
        )

        CompilationError(
          message = cleanMessage,
          line = Some(ctx.line + lineOffset),
          column = Some(adjustedColumn)
        )
      case None =>
        // Fall back to regex-based extraction from error message
        val (line, column) = extractLineColumnFromMessage(message)
        val cleanMessage = cleanErrorMessage(message)
        CompilationError(
          message = cleanMessage,
          line = line.map(_ + lineOffset),
          column = column
        )
    }
  }

  /** Extract SourceContext from an exception using reflection. The sigma
    * exceptions have a `source` method that returns Option[SourceContext].
    */
  private def extractSourceContext(ex: Throwable): Option[SourceContext] = {
    try {
      val sourceMethod = ex.getClass.getMethod("source")
      sourceMethod.invoke(ex) match {
        case opt: Option[_] => opt.map(_.asInstanceOf[SourceContext])
        case _              => None
      }
    } catch {
      case _: NoSuchMethodException => None
      case _: Exception             => None
    }
  }

  /** Calculate line offset for EIP-5 format contracts. The parser extracts the
    * body after the `=` sign, so we need to find which line that starts on to
    * adjust error positions back to the original source.
    */
  private def calculateEIP5LineOffset(script: String): Int = {
    // Find the position of the `=` sign that starts the contract body
    // The pattern is: @contract def name(params) = { body }
    val contractDefPattern = """@contract\s+def\s+\w+\s*\([^)]*\)\s*=""".r

    contractDefPattern.findFirstMatchIn(script) match {
      case Some(m) =>
        // Count newlines up to the `=` sign
        val prefixBeforeBody = script.substring(0, m.end)
        prefixBeforeBody.count(_ == '\n')
      case None =>
        // If we can't find the pattern, return 0 (no offset)
        0
    }
  }

  /** Adjust column position for method call errors in chained calls. When a
    * method doesn't exist in a chain like `box.value.nonExistentMethod()`, the
    * error points to the start of the chain, but we want to point to the actual
    * failing method name. Also handles type mismatch errors in lambda
    * expressions.
    *
    * @param message
    *   The error message
    * @param line
    *   The line number where the error occurred
    * @param column
    *   The column number reported by the compiler
    * @param script
    *   The original script source
    * @return
    *   Adjusted column number
    */
  private def adjustColumnForMethodCall(
      message: String,
      line: Int,
      column: Int,
      script: Option[String]
  ): Int = {
    // Pattern to extract method name from error message like:
    // "Cannot find method 'nonExistentMethod' in ..."
    val methodNamePattern = """Cannot find method '(\w+)'""".r

    // Pattern for type mismatch in Select (method call on chain)
    // e.g., "Invalid argument type of application Apply(Select(...),filter,None)"
    // We look for ,methodName,None) pattern which appears in the Select AST representation
    val selectMethodPattern = """,(\w+),None\)""".r

    methodNamePattern.findFirstMatchIn(message) match {
      case Some(m) =>
        val methodName = m.group(1)

        // Get the source line if available
        script
          .flatMap { src =>
            val lines = src.split("\n")
            if (line > 0 && line <= lines.length) {
              val sourceLine = lines(line - 1) // Convert to 0-based index

              // Find the method name in the line
              val methodIndex = sourceLine.indexOf(methodName)
              if (methodIndex >= 0) {
                // Return the column where the method name starts (1-based)
                Some(methodIndex + 1)
              } else {
                None
              }
            } else {
              None
            }
          }
          .getOrElse(column) // Fall back to original column if we can't find it

      case None =>
        // Check for type mismatch errors in method calls like filter, map, etc.
        selectMethodPattern.findFirstMatchIn(message) match {
          case Some(m) =>
            val methodName = m.group(1)

            // Get the source line if available
            script
              .flatMap { src =>
                val lines = src.split("\n")
                if (line > 0 && line <= lines.length) {
                  val sourceLine = lines(line - 1) // Convert to 0-based index

                  // Find the method name in the line
                  val methodIndex = sourceLine.indexOf(s".$methodName")
                  if (methodIndex >= 0) {
                    // Point to the method name after the dot
                    Some(methodIndex + 2) // +1 for 1-based, +1 to skip the dot
                  } else {
                    None
                  }
                } else {
                  None
                }
              }
              .getOrElse(
                column
              ) // Fall back to original column if we can't find it

          case None =>
            // Not a recognized error pattern, return original column
            column
        }
    }
  }

  /** Clean up error messages by removing redundant source code display and
    * keeping only the meaningful error description.
    */
  private def cleanErrorMessage(message: String): String = {
    // The error message format is typically:
    // line N: <source code>
    //         ^
    // <actual error message>

    val lines = message.split("\n")

    // Find the actual error message (usually after the caret line)
    val errorLines = lines.dropWhile(line =>
      line.startsWith("line ") ||
        line.trim.matches("^\\^+$") ||
        line.trim.isEmpty
    )

    val rawError = if (errorLines.nonEmpty) {
      errorLines.mkString("\n").trim
    } else {
      // If we couldn't parse it, return the original
      message
    }

    // Clean up verbose error messages
    // Remove env HashMap dump from "not found in env HashMap(...)" messages
    // The HashMap contains nested parentheses so we can't use a simple regex
    val envStartPattern = " because it is not found in env HashMap("
    val cleaned1 = rawError.indexOf(envStartPattern) match {
      case -1  => rawError
      case idx => rawError.substring(0, idx)
    }

    // Remove other verbose context that isn't helpful
    // e.g., "Cannot assign type for variable 'x'" is enough without the env dump
    val cleaned2 = cleaned1.trim

    // If the message ends with "because it is not found in env" without the HashMap, clean that too
    val envSuffixPattern = """ because it is not found in env\s*$""".r
    val cleaned3 = envSuffixPattern.replaceFirstIn(cleaned2, "")

    if (cleaned3.nonEmpty) cleaned3 else rawError
  }

  /** Extract line and column from error message using regex patterns. This is a
    * fallback for when structured error info is not available.
    */
  private def extractLineColumnFromMessage(
      message: String
  ): (Option[Int], Option[Int]) = {
    // Pattern 1: "line N:" at the start of message or on its own line
    val linePattern1 = """(?m)^line (\d+):""".r
    // Pattern 2: "Position L:C" format from parser
    val positionPattern = """Position (\d+):(\d+)""".r
    // Pattern 3: "at line N, column C" format
    val lineColPattern = """at line (\d+),?\s*column (\d+)""".r

    // Try each pattern
    lineColPattern.findFirstMatchIn(message) match {
      case Some(m) =>
        (Some(m.group(1).toInt), Some(m.group(2).toInt))
      case None =>
        positionPattern.findFirstMatchIn(message) match {
          case Some(m) =>
            (Some(m.group(1).toInt), Some(m.group(2).toInt))
          case None =>
            linePattern1.findFirstMatchIn(message) match {
              case Some(m) =>
                // For "line N:" format, try to find column from caret position
                val line = m.group(1).toInt
                val column = extractColumnFromCaret(message)
                (Some(line), column)
              case None =>
                (None, None)
            }
        }
    }
  }

  /** Try to extract column position from caret (^) in error message. Error
    * messages often show: line 1: some code here ^
    */
  private def extractColumnFromCaret(message: String): Option[Int] = {
    val lines = message.split("\n")
    lines.zipWithIndex
      .find { case (line, _) =>
        line.trim.matches("^\\^+$")
      }
      .map { case (caretLine, _) =>
        // Column is the position of the caret
        caretLine.indexOf('^') + 1
      }
  }

  def compileFromFile(
      filePath: String,
      name: String,
      description: String,
      networkPrefix: Byte = 0x00
  ): Either[CompilationError, CompilationResult] = {
    Try {
      val source = scala.io.Source.fromFile(filePath)
      try {
        source.mkString
      } finally {
        source.close()
      }
    } match {
      case Success(script) =>
        compile(script, name, description, networkPrefix)
      case Failure(ex) =>
        Left(CompilationError(s"Failed to read file: ${ex.getMessage}"))
    }
  }
}
