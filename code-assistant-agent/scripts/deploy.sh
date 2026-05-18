#!/usr/bin/env bash
set -euo pipefail

: "${AI_API_KEY:?AI_API_KEY is required}"
: "${AI_API_BASE_URL:?AI_API_BASE_URL is required}"
: "${AI_API_MODEL:?AI_API_MODEL is required}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_JAR="${PROJECT_ROOT}/backend/target/backend-0.0.1-SNAPSHOT.jar"
FRONTEND_DIST="${PROJECT_ROOT}/frontend/dist"
DEPLOY_TEMPLATE_DIR="${PROJECT_ROOT}/deploy"
DEPLOY_PACKAGE_DIR="${DEPLOY_PACKAGE_DIR:-${PROJECT_ROOT}/.deploy-package}"
DEPLOY_TARGET_DIR="${DEPLOY_TARGET_DIR:-/opt/code-assistant-agent}"
DEPLOY_RELEASE_DIR="${DEPLOY_TARGET_DIR}/current"
ARCHIVE_NAME="release.tgz"
LOCAL_ARCHIVE="${PROJECT_ROOT}/${ARCHIVE_NAME}"

[ -f "$BACKEND_JAR" ] || { echo "Missing backend artifact: $BACKEND_JAR"; exit 1; }
[ -d "$FRONTEND_DIST" ] || { echo "Missing frontend artifact directory: $FRONTEND_DIST"; exit 1; }

rm -rf "$DEPLOY_PACKAGE_DIR"
mkdir -p "$DEPLOY_PACKAGE_DIR/backend" "$DEPLOY_PACKAGE_DIR/frontend"

cp "$BACKEND_JAR" "$DEPLOY_PACKAGE_DIR/backend/app.jar"
cp -R "$FRONTEND_DIST/." "$DEPLOY_PACKAGE_DIR/frontend/"
cp "$DEPLOY_TEMPLATE_DIR/docker-compose.yml" "$DEPLOY_PACKAGE_DIR/docker-compose.yml"
cp "$DEPLOY_TEMPLATE_DIR/nginx.conf" "$DEPLOY_PACKAGE_DIR/nginx.conf"
cp "$DEPLOY_TEMPLATE_DIR/.env.example" "$DEPLOY_PACKAGE_DIR/.env"

python3 <<'PY'
from pathlib import Path
import os

path = Path(os.environ["DEPLOY_PACKAGE_DIR"]) / ".env"
content = path.read_text()
replacements = {
    "__AI_API_KEY__": os.environ["AI_API_KEY"],
    "__AI_API_BASE_URL__": os.environ["AI_API_BASE_URL"],
    "__AI_API_MODEL__": os.environ["AI_API_MODEL"],
    "__BACKEND_PORT__": "8080",
    "__FRONTEND_PORT__": "80",
}
for old, new in replacements.items():
    content = content.replace(old, new)
path.write_text(content)
PY

tar -C "$DEPLOY_PACKAGE_DIR" -czf "$LOCAL_ARCHIVE" .

mkdir -p "$DEPLOY_RELEASE_DIR"
tar -xzf "$LOCAL_ARCHIVE" -C "$DEPLOY_RELEASE_DIR"
rm -f "$LOCAL_ARCHIVE"

cd "$DEPLOY_RELEASE_DIR"
docker-compose down || true
docker-compose up -d

rm -rf "$DEPLOY_PACKAGE_DIR"
