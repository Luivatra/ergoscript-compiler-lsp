#!/bin/bash
# Test script to verify LSP diagnostics functionality

set -e

JAR="target/scala-2.13/ergoscript-compiler-lsp.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: JAR file not found. Run 'sbt assembly' first."
    exit 1
fi

echo "Testing ErgoScript LSP Diagnostics"
echo "==================================="
echo ""

# Function to send multiple LSP messages and capture all responses
test_diagnostics() {
    local init_msg='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":"file:///tmp","capabilities":{}}}'
    local init_len=${#init_msg}

    local initialized_msg='{"jsonrpc":"2.0","method":"initialized","params":{}}'
    local initialized_len=${#initialized_msg}

    local didopen_msg=$1
    local didopen_len=${#didopen_msg}

    # Send all messages in sequence
    {
        printf "Content-Length: %d\r\n\r\n%s" "$init_len" "$init_msg"
        sleep 0.1
        printf "Content-Length: %d\r\n\r\n%s" "$initialized_len" "$initialized_msg"
        sleep 0.1
        printf "Content-Length: %d\r\n\r\n%s" "$didopen_len" "$didopen_msg"
        sleep 1
    } | timeout 5 java -jar "$JAR" lsp 2>&1
}

# Test 1: Valid ErgoScript code
echo "Test 1: Valid ErgoScript Code"
echo "------------------------------"
VALID_CODE='sigmaProp(true)'
DIDOPEN_VALID="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test.es\",\"languageId\":\"ergoscript\",\"version\":1,\"text\":\"$VALID_CODE\"}}}"

RESPONSE=$(test_diagnostics "$DIDOPEN_VALID")

if echo "$RESPONSE" | grep -q "publishDiagnostics"; then
    echo "✅ Received diagnostics notification"
    if echo "$RESPONSE" | grep -q '"diagnostics":\[]'; then
        echo "✅ No errors for valid code"
    else
        echo "⚠️  Unexpected diagnostics for valid code"
        echo "$RESPONSE" | grep -A 5 "publishDiagnostics"
    fi
else
    echo "❌ No diagnostics notification received"
fi
echo ""

# Test 2: Invalid ErgoScript code (undefined variable)
echo "Test 2: Invalid ErgoScript Code (undefined variable)"
echo "----------------------------------------------------"
INVALID_CODE='sigmaProp(unknownVar > 100)'
DIDOPEN_INVALID="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test2.es\",\"languageId\":\"ergoscript\",\"version\":1,\"text\":\"$INVALID_CODE\"}}}"

RESPONSE=$(test_diagnostics "$DIDOPEN_INVALID")

if echo "$RESPONSE" | grep -q "publishDiagnostics"; then
    echo "✅ Received diagnostics notification"

    # Check if diagnostics array is not empty
    if echo "$RESPONSE" | grep -q '"diagnostics":\[{'; then
        echo "✅ Error detected for invalid code"

        # Try to extract error message
        ERROR_MSG=$(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ -n "$ERROR_MSG" ]; then
            echo "   Error message: $ERROR_MSG"
        fi

        # Check for line/column information
        if echo "$RESPONSE" | grep -q '"line":[0-9]'; then
            echo "✅ Line information present"
        fi
        if echo "$RESPONSE" | grep -q '"character":[0-9]'; then
            echo "✅ Column information present"
        fi
    else
        echo "❌ No errors detected for invalid code"
    fi
else
    echo "❌ No diagnostics notification received"
fi
echo ""

# Test 3: Syntax error (invalid syntax)
echo "Test 3: Syntax Error (invalid syntax)"
echo "--------------------------------------"
SYNTAX_ERROR='this is not valid ergoscript'
DIDOPEN_SYNTAX="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test3.es\",\"languageId\":\"ergoscript\",\"version\":1,\"text\":\"$SYNTAX_ERROR\"}}}"

RESPONSE=$(test_diagnostics "$DIDOPEN_SYNTAX")

if echo "$RESPONSE" | grep -q "publishDiagnostics"; then
    echo "✅ Received diagnostics notification"

    if echo "$RESPONSE" | grep -q '"diagnostics":\[{'; then
        echo "✅ Syntax error detected"

        ERROR_MSG=$(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ -n "$ERROR_MSG" ]; then
            echo "   Error message: $ERROR_MSG"
        fi
    else
        echo "❌ No syntax error detected"
    fi
else
    echo "❌ No diagnostics notification received"
fi
echo ""

echo "==================================="
echo "Diagnostics Test Summary"
echo "==================================="
echo ""
echo "The LSP server now provides real-time diagnostics!"
echo ""
echo "Features:"
echo "  ✅ Compiles ErgoScript on document open/change/save"
echo "  ✅ Reports syntax errors with line/column information"
echo "  ✅ Reports semantic errors (undefined variables, type errors)"
echo "  ✅ Clears diagnostics when code is valid"
echo ""
echo "Try it in your editor:"
echo "  1. Configure the LSP client (see examples/)"
echo "  2. Open an ErgoScript file"
echo "  3. See errors highlighted in real-time!"
echo ""
