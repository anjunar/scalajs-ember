#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-}"
PUBLISHING_TYPE="${PUBLISHING_TYPE:-AUTOMATIC}"
DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-}"
POLL_SECONDS="${POLL_SECONDS:-10}"
MAX_POLLS="${MAX_POLLS:-90}"
SKIP_PUBLISH_SIGNED="${SKIP_PUBLISH_SIGNED:-0}"

usage() {
  cat <<'EOF'
Usage: scripts/publish-central.sh [options]

Options:
  --version VERSION             Publish VERSION instead of reading build.sbt
  --publishing-type TYPE        AUTOMATIC or USER_MANAGED (default: AUTOMATIC)
  --deployment-name NAME        Sonatype deployment name
  --poll-seconds SECONDS        Delay between status checks (default: 10)
  --max-polls COUNT             Maximum status checks (default: 90)
  --skip-publish-signed         Reuse an existing target/sona-staging bundle
  -h, --help                    Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="$2"
      shift 2
      ;;
    --publishing-type)
      PUBLISHING_TYPE="$2"
      shift 2
      ;;
    --deployment-name)
      DEPLOYMENT_NAME="$2"
      shift 2
      ;;
    --poll-seconds)
      POLL_SECONDS="$2"
      shift 2
      ;;
    --max-polls)
      MAX_POLLS="$2"
      shift 2
      ;;
    --skip-publish-signed)
      SKIP_PUBLISH_SIGNED=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "$REPO_ROOT"

BUILD_VERSION="$(sed -n \
  -e 's/^[[:space:]]*version[[:space:]]*:=[[:space:]]*"\([^"].*\)".*/\1/p' \
  -e 's/^[[:space:]]*ThisBuild[[:space:]]*\/[[:space:]]*version[[:space:]]*:=[[:space:]]*"\([^"].*\)".*/\1/p' \
  build.sbt | head -n 1)"

if [[ -z "$BUILD_VERSION" ]]; then
  echo "Could not read the version from build.sbt." >&2
  exit 1
fi

if [[ -z "$VERSION" ]]; then
  VERSION="$BUILD_VERSION"
fi

# The bundle is built from build.sbt. A different --version would upload artifacts under a
# deployment name that does not match their POMs.
if [[ "$VERSION" != "$BUILD_VERSION" && "$SKIP_PUBLISH_SIGNED" -eq 0 ]]; then
  echo "Version ${VERSION} does not match build.sbt (${BUILD_VERSION}). Set the version in build.sbt first." >&2
  exit 1
fi

# A SNAPSHOT is published straight to the snapshot repository by sbt and never reaches
# target/sona-staging, so this script has nothing to upload for it.
if [[ "$VERSION" == *-SNAPSHOT ]]; then
  echo "Refusing to publish SNAPSHOT version ${VERSION} to Maven Central. Set a release version in build.sbt." >&2
  exit 1
fi

if [[ -z "$DEPLOYMENT_NAME" ]]; then
  DEPLOYMENT_NAME="com.anjunar:scalajs-ember:${VERSION}"
fi

if [[ "$PUBLISHING_TYPE" != "AUTOMATIC" && "$PUBLISHING_TYPE" != "USER_MANAGED" ]]; then
  echo "--publishing-type must be AUTOMATIC or USER_MANAGED." >&2
  exit 2
fi

BUNDLE_DIR="${REPO_ROOT}/target/sona-staging"
BUNDLE_ZIP="${REPO_ROOT}/target/central-bundle-${VERSION}.zip"

if [[ "$SKIP_PUBLISH_SIGNED" -eq 0 ]]; then
  case "$BUNDLE_DIR" in
    "$REPO_ROOT"/target/*) rm -rf -- "$BUNDLE_DIR" ;;
    *) echo "Refusing to clean staging outside target: $BUNDLE_DIR" >&2; exit 1 ;;
  esac
  sbt --server "publishSigned"
fi

if [[ ! -d "$BUNDLE_DIR" ]]; then
  echo "Bundle directory not found: ${BUNDLE_DIR}" >&2
  exit 1
fi

rm -f "$BUNDLE_ZIP"
(
  cd "$BUNDLE_DIR"
  if command -v zip >/dev/null 2>&1; then
    zip -qr "$BUNDLE_ZIP" .
  else
    jar --create --file "$BUNDLE_ZIP" -C . .
  fi
)

get_credential_value() {
  local key="$1"
  local env_name="$2"
  local env_value="${!env_name:-}"
  local cred_file="${HOME}/.sbt/sonatype_central_credentials"

  if [[ -n "$env_value" ]]; then
    printf '%s\n' "$env_value"
    return
  fi

  if [[ ! -f "$cred_file" ]]; then
    echo "Credentials file not found: $cred_file" >&2
    exit 1
  fi

  local value
  value="$(sed -n "s/^${key}=//p" "$cred_file" | head -n 1)"
  if [[ -z "$value" ]]; then
    echo "Entry '$key' is missing from $cred_file" >&2
    exit 1
  fi

  printf '%s\n' "$value"
}

USER_NAME="$(get_credential_value user SONATYPE_CENTRAL_USERNAME)"
PASSWORD="$(get_credential_value password SONATYPE_CENTRAL_PASSWORD)"

if [[ -z "$USER_NAME" || -z "$PASSWORD" ]]; then
  echo "Sonatype credentials are incomplete." >&2
  exit 1
fi

TOKEN="$(printf '%s' "$USER_NAME:$PASSWORD" | base64 | tr -d '\r\n')"
if command -v python3 >/dev/null 2>&1; then
  ENCODED_NAME="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$DEPLOYMENT_NAME")"
else
  ENCODED_NAME="${DEPLOYMENT_NAME//:/%3A}"
fi
UPLOAD_URL="https://central.sonatype.com/api/v1/publisher/upload?name=${ENCODED_NAME}&publishingType=${PUBLISHING_TYPE}"

echo "Uploading bundle: ${BUNDLE_ZIP}"
DEPLOYMENT_ID="$(curl --silent --show-error --fail \
  --request POST \
  --header "Authorization: Bearer ${TOKEN}" \
  --form "bundle=@${BUNDLE_ZIP}" \
  "$UPLOAD_URL")"

[[ -n "$DEPLOYMENT_ID" ]] || {
  echo "Sonatype did not return a deployment ID." >&2
  exit 1
}

echo "Deployment ID: ${DEPLOYMENT_ID}"

for ((attempt = 1; attempt <= MAX_POLLS; attempt++)); do
  sleep "$POLL_SECONDS"
  STATUS_JSON="$(curl --silent --show-error --fail \
    --request POST \
    --header "Authorization: Bearer ${TOKEN}" \
    "https://central.sonatype.com/api/v1/publisher/status?id=${DEPLOYMENT_ID}")"
  if command -v python3 >/dev/null 2>&1; then
    DEPLOYMENT_STATE="$(python3 -c 'import json, sys; print(json.loads(sys.stdin.read()).get("deploymentState", ""))' <<<"$STATUS_JSON")"
  else
    DEPLOYMENT_STATE="$(sed -n 's/.*"deploymentState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$STATUS_JSON" | head -n 1)"
  fi
  echo "[${attempt}/${MAX_POLLS}] Status: ${DEPLOYMENT_STATE}"

  case "$DEPLOYMENT_STATE" in
    PUBLISHED)
      echo "Maven Central publishing completed."
      printf '%s\n' "$STATUS_JSON"
      exit 0
      ;;
    FAILED)
      echo "Deployment ended with status ${DEPLOYMENT_STATE}."
      printf '%s\n' "$STATUS_JSON"
      exit 1
      ;;
    VALIDATED)
      if [[ "$PUBLISHING_TYPE" == "USER_MANAGED" ]]; then
        echo "Deployment validated. Publish it in the Central Portal."
        printf '%s\n' "$STATUS_JSON"
        exit 1
      fi
      ;;
  esac
done

echo "Timed out while waiting for the Sonatype status. Deployment ID: ${DEPLOYMENT_ID}" >&2
exit 1
