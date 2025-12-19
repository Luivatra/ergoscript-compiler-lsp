-- Neovim LSP configuration for ErgoScript
-- Add this to your Neovim config (e.g., ~/.config/nvim/lua/lsp/ergoscript.lua)

local configs = require('lspconfig.configs')
local util = require('lspconfig.util')

-- Define the ErgoScript LSP configuration if not already defined
if not configs.ergoscript then
  configs.ergoscript = {
    default_config = {
      cmd = {
        'java',
        '-jar',
        '/absolute/path/to/ergoscript-compiler-lsp.jar',
        'lsp'
      },
      filetypes = { 'ergoscript', 'ergo' },
      root_dir = util.root_pattern('.git', 'ergo.json', 'build.sbt'),
      settings = {},
      single_file_support = true,
    },
  }
end

-- Setup the LSP client
require('lspconfig').ergoscript.setup{
  -- Optional: Add custom on_attach function
  on_attach = function(client, bufnr)
    -- Enable completion triggered by <c-x><c-o>
    vim.api.nvim_buf_set_option(bufnr, 'omnifunc', 'v:lua.vim.lsp.omnifunc')

    -- Key mappings
    local opts = { noremap = true, silent = true, buffer = bufnr }
    vim.keymap.set('n', 'gD', vim.lsp.buf.declaration, opts)
    vim.keymap.set('n', 'gd', vim.lsp.buf.definition, opts)
    vim.keymap.set('n', 'K', vim.lsp.buf.hover, opts)
    vim.keymap.set('n', 'gi', vim.lsp.buf.implementation, opts)
    vim.keymap.set('n', '<C-k>', vim.lsp.buf.signature_help, opts)
    vim.keymap.set('n', 'gr', vim.lsp.buf.references, opts)
    vim.keymap.set('n', '<leader>rn', vim.lsp.buf.rename, opts)
    vim.keymap.set('n', '<leader>ca', vim.lsp.buf.code_action, opts)
    vim.keymap.set('n', '<leader>f', function() vim.lsp.buf.format { async = true } end, opts)
  end,

  -- Optional: Customize capabilities
  capabilities = require('cmp_nvim_lsp').default_capabilities(),
}

-- Optional: Auto-command to set filetype for .es and .ergo files
vim.api.nvim_create_autocmd({ "BufRead", "BufNewFile" }, {
  pattern = { "*.es", "*.ergo" },
  callback = function()
    vim.bo.filetype = "ergoscript"
  end,
})
