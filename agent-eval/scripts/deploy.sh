#!/usr/bin/env bash
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DB_ROOT_PASSWORD:?DB_ROOT_PASSWORD is required}"
: "${OPENAI_API_KEY:?OPENAI_API_KEY is required}"
: "${OPENAI_BASE_URL:?OPENAI_BASE_URL is required}"
: "${JUDGE_MODEL:?JUDGE_MODEL is required}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_SRC="${PROJECT_ROOT}/backend"
FRONTEND_DIST="${PROJECT_ROOT}/frontend/dist"
DEPLOY_TEMPLATE_DIR="${PROJECT_ROOT}/deploy"
DEPLOY_PACKAGE_DIR="${DEPLOY_PACKAGE_DIR:-${PROJECT_ROOT}/.deploy-package}"
DEPLOY_TARGET_DIR="${DEPLOY_TARGET_DIR:-/opt/agent-eval}"
DEPLOY_RELEASE_DIR="${DEPLOY_TARGET_DIR}/current"
ARCHIVE_NAME="release.tgz"
LOCAL_ARCHIVE="${PROJECT_ROOT}/${ARCHIVE_NAME}"

[ -d "$BACKEND_SRC" ] || { echo "Missing backend source directory: $BACKEND_SRC"; exit 1; }
[ -d "$FRONTEND_DIST" ] || { echo "Missing frontend artifact directory: $FRONTEND_DIST"; exit 1; }

rm -rf "$DEPLOY_PACKAGE_DIR"
mkdir -p "$DEPLOY_PACKAGE_DIR/backend" "$DEPLOY_PACKAGE_DIR/frontend"

# Backend: copy source code (excluding venv, __pycache__)
rsync -a --exclude='venv' --exclude='__pycache__' --exclude='.env' "$BACKEND_SRC/" "$DEPLOY_PACKAGE_DIR/backend/"

# Frontend: copy built dist
cp -R "$FRONTEND_DIST/." "$DEPLOY_PACKAGE_DIR/frontend/"

# Deploy configs
cp "$DEPLOY_TEMPLATE_DIR/docker-compose.yml" "$DEPLOY_PACKAGE_DIR/docker-compose.yml"
cp "$DEPLOY_TEMPLATE_DIR/nginx.conf" "$DEPLOY_PACKAGE_DIR/nginx.conf"
cp "$DEPLOY_TEMPLATE_DIR/.env.example" "$DEPLOY_PACKAGE_DIR/.env"

python3 <<'PY'
from pathlib import Path
import os

path = Path(os.environ["DEPLOY_PACKAGE_DIR"]) / ".env"
content = path.read_text()
replacements = {
    "__DB_PASSWORD__": os.environ["DB_PASSWORD"],
    "__OPENAI_API_KEY__": os.environ["OPENAI_API_KEY"],
    "__OPENAI_BASE_URL__": os.environ["OPENAI_BASE_URL"],
    "__JUDGE_MODEL__": os.environ["JUDGE_MODEL"],
    "__BACKEND_PORT__": "8000",
    "__FRONTEND_PORT__": os.environ.get("FRONTEND_PORT", "5173"),
}
for old, new in replacements.items():
    content = content.replace(old, new)
# Also set DB_ROOT_PASSWORD (not in .env.example but needed for MySQL)
content += f"\nDB_ROOT_PASSWORD={os.environ['DB_ROOT_PASSWORD']}\n"
path.write_text(content)
PY

tar -C "$DEPLOY_PACKAGE_DIR" -czf "$LOCAL_ARCHIVE" .

mkdir -p "$DEPLOY_RELEASE_DIR"
tar -xzf "$LOCAL_ARCHIVE" -C "$DEPLOY_RELEASE_DIR"
rm -f "$LOCAL_ARCHIVE"

cd "$DEPLOY_RELEASE_DIR"
docker-compose down || true
docker-compose up -d --build

rm -rf "$DEPLOY_PACKAGE_DIR"
