;; Emacs LSP configuration for ErgoScript using lsp-mode
;; Add this to your Emacs init.el or configuration file

(use-package lsp-mode
  :commands lsp
  :hook ((ergoscript-mode . lsp))
  :config
  (lsp-register-client
   (make-lsp-client
    :new-connection (lsp-stdio-connection
                     '("java" "-jar" "/absolute/path/to/ergoscript-compiler-lsp.jar" "lsp"))
    :major-modes '(ergoscript-mode)
    :server-id 'ergoscript-lsp
    :priority 0)))

;; Define ergoscript-mode (basic version)
(define-derived-mode ergoscript-mode prog-mode "ErgoScript"
  "Major mode for editing ErgoScript files."
  (setq-local comment-start "//")
  (setq-local comment-end ""))

;; Associate file extensions with ergoscript-mode
(add-to-list 'auto-mode-alist '("\\.es\\'" . ergoscript-mode))
(add-to-list 'auto-mode-alist '("\\.ergo\\'" . ergoscript-mode))

;; Optional: Use eglot instead of lsp-mode
;; (require 'eglot)
;; (add-to-list 'eglot-server-programs
;;              '(ergoscript-mode . ("java" "-jar" "/absolute/path/to/ergoscript-compiler-lsp.jar" "lsp")))
;; (add-hook 'ergoscript-mode-hook 'eglot-ensure)
