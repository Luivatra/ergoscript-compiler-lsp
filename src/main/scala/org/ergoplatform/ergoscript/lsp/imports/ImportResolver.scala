package org.ergoplatform.ergoscript.lsp.imports

import java.nio.file.{Files, Path, Paths}
import scala.util.{Try, Success, Failure}
import scala.collection.mutable
import com.typesafe.scalalogging.LazyLogging

/** Represents an import directive in ErgoScript code.
  *
  * @param path
  *   The path specified in the import (e.g., "path/to/library.es" or
  *   "lib:utils.es")
  * @param line
  *   The line number where the import appears (1-based)
  * @param startColumn
  *   The starting column of the import directive (1-based)
  * @param endColumn
  *   The ending column of the import directive (1-based)
  */
case class ImportDirective(
    path: String,
    line: Int,
    startColumn: Int,
    endColumn: Int
)

/** Result of processing imports in a file.
  *
  * @param expandedCode
  *   The expanded code with source mapping
  * @param imports
  *   List of import directives found
  * @param errors
  *   Any errors encountered during import resolution
  */
case class ImportResult(
    expandedCode: ExpandedCode,
    imports: List[ImportDirective],
    errors: List[String]
)

/** Resolver for ErgoScript import directives. Handles parsing #import
  * statements and expanding them by including the referenced file contents.
  * Supports new import syntax: lib:, src:, and traditional paths.
  */
object ImportResolver extends LazyLogging {

  // Pattern to match: #import path/to/file.es; or #import lib:file.es; etc.
  private val importPattern = """#import\s+([^;]+);""".r

  /** Parse import directives from ErgoScript code.
    *
    * @param code
    *   The ErgoScript source code
    * @return
    *   List of import directives found in the code
    */
  def parseImports(code: String): List[ImportDirective] = {
    importPattern
      .findAllMatchIn(code)
      .map { m =>
        val path = m.group(1).trim
        val matchStart = m.start
        val matchEnd = m.end
        val lineNumber =
          code.substring(0, matchStart).count(_ == '\n') + 1 // 1-based
        val lineStart = code.lastIndexOf('\n', matchStart) + 1
        val startColumn = matchStart - lineStart + 1 // 1-based
        val endColumn = matchEnd - lineStart + 1 // 1-based

        ImportDirective(
          path = path,
          line = lineNumber,
          startColumn = startColumn,
          endColumn = endColumn
        )
      }
      .toList
  }

  /** Expand all imports in the given code with source mapping.
    *
    * @param code
    *   The ErgoScript source code
    * @param currentFilePath
    *   The path of the current file (for resolving relative imports)
    * @param workspaceRoot
    *   The workspace root directory
    * @return
    *   ImportResult with expanded code, source map, and any errors
    */
  def expandImports(
      code: String,
      currentFilePath: Option[String],
      workspaceRoot: Option[String]
  ): ImportResult = {
    val visited = mutable.Set[String]()
    val importChain = mutable.ListBuffer[String]()

    currentFilePath.foreach { fp =>
      visited.add(fp)
      importChain += fp
    }

    expandImportsInternal(
      code,
      currentFilePath,
      workspaceRoot,
      visited,
      importChain.toList
    )
  }

  /** Internal method for expanding imports with source mapping.
    */
  private def expandImportsInternal(
      code: String,
      currentFilePath: Option[String],
      workspaceRoot: Option[String],
      visited: mutable.Set[String],
      importChain: List[String]
  ): ImportResult = {
    val imports = parseImports(code)
    val errors = mutable.ListBuffer[String]()

    // If no imports, return code with identity source map
    if (imports.isEmpty) {
      val sourceMap = currentFilePath
        .map(fp => SourceMap.identity(code, fp))
        .getOrElse(SourceMap.identity(code, "<unknown>"))
      return ImportResult(sourceMap, List.empty, List.empty)
    }

    // Build the expanded code and source map
    val lines = code.split("\n", -1)
    val expandedLines = mutable.ListBuffer[String]()
    val sourceMapBuilder = mutable.Map[Int, SourceLocation]()
    var currentExpandedLine = 1
    var currentSourceLine = 1

    // Track which lines have imports
    val importLineMap = imports.map(imp => imp.line -> imp).toMap

    for (line <- lines) {
      if (importLineMap.contains(currentSourceLine)) {
        val imp = importLineMap(currentSourceLine)

        // Resolve and expand the import
        resolveImportPath(imp.path, currentFilePath, workspaceRoot) match {
          case Some(resolvedPath) =>
            val resolvedPathStr =
              resolvedPath.toAbsolutePath.normalize.toString

            if (visited.contains(resolvedPathStr)) {
              errors += s"Circular import detected: ${imp.path}"
              // Add error comment
              expandedLines += s"// ERROR: Circular import: ${imp.path}"
              sourceMapBuilder(currentExpandedLine) = SourceLocation(
                currentFilePath.getOrElse("<unknown>"),
                currentSourceLine,
                1,
                importChain
              )
              currentExpandedLine += 1
            } else {
              Try {
                visited.add(resolvedPathStr)
                val importedContent = Files.readString(resolvedPath)
                val newChain = importChain :+ resolvedPathStr

                // Recursively expand imports in the imported file
                val nestedResult = expandImportsInternal(
                  importedContent,
                  Some(resolvedPathStr),
                  workspaceRoot,
                  visited,
                  newChain
                )

                errors ++= nestedResult.errors

                // Add imported content
                // Skip the comment if either the imported file OR the importing file contains @contract/@param
                val importedCode = nestedResult.expandedCode.code
                val isImportedTemplate = importedCode.contains(
                  "@contract"
                ) || importedCode.contains("@param")
                val isImportingTemplate =
                  code.contains("@contract") || code.contains("@param")

                if (!isImportedTemplate && !isImportingTemplate) {
                  expandedLines += s"// Imported from ${imp.path}"
                  sourceMapBuilder(currentExpandedLine) = SourceLocation(
                    currentFilePath.getOrElse("<unknown>"),
                    currentSourceLine,
                    1,
                    importChain
                  )
                  currentExpandedLine += 1
                }

                // Add each line from the imported file with its source map
                val importedLines = importedCode.split("\n", -1)
                importedLines.zipWithIndex.foreach { case (importedLine, idx) =>
                  expandedLines += importedLine
                  // Get the original location from the nested source map
                  val importedLineNum = idx + 1
                  val originalLoc = nestedResult.expandedCode
                    .getOriginalLocation(importedLineNum)
                    .getOrElse(
                      SourceLocation(
                        resolvedPathStr,
                        importedLineNum,
                        1,
                        newChain
                      )
                    )
                  sourceMapBuilder(currentExpandedLine) = originalLoc
                  currentExpandedLine += 1
                }

                if (!isImportedTemplate && !isImportingTemplate) {
                  expandedLines += s"// End of import ${imp.path}"
                  sourceMapBuilder(currentExpandedLine) = SourceLocation(
                    currentFilePath.getOrElse("<unknown>"),
                    currentSourceLine,
                    1,
                    importChain
                  )
                  currentExpandedLine += 1
                }

              } match {
                case Success(_) => // Import expanded successfully
                case Failure(ex) =>
                  errors += s"Failed to read import '${imp.path}': ${ex.getMessage}"
                  expandedLines += s"// ERROR: Failed to read ${imp.path}"
                  sourceMapBuilder(currentExpandedLine) = SourceLocation(
                    currentFilePath.getOrElse("<unknown>"),
                    currentSourceLine,
                    1,
                    importChain
                  )
                  currentExpandedLine += 1
              }
            }

          case None =>
            errors += s"Could not resolve import path: ${imp.path}"
            expandedLines += s"// ERROR: Could not resolve ${imp.path}"
            sourceMapBuilder(currentExpandedLine) = SourceLocation(
              currentFilePath.getOrElse("<unknown>"),
              currentSourceLine,
              1,
              importChain
            )
            currentExpandedLine += 1
        }
      } else {
        // Regular line, not an import
        expandedLines += line
        sourceMapBuilder(currentExpandedLine) = SourceLocation(
          currentFilePath.getOrElse("<unknown>"),
          currentSourceLine,
          1,
          importChain
        )
        currentExpandedLine += 1
      }
      currentSourceLine += 1
    }

    val expandedCodeStr = expandedLines.mkString("\n")
    val sourceMap =
      ExpandedCode(expandedCodeStr, sourceMapBuilder.toMap, expandedLines.size)

    ImportResult(
      expandedCode = sourceMap,
      imports = imports,
      errors = errors.toList
    )
  }

  /** Resolve an import path to an absolute file path. Supports new prefix
    * syntax: - lib:path - Look in project's lib/ directory - src:path - Look in
    * project's src/ directory - ./path or ../path - Relative to current file -
    * path - Look in project root, then relative to current file
    *
    * @param importPath
    *   The path from the import directive
    * @param currentFilePath
    *   The path of the file containing the import
    * @param workspaceRoot
    *   The workspace root directory
    * @return
    *   Resolved absolute path if found
    */
  private def resolveImportPath(
      importPath: String,
      currentFilePath: Option[String],
      workspaceRoot: Option[String]
  ): Option[Path] = {
    // Handle prefixed paths
    if (importPath.startsWith("lib:")) {
      val relativePath = importPath.substring(4)
      return workspaceRoot.flatMap { root =>
        val path = Paths.get(root, "lib", relativePath)
        if (Files.exists(path) && Files.isRegularFile(path)) Some(path)
        else None
      }
    }

    if (importPath.startsWith("src:")) {
      val relativePath = importPath.substring(4)
      return workspaceRoot.flatMap { root =>
        val path = Paths.get(root, "src", relativePath)
        if (Files.exists(path) && Files.isRegularFile(path)) Some(path)
        else None
      }
    }

    // Handle relative paths (starts with ./ or ../)
    if (importPath.startsWith("./") || importPath.startsWith("../")) {
      return currentFilePath.flatMap { current =>
        val currentDir = Paths.get(current).getParent
        if (currentDir != null) {
          val path = currentDir.resolve(importPath).normalize()
          if (Files.exists(path) && Files.isRegularFile(path)) Some(path)
          else None
        } else {
          None
        }
      }
    }

    // Try workspace root first (for backwards compatibility with ergoscript/ folder)
    val workspaceAttempt = workspaceRoot.flatMap { root =>
      val path = Paths.get(root, "ergoscript", importPath)
      if (Files.exists(path) && Files.isRegularFile(path)) Some(path) else None
    }

    if (workspaceAttempt.isDefined) return workspaceAttempt

    // Try project root
    val projectRootAttempt = workspaceRoot.flatMap { root =>
      val path = Paths.get(root, importPath)
      if (Files.exists(path) && Files.isRegularFile(path)) Some(path) else None
    }

    if (projectRootAttempt.isDefined) return projectRootAttempt

    // Try relative to current file
    val relativeAttempt = currentFilePath.flatMap { current =>
      val currentDir = Paths.get(current).getParent
      if (currentDir != null) {
        val path = currentDir.resolve(importPath)
        if (Files.exists(path) && Files.isRegularFile(path)) Some(path)
        else None
      } else {
        None
      }
    }

    if (relativeAttempt.isDefined) return relativeAttempt

    // Try as absolute path
    val absolutePath = Paths.get(importPath)
    if (Files.exists(absolutePath) && Files.isRegularFile(absolutePath)) {
      Some(absolutePath)
    } else {
      None
    }
  }

  /** Find the project root from a file path. Looks for project markers in order
    * of priority: 1. ergo.json (new project file) 2. ergoproject.json
    * (alternative name) 3. .ergoscript (hidden marker file) 4. ergoscript/
    * (legacy folder) 5. .git (fallback to git root)
    *
    * @param startPath
    *   The file path to start searching from
    * @return
    *   The project root path if found
    */
  def findProjectRoot(startPath: Path): Option[Path] = {
    val markers = List(
      "ergo.json",
      "ergoproject.json",
      ".ergoscript",
      "ergoscript",
      ".git"
    )

    var current =
      if (Files.isDirectory(startPath)) startPath else startPath.getParent
    while (current != null) {
      val found = markers.exists { marker =>
        Files.exists(current.resolve(marker))
      }
      if (found) {
        return Some(current)
      }
      current = current.getParent
    }
    None
  }

  /** Get the workspace root from a file URI. Uses improved project root
    * detection.
    *
    * @param uri
    *   The file URI
    * @return
    *   The workspace root path
    */
  def getWorkspaceRootFromUri(uri: String): Option[String] = {
    Try {
      val path = if (uri.startsWith("file://")) {
        Paths.get(java.net.URI.create(uri))
      } else {
        Paths.get(uri)
      }

      findProjectRoot(path).map(_.toString)
    }.toOption.flatten
  }
}
