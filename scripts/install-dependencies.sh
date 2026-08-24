#!/usr/bin/env bash
# Downloads the frontend, backend, and container dependencies required by the project.
# Run from the repository root: ./scripts/install-dependencies.sh

set -euo pipefail

skip_infrastructure=false
if [[ "${1:-}" == "--skip-infrastructure" ]]; then
  skip_infrastructure=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--skip-infrastructure]" >&2
  exit 2
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command npm
require_command mvn

(
  cd "$project_root/frontend"
  npm ci
)

(
  cd "$project_root/backend"
  mvn dependency:go-offline
)

if [[ "$skip_infrastructure" == false ]]; then
  require_command docker
  (
    cd "$project_root"
    docker compose pull
    docker compose up -d
  )
fi

echo "Dependencies are ready."
