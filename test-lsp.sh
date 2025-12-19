#!/bin/bash
# Test script for the ErgoScript LSP server

set -e

JAR="target/scala-2.13/ergoscript-compiler-lsp.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: JAR file not found. Run 'sbt assembly' first."
    exit 1
fi

echo "Testing ErgoScript LSP Server"
echo "=============================="
echo ""

# Test 1: Initialize request
echo "Test 1: Initialize request"
echo "--------------------------"
{
  MSG='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}}'
  LEN=${#MSG}
  printf "Content-Length: %d\r\n\r\n%s" "$LEN" "$MSG"
} | timeout 3 java -jar "$JAR" lsp 2>/dev/null | grep -A 1 "Content-Length"

echo ""
echo "✅ Initialize request successful"
echo ""

# Test 2: Version command
echo "Test 2: Version command"
echo "----------------------"
java -jar "$JAR" --version

echo ""
echo "✅ Version command successful"
echo ""

# Test 3: Help command
echo "Test 3: Help command"
echo "-------------------"
java -jar "$JAR" --help | head -10

echo ""
echo "✅ Help command successful"
echo ""

echo "=============================="
echo "All tests passed! ✅"
echo ""
echo "The LSP server is ready to use."
echo ""
echo "To start the server:"
echo "  java -jar $JAR lsp"
echo ""
echo "For editor integration, see the documentation."
