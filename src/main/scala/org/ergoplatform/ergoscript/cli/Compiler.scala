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
    template: Option[ContractTemplate] = None
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
    compileWithImports(
      script,
      name,
      description,
      None,
      None,
      networkPrefix,
      treeVersion
    )
  }

  /** Compile ErgoScript with import resolution support.
    *
    * @param script
    *   The ErgoScript source code
    * @param name
    *   Contract name
    * @param description
    *   Contract description
    * @param filePath
    *   Path to the current file (for resolving relative imports)
    * @param workspaceRoot
    *   Workspace root directory (for resolving imports)
    * @param networkPrefix
    *   Network prefix (mainnet/testnet)
    * @param treeVersion
    *   ErgoTree version
    * @return
    *   Compilation result or error
    */
  def compileWithImports(
      script: String,
      name: String,
      description: String,
      filePath: Option[String],
      workspaceRoot: Option[String],
      networkPrefix: Byte = 0x00,
      treeVersion: Byte = ErgoTree.VersionFlag
  ): Either[CompilationError, CompilationResult] = {
    import org.ergoplatform.ergoscript.lsp.imports.ImportResolver
    import org.ergoplatform.ergoscript.project.{
      ProjectConfigParser,
      ConstantSubstitution
    }
    import java.nio.file.Paths

    // Try to load project configuration if workspace root is available
    val projectConfig = workspaceRoot.flatMap { root =>
      ProjectConfigParser.findAndParse(Paths.get(root))
    }

    // Apply constant substitution if project config exists
    val scriptWithConstants = projectConfig match {
      case Some(config) =>
        ConstantSubstitution.substitute(script, config.constants) match {
          case Right(substituted) => substituted
          case Left(error) =>
            return Left(
              CompilationError(
                message = s"Constant substitution error: $error",
                line = None,
                column = None
              )
            )
        }
      case None => script
    }

    // Expand imports
    logger.debug(
      s"Expanding imports for script with filePath=$filePath, workspaceRoot=$workspaceRoot"
    )
    logger.debug(
      s"Script before import expansion: ${scriptWithConstants.take(100)}"
    )
    val importResult =
      ImportResolver.expandImports(scriptWithConstants, filePath, workspaceRoot)
    logger.debug(
      s"Expanded script: ${importResult.expandedCode.code.take(100)}"
    )

    // Check for import errors
    if (importResult.errors.nonEmpty) {
      return Left(
        CompilationError(
          message = s"Import errors: ${importResult.errors.mkString("; ")}",
          line = None,
          column = None
        )
      )
    }

    // Get the expanded code and source map
    val expandedCode = importResult.expandedCode
    val expandedScript = expandedCode.code

    // Use network prefix from project config if available
    val finalNetworkPrefix = projectConfig
      .map(_.ergoscript.networkPrefix)
      .getOrElse(networkPrefix)

    // Check if script contains EIP-5 annotations
    if (
      expandedScript.contains("@contract") || expandedScript.contains("@param")
    ) {
      compileTemplate(
        expandedScript,
        finalNetworkPrefix,
        treeVersion,
        Some(expandedCode)
      )
    } else {
      compileStandard(
        expandedScript,
        name,
        description,
        finalNetworkPrefix,
        treeVersion,
        Some(expandedCode)
      )
    }
  }

  /** Compile using SigmaTemplateCompiler for EIP-5 template support. The script
    * should follow EIP-5 contract template syntax with @contract decorator.
    */
  private def compileTemplate(
      script: String,
      networkPrefix: Byte,
      treeVersion: Byte,
      sourceMap: Option[org.ergoplatform.ergoscript.lsp.imports.ExpandedCode] =
        None
  ): Either[CompilationError, CompilationResult] = {
    // For templates, inline library functions into the template body
    val (processedScript, adjustedSourceMap) =
      if (script.contains("@contract")) {
        val inlined = inlineLibraryFunctionsIntoTemplate(script)
        val adjusted =
          sourceMap.map(sm => adjustSourceMapForInlining(script, inlined, sm))
        (inlined, adjusted)
      } else {
        (script, sourceMap)
      }

    Try {
      val templateCompiler = SigmaTemplateCompiler(networkPrefix)
      val template = templateCompiler.compile(processedScript)

      // For templates, we create placeholder constants for parameters
      // This allows the template to be stored and later bound with actual values
      import sigma.ast._
      val paramConstants = template.parameters
        .zip(template.constTypes)
        .map { case (param, constType) =>
          // Create a placeholder constant of the appropriate type
          val constant: Constant[SType] = constType match {
            case SInt  => IntConstant(0).asInstanceOf[Constant[SType]]
            case SLong => LongConstant(0L).asInstanceOf[Constant[SType]]
            case SBoolean =>
              BooleanConstant(false).asInstanceOf[Constant[SType]]
            case _ =>
              IntConstant(0).asInstanceOf[Constant[SType]] // Default fallback
          }
          param.name -> constant
        }
        .toMap

      val ergoTree = template.applyTemplate(Some(treeVersion), paramConstants)

      CompilationResult(ergoTree, Some(template))
    } match {
      case Success(result) => Right(result)
      case Failure(ex) =>
        logger.error("Template compilation failed", ex)
        // Calculate line offset for EIP-5 format to adjust error positions
        val lineOffset = calculateEIP5LineOffset(processedScript)
        Left(
          extractCompilationError(
            ex,
            lineOffset,
            Some(processedScript),
            adjustedSourceMap
          )
        )
    }
  }

  /** Standard compilation without EIP-5 template features.
    */
  private def compileStandard(
      script: String,
      name: String,
      description: String,
      networkPrefix: Byte,
      treeVersion: Byte,
      sourceMap: Option[org.ergoplatform.ergoscript.lsp.imports.ExpandedCode] =
        None
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

      CompilationResult(ergoTree, Some(template))

    } match {
      case Success(result) => Right(result)
      case Failure(ex) =>
        logger.error("Compilation failed", ex)
        Left(extractCompilationError(ex, 0, Some(script), sourceMap))
    }
  }

  /** Adjust source map after inlining library functions into template body.
    * This accounts for the line number shifts caused by moving library
    * functions.
    *
    * @param originalScript
    *   The script before inlining
    * @param inlinedScript
    *   The script after inlining
    * @param sourceMap
    *   The original source map
    * @return
    *   Adjusted source map
    */
  private def adjustSourceMapForInlining(
      originalScript: String,
      inlinedScript: String,
      sourceMap: org.ergoplatform.ergoscript.lsp.imports.ExpandedCode
  ): org.ergoplatform.ergoscript.lsp.imports.ExpandedCode = {
    import org.ergoplatform.ergoscript.lsp.imports.SourceLocation

    // Find where library functions were in the original
    val contractIndex = originalScript.indexOf("@contract")
    if (contractIndex == -1) {
      return sourceMap
    }

    val beforeContract = originalScript.substring(0, contractIndex)
    val libraryFunctions = extractLibraryFunctions(beforeContract.trim)

    if (libraryFunctions.isEmpty) {
      return sourceMap
    }

    // Count lines in library functions
    val libraryLineCount = libraryFunctions.map(_.count(_ == '\n') + 1).sum

    // Find the template body opening brace line in the inlined script
    val templateBodyPattern = """@contract\s+def\s+\w+\s*\([^)]*\)\s*=\s*\{""".r
    val openBraceLineInInlined =
      templateBodyPattern.findFirstMatchIn(inlinedScript) match {
        case Some(m) =>
          inlinedScript.substring(0, m.end).count(_ == '\n') + 1
        case None =>
          return sourceMap
      }

    // Find the template body opening brace line in the original script
    val openBraceLineInOriginal =
      templateBodyPattern.findFirstMatchIn(originalScript) match {
        case Some(m) =>
          originalScript.substring(0, m.end).count(_ == '\n') + 1
        case None =>
          return sourceMap
      }

    // Count lines before @contract that were removed
    val linesBeforeContractOriginal =
      originalScript.substring(0, contractIndex).count(_ == '\n') + 1
    val linesBeforeContractInlined = inlinedScript
      .substring(0, inlinedScript.indexOf("@contract"))
      .count(_ == '\n') + 1
    val removedLines = linesBeforeContractOriginal - linesBeforeContractInlined

    // Build new source map
    val newMappings = scala.collection.mutable.Map[Int, SourceLocation]()

    inlinedScript.split("\n").indices.foreach { idx =>
      val inlinedLine = idx + 1

      if (inlinedLine <= linesBeforeContractInlined) {
        // Lines before @contract - map directly but adjust for removed library functions
        val originalLine = inlinedLine + removedLines
        sourceMap.getOriginalLocation(originalLine).foreach { loc =>
          newMappings(inlinedLine) = loc
        }
      } else if (inlinedLine <= openBraceLineInInlined) {
        // @contract declaration line - map directly
        val originalLine = inlinedLine + removedLines
        sourceMap.getOriginalLocation(originalLine).foreach { loc =>
          newMappings(inlinedLine) = loc
        }
      } else if (inlinedLine <= openBraceLineInInlined + libraryLineCount) {
        // Library functions that were moved - map back to their original location
        val libraryOffset = inlinedLine - openBraceLineInInlined - 1
        val originalLine =
          linesBeforeContractOriginal - removedLines + libraryOffset + 1
        sourceMap.getOriginalLocation(originalLine).foreach { loc =>
          newMappings(inlinedLine) = loc
        }
      } else {
        // Original template body - don't shift, map to same relative position as before
        val offsetFromBrace =
          inlinedLine - openBraceLineInInlined - libraryLineCount
        val originalLine = openBraceLineInOriginal + offsetFromBrace
        sourceMap.getOriginalLocation(originalLine).foreach { loc =>
          newMappings(inlinedLine) = loc
        }
      }
    }

    org.ergoplatform.ergoscript.lsp.imports.SourceMap(
      inlinedScript,
      newMappings.toMap
    )
  }

  /** Inline library functions into template body. For @contract templates that
    * have library function definitions before the @contract annotation, this
    * method moves those definitions into the template body (after the opening
    * brace). This allows templates to use imported helper functions.
    *
    * @param script
    *   The script with potential library functions before @contract
    * @return
    *   Script with library functions inlined into template body
    */
  private def inlineLibraryFunctionsIntoTemplate(script: String): String = {
    // Find the @contract line
    val contractIndex = script.indexOf("@contract")
    if (contractIndex == -1) {
      return script
    }

    // Extract everything before @contract
    val beforeContract = script.substring(0, contractIndex).trim

    // Check if there are any def or val definitions
    if (!beforeContract.contains("def ") && !beforeContract.contains("val ")) {
      return script
    }

    // Extract library functions using balanced brace matching
    // We need to handle: def name(...) = { ... } with proper brace counting
    val libraryFunctions = extractLibraryFunctions(beforeContract)

    // If no library functions found, return original script
    if (libraryFunctions.isEmpty) {
      return script
    }

    // Find the opening brace of the template body
    // Pattern: @contract def name(...) = {
    // Need to handle parameters with default values like (minHeight: Int = 100)
    val templateBodyPattern = """@contract\s+def\s+\w+\s*\([^)]*\)\s*=\s*\{""".r

    templateBodyPattern.findFirstMatchIn(script) match {
      case Some(m) =>
        val openBraceIndex = m.end - 1 // Position of the '{'

        // Remove library functions from before @contract, keeping only comments
        val cleanedBefore =
          removeLibraryFunctions(beforeContract, libraryFunctions).trim

        // Build the new script:
        // 1. Cleaned before content (comments and whitespace)
        // 2. @contract line and opening brace
        // 3. Library functions indented
        // 4. Rest of the template body
        val afterOpenBrace = script.substring(openBraceIndex + 1)

        val indentedLibraryFunctions =
          libraryFunctions.map(func => s"  $func").mkString("\n", "\n", "\n")

        val result = if (cleanedBefore.nonEmpty) {
          s"$cleanedBefore\n${script.substring(contractIndex, openBraceIndex + 1)}$indentedLibraryFunctions$afterOpenBrace"
        } else {
          s"${script.substring(contractIndex, openBraceIndex + 1)}$indentedLibraryFunctions$afterOpenBrace"
        }

        result

      case None =>
        // Could not find template body pattern, return original
        script
    }
  }

  /** Extract library functions (def and val) from text using balanced brace
    * matching.
    */
  private def extractLibraryFunctions(text: String): List[String] = {
    val functions = scala.collection.mutable.ListBuffer[String]()
    var i = 0
    val chars = text.toCharArray

    while (i < chars.length) {
      // Skip whitespace
      while (i < chars.length && chars(i).isWhitespace) i += 1

      if (i >= chars.length) return functions.toList

      // Check if we're at a def or val
      if (
        text
          .substring(i)
          .startsWith("def ") || text.substring(i).startsWith("val ")
      ) {
        val start = i

        // Find the '=' sign
        while (i < chars.length && chars(i) != '=') i += 1

        if (i >= chars.length) return functions.toList

        i += 1 // Skip '='

        // Skip whitespace after '='
        while (i < chars.length && chars(i).isWhitespace) i += 1

        if (i >= chars.length) return functions.toList

        // Check if it's a brace-enclosed expression
        if (chars(i) == '{') {
          // Find matching closing brace
          var braceCount = 1
          i += 1
          while (i < chars.length && braceCount > 0) {
            if (chars(i) == '{') braceCount += 1
            else if (chars(i) == '}') braceCount -= 1
            i += 1
          }

          functions += text.substring(start, i).trim
        } else {
          // Single expression until newline or semicolon
          while (i < chars.length && chars(i) != '\n' && chars(i) != ';') i += 1
          functions += text.substring(start, i).trim
        }
      } else if (text.substring(i).startsWith("//")) {
        // Skip comment line
        while (i < chars.length && chars(i) != '\n') i += 1
      } else if (text.substring(i).startsWith("/*")) {
        // Skip block comment
        i += 2
        while (
          i < chars.length - 1 && !(chars(i) == '*' && chars(i + 1) == '/')
        ) i += 1
        i += 2
      } else {
        // Skip unknown character
        i += 1
      }
    }

    functions.toList
  }

  /** Remove library functions from text, keeping comments.
    */
  private def removeLibraryFunctions(
      text: String,
      functions: List[String]
  ): String = {
    functions.foldLeft(text) { (t, func) =>
      t.replace(func, "")
    }
  }

  /** Extract structured error information from various exception types. Uses
    * reflection to access the `source` field from SigmaException and its
    * subclasses. Maps error positions back to original source using source map.
    *
    * @param ex
    *   The exception to extract error information from
    * @param lineOffset
    *   Line offset to add to reported line numbers (for EIP-5 format)
    * @param script
    *   Optional original script source for improved column position detection
    * @param sourceMap
    *   Optional source map for mapping expanded code positions to original
    */
  private def extractCompilationError(
      ex: Throwable,
      lineOffset: Int = 0,
      script: Option[String] = None,
      sourceMap: Option[org.ergoplatform.ergoscript.lsp.imports.ExpandedCode] =
        None
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

        val expandedLine = ctx.line + lineOffset
        val expandedColumn = adjustedColumn

        // Map back to original source if we have a source map
        sourceMap match {
          case Some(sm) =>
            sm.getOriginalLocation(expandedLine, expandedColumn) match {
              case Some(originalLoc) =>
                // Build error message with original file info if different
                val filePrefix = if (originalLoc.originalFile != "<unknown>") {
                  s"${originalLoc.originalFile}: "
                } else {
                  ""
                }

                CompilationError(
                  message = s"$filePrefix$cleanMessage",
                  line = Some(originalLoc.originalLine),
                  column = Some(originalLoc.originalColumn)
                )
              case None =>
                // No mapping found, use expanded position
                CompilationError(
                  message = cleanMessage,
                  line = Some(expandedLine),
                  column = Some(expandedColumn)
                )
            }
          case None =>
            // No source map, use expanded position
            CompilationError(
              message = cleanMessage,
              line = Some(expandedLine),
              column = Some(expandedColumn)
            )
        }
      case None =>
        // Fall back to regex-based extraction from error message
        val (line, column) = extractLineColumnFromMessage(message)
        val cleanMessage = cleanErrorMessage(message)

        // Try to map if we have both line and source map
        val (finalLine, finalColumn, finalMessage) = (line, sourceMap) match {
          case (Some(l), Some(sm)) =>
            val expandedLine = l + lineOffset
            sm.getOriginalLocation(expandedLine, column.getOrElse(1)) match {
              case Some(originalLoc) =>
                val filePrefix = if (originalLoc.originalFile != "<unknown>") {
                  s"${originalLoc.originalFile}: "
                } else {
                  ""
                }
                (
                  Some(originalLoc.originalLine),
                  Some(originalLoc.originalColumn),
                  s"$filePrefix$cleanMessage"
                )
              case None =>
                (Some(expandedLine), column, cleanMessage)
            }
          case _ =>
            (line.map(_ + lineOffset), column, cleanMessage)
        }

        CompilationError(
          message = finalMessage,
          line = finalLine,
          column = finalColumn
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
