#!/usr/bin/env bash
set -e

INSTALL_DIR="$HOME/.connekt"
BIN_PATH="$INSTALL_DIR/connekt"
PROFILE_SNIPPET='export PATH="$HOME/.connekt:$PATH"'

mkdir -p "$INSTALL_DIR"
curl -fsSL "https://raw.githubusercontent.com/Amplicode/connekt/main/install/connekt" -o "$BIN_PATH"
chmod +x "$BIN_PATH"

FOUND_RC_FILES=()

for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
  if [ -f "$rc" ]; then
    FOUND_RC_FILES+=("$rc")
    if ! grep -Fq "$PROFILE_SNIPPET" "$rc"; then
      echo "$PROFILE_SNIPPET" >> "$rc"
      echo "✅ Updated $rc"
    fi
  fi
done

echo "✅ Installed to $BIN_PATH"

if [ ${#FOUND_RC_FILES[@]} -gt 0 ]; then
  RESTART_SUGGESTION="Restart your terminal"
  SOURCE_SUGGESTIONS=()

  for rc in "${FOUND_RC_FILES[@]}"; do
    SOURCE_SUGGESTIONS+=("source $rc")
  done

  if [ ${#SOURCE_SUGGESTIONS[@]} -gt 0 ]; then
    RESTART_SUGGESTION+=" or run '${SOURCE_SUGGESTIONS[0]}'"
  fi

  echo "ℹ️ $RESTART_SUGGESTION"
else
  echo "ℹ️ Add the following line to your shell profile:"
  echo "  $PROFILE_SNIPPET"
fi