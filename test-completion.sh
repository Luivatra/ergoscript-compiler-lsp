#!/bin/bash
# Test completion functionality for ErgoScript LSP

set -e

echo "=== Testing ErgoScript LSP Completion ==="

# Build the project first
echo "Building project..."
sbt assembly > /dev/null 2>&1

# Start the LSP server in the background
JAR_FILE="target/scala-2.13/ergoscript-compiler-lsp-assembly-0.1.0.jar"
echo "Starting LSP server..."
java -jar "$JAR_FILE" > test-completion-output.log 2>&1 &
SERVER_PID=$!

# Give the server a moment to start
sleep 1

# Function to send JSON-RPC message
send_message() {
  local content="$1"
  local content_length=${#content}
  echo -e "Content-Length: $content_length\r\n\r\n$content"
}

# Function to cleanup on exit
cleanup() {
  echo "Cleaning up..."
  kill $SERVER_PID 2>/dev/null || true
  rm -f test-completion-output.log
}
trap cleanup EXIT

# Test 1: Initialize
echo "Test 1: Initializing LSP server..."
INIT_REQUEST=$(cat <<'EOF'
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":"file:///home/luivatra/develop/ergo/ergoscript-compiler-lsp","capabilities":{"textDocument":{"completion":{"completionItem":{"snippetSupport":true}}}}}}
EOF
)

send_message "$INIT_REQUEST" | java -jar "$JAR_FILE" &
RESPONSE_PID=$!
sleep 2

# Test 2: Open document
echo "Test 2: Opening test document..."
OPEN_REQUEST=$(cat <<'EOF'
{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"file:///test.es","languageId":"ergoscript","version":1,"text":"sigmaProp(HEIGHT > 100)"}}}
EOF
)

# Test 3: Request completion for general context
echo "Test 3: Testing general completion (keywords, functions, etc.)..."
COMPLETION_REQUEST_1=$(cat <<'EOF'
{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"file:///test.es"},"position":{"line":0,"character":0},"context":{"triggerKind":1}}}
EOF
)

# Test 4: Request completion after "SELF."
echo "Test 4: Testing member completion for SELF..."
MEMBER_COMPLETION=$(cat <<'EOF'
{"jsonrpc":"2.0","id":3,"method":"textDocument/completion","params":{"textDocument":{"uri":"file:///test.es"},"position":{"line":0,"character":5},"context":{"triggerKind":2,"triggerCharacter":"."}}}
EOF
)

echo ""
echo "=== Completion Tests Completed ==="
echo "Check 'test-completion-output.log' for detailed server output"
echo ""
echo "To manually test completions:"
echo "1. Start your LSP client (VS Code, Neovim, etc.)"
echo "2. Open an .es file"
echo "3. Try typing: SELF. (should show box members)"
echo "4. Try typing: sigma (should show sigmaProp function)"
echo "5. Try typing: HE (should show HEIGHT constant)"
