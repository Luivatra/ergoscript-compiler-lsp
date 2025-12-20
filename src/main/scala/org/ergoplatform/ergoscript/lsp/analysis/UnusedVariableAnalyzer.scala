package org.ergoplatform.ergoscript.lsp.analysis

/** Represents an unused variable detected in the code.
  *
  * @param name
  *   The name of the unused variable
  * @param line
  *   The line number where the variable is declared (0-based)
  * @param column
  *   The column where the variable name starts (0-based)
  */
case class UnusedVariable(name: String, line: Int, column: Int)

/** Analyzer for detecting unused variables in ErgoScript code. */
object UnusedVariableAnalyzer {

  /** Analyze the given script to find unused variables.
    *
    * @param script
    *   The ErgoScript source code to analyze
    * @return
    *   A list of unused variables with their positions
    */
  def findUnusedVariables(script: String): List[UnusedVariable] = {
    val declarations = extractVariableDeclarations(script)
    val unusedVars = declarations.filter { decl =>
      !isVariableUsed(decl.name, script, decl.declarationEnd)
    }
    // Convert VariableDeclaration to UnusedVariable
    unusedVars.map { decl =>
      UnusedVariable(name = decl.name, line = decl.line, column = decl.column)
    }
  }

  /** Extract all variable declarations from the script.
    *
    * @param script
    *   The ErgoScript source code
    * @return
    *   A list of variable declarations with their positions
    */
  private def extractVariableDeclarations(
      script: String
  ): List[VariableDeclaration] = {
    // Pattern to match: val <name> = <expression>
    val valPattern = """val\s+(\w+)\s*=""".r

    valPattern
      .findAllMatchIn(script)
      .map { m =>
        val name = m.group(1)
        val matchStart = m.start
        val lineNumber = script.substring(0, matchStart).count(_ == '\n')

        // Find the column where the variable name starts
        val lineStart = script.lastIndexOf('\n', matchStart) + 1
        val valKeywordStart = matchStart
        val nameStart = m.group(0).indexOf(name) + matchStart
        val column = nameStart - lineStart

        VariableDeclaration(
          name = name,
          line = lineNumber,
          column = column,
          declarationEnd = m.end
        )
      }
      .toList
  }

  /** Check if a variable is used after its declaration.
    *
    * @param varName
    *   The variable name to check
    * @param script
    *   The ErgoScript source code
    * @param declarationEnd
    *   The position where the declaration ends
    * @return
    *   true if the variable is used, false otherwise
    */
  private def isVariableUsed(
      varName: String,
      script: String,
      declarationEnd: Int
  ): Boolean = {
    // Get the code after the declaration
    val codeAfterDeclaration = script.substring(declarationEnd)

    // Create a regex pattern to match variable usage
    // We need to ensure it's a whole word, not part of another identifier
    val usagePattern = s"""\\b$varName\\b""".r

    // Check if the variable appears anywhere after its declaration
    usagePattern.findFirstIn(codeAfterDeclaration).isDefined
  }

  /** Internal representation of a variable declaration. */
  private case class VariableDeclaration(
      name: String,
      line: Int,
      column: Int,
      declarationEnd: Int
  )
}
