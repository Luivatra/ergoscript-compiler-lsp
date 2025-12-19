#!/bin/bash
# Interactive test script to verify LSP diagnostics functionality
# This script shows the actual LSP communication

set -e

JAR="target/scala-2.13/ergoscript-compiler-lsp.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: JAR file not found. Run 'sbt assembly' first."
    exit 1
fi

echo "Testing ErgoScript LSP Diagnostics (Interactive)"
echo "================================================"
echo ""

# Create a temporary script file to send messages
TEMP_SCRIPT=$(mktemp)

cat > "$TEMP_SCRIPT" << 'EOF'
#!/bin/bash

# Function to send LSP message
send_message() {
    local msg=$1
    local len=${#msg}
    printf "Content-Length: %d\r\n\r\n%s" "$len" "$msg"
}

# Initialize
INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":"file:///tmp","capabilities":{}}}'
send_message "$INIT"

# Wait for response
sleep 0.5

# Send initialized notification
INITIALIZED='{"jsonrpc":"2.0","method":"initialized","params":{}}'
send_message "$INITIALIZED"

sleep 0.2

# Open document with VALID code
echo "--- Opening document with VALID code ---" >&2
VALID_CODE='sigmaProp(true)'
DIDOPEN_VALID="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"file:///test-valid.es\",\"languageId\":\"ergoscript\",\"version\":1,\"text\":\"$VALID_CODE\"}}}"
send_message "$DIDOPEN_VALID"

sleep 1

# Change document to INVALID code
echo "--- Changing document to INVALID code (undefined variable) ---" >&2
INVALID_CODE='sigmaProp(unknownVariable > 100)'
DIDCHANGE_INVALID="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didChange\",\"params\":{\"textDocument\":{\"uri\":\"file:///test-valid.es\",\"version\":2},\"contentChanges\":[{\"text\":\"$INVALID_CODE\"}]}}"
send_message "$DIDCHANGE_INVALID"

sleep 1

# Change document back to VALID code
echo "--- Changing document back to VALID code ---" >&2
DIDCHANGE_VALID="{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didChange\",\"params\":{\"textDocument\":{\"uri\":\"file:///test-valid.es\",\"version\":3},\"contentChanges\":[{\"text\":\"$VALID_CODE\"}]}}"
send_message "$DIDCHANGE_VALID"

sleep 1

# Shutdown
SHUTDOWN='{"jsonrpc":"2.0","id":99,"method":"shutdown","params":{}}'
send_message "$SHUTDOWN"

sleep 0.2

EXIT='{"jsonrpc":"2.0","method":"exit","params":{}}'
send_message "$EXIT"
EOF

chmod +x "$TEMP_SCRIPT"

echo "Running LSP server and sending test messages..."
echo "================================================"
echo ""
echo "Watch for 'publishDiagnostics' notifications below:"
echo ""

# Run the test and capture both stdout and stderr
"$TEMP_SCRIPT" | java -jar "$JAR" lsp 2>&1 | while IFS= read -r line; do
    # Highlight diagnostics notifications
    if echo "$line" | grep -q "publishDiagnostics"; then
        echo ""
        echo ">>> DIAGNOSTICS NOTIFICATION DETECTED <<<"
        echo "$line"

        # Try to parse and display diagnostic info
        if echo "$line" | grep -q '"diagnostics":\[\]'; then
            echo "    ✅ No errors (empty diagnostics)"
        elif echo "$line" | grep -q '"diagnostics":\[{'; then
            echo "    ⚠️  Error detected:"
            # Extract message if present
            MSG=$(echo "$line" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
            if [ -n "$MSG" ]; then
                echo "       Message: $MSG"
            fi
        fi
        echo ""
    elif echo "$line" | grep -q "Compilation error:"; then
        echo ">>> $line"
    elif echo "$line" | grep -q "Compilation successful"; then
        echo ">>> $line"
    elif echo "$line" | grep -q "Opening document\|Changing document"; then
        echo ""
        echo "$line"
        echo ""
    fi
done

# Cleanup
rm -f "$TEMP_SCRIPT"

echo ""
echo "================================================"
echo "Test completed!"
echo ""
echo "If you saw 'publishDiagnostics' notifications above,"
echo "then the diagnostics feature is working correctly."
echo ""
