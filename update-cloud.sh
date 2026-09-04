#!/usr/bin/env bash
set -Eeuo pipefail

# Backward-compatible entry point. Updates now use the same fixed-image
# deployment path; the former JAR-only package could not rebuild this image.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
echo "update-cloud.sh now delegates to deploy-cloud.sh (fixed GHCR sha-* image tag)."
exec bash "$SCRIPT_DIR/deploy-cloud.sh"
