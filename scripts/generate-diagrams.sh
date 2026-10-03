#!/usr/bin/env bash
# ==============================================================================
# Payflow API — D2 Diagram Compilation & Verification Automation
# Compiles declarative D2 sources in docs/diagrams/ to self-contained SVGs
# in docs/assets/diagrams/ with automatic dark/light theme support.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIAGRAMS_DIR="$REPO_ROOT/docs/diagrams"
OUTPUT_DIR="$REPO_ROOT/docs/assets/diagrams"

# Ensure D2 CLI is installed
if ! command -v d2 &> /dev/null; then
  echo "❌ Error: D2 CLI is not installed or not in PATH."
  echo "   Install via Homebrew: brew install d2"
  echo "   Install via script:   curl -fsSL https://d2lang.com/install.sh | sh -s --"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

CHECK_MODE=false
if [[ "${1:-}" == "--check" ]]; then
  CHECK_MODE=true
fi

echo "🎨 Compiling D2 diagrams from $DIAGRAMS_DIR to $OUTPUT_DIR..."

compiled_count=0
for d2file in "$DIAGRAMS_DIR"/*.d2; do
  [ -f "$d2file" ] || continue
  name="$(basename "$d2file" .d2)"
  target_svg="$OUTPUT_DIR/$name.svg"
  
  # Compile with theme 0 (default/light) and dark-theme 200 (slate/dark)
  # ELK layout provides superior orthogonal routing and generous container padding
  d2 --layout=elk \
     --elk-nodeNodeBetweenLayers=100 \
     --elk-padding="[top=80,left=50,bottom=50,right=50]" \
     --elk-edgeNodeBetweenLayers=60 \
     --theme=0 \
     --dark-theme=200 \
     "$d2file" "$target_svg"
  compiled_count=$((compiled_count + 1))
  echo "  ✅ Compiled: $name.d2 -> $name.svg"
done

echo "🎉 Successfully compiled $compiled_count D2 diagrams."

if [ "$CHECK_MODE" = true ]; then
  echo "🔍 Checking for uncommitted diagram drift..."
  if ! git diff --exit-code "$OUTPUT_DIR" > /dev/null; then
    echo "❌ Error: Pre-compiled D2 SVGs are out of sync with docs/diagrams/*.d2 sources!"
    echo "   Please run './scripts/generate-diagrams.sh' locally and commit the updated SVGs."
    git diff --stat "$OUTPUT_DIR"
    exit 1
  fi
  echo "✅ All diagram SVGs are up-to-date with zero drift."
fi
