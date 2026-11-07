#!/usr/bin/env bash
set -euo pipefail

image="${1:?Usage: smoke-integrated-image.sh IMAGE}"
suffix="${GITHUB_RUN_ID:-local}-${RANDOM}"
network="identipat-smoke-${suffix}"
database="identipat-smoke-db-${suffix}"
application="identipat-smoke-app-${suffix}"
response_dir="$(mktemp -d)"

cleanup() {
  docker rm -f "$application" "$database" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$response_dir"
}
trap cleanup EXIT

docker network create "$network" >/dev/null
docker run --detach --name "$database" --network "$network" \
  --env POSTGRES_DB=identipatdb \
  --env POSTGRES_USER=identipat \
  --env POSTGRES_PASSWORD=smoke-only-password \
  postgres:16.4-bookworm >/dev/null

for _ in {1..30}; do
  if docker exec "$database" pg_isready --username identipat --dbname identipatdb >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$database" pg_isready --username identipat --dbname identipatdb >/dev/null

docker run --detach --name "$application" --network "$network" \
  --publish 127.0.0.1::8082 \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env DB_URL=jdbc:postgresql://"$database":5432/identipatdb \
  --env DB_USER=identipat \
  --env DB_PASSWORD=smoke-only-password \
  --env IDENTIPAT_AI_ENABLED=false \
  --env IDENTIPAT_AI_PROVIDER=openai \
  --env OPENAI_API_KEY=not-used-in-smoke-test \
  --env OPENAI_MODEL=not-used-in-smoke-test \
  --env OPENAI_TIMEOUT=60s \
  --env PREPROCESSING_SERVICE_BASE_URL=http://preprocessing:8090 \
  --env JWT_SECRET=identipat-smoke-secret-key-at-least-32-chars \
  --env CORS_ALLOWED_ORIGINS=http://127.0.0.1 \
  --env STANDARD_SESSION_PEPPER=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE= \
  "$image" >/dev/null

host_port="$(docker port "$application" 8082/tcp | sed 's/.*://')"
base_url="http://127.0.0.1:${host_port}/identipat-ia"

for _ in {1..60}; do
  if curl --fail --silent --show-error "$base_url/" --output "$response_dir/index.html"; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --dump-header "$response_dir/root.headers" \
  "$base_url/" --output "$response_dir/index.html"
grep -Eiq '^Content-Type: text/html' "$response_dir/root.headers"
grep -Fq '<base href="/identipat-ia/">' "$response_dir/index.html"

asset="$(grep -Eo '(src|href)="[^"]+\.(js|css)"' "$response_dir/index.html" | head -n 1 | cut -d '"' -f 2)"
curl --fail --silent --show-error --dump-header "$response_dir/asset.headers" \
  "$base_url/$asset" --output "$response_dir/asset"
grep -Eiq '^Content-Type: (text/javascript|application/javascript|text/css)' "$response_dir/asset.headers"

curl --fail --silent --show-error "$base_url/terminos-y-privacidad" \
  --output "$response_dir/client-route.html"
grep -Fq '<base href="/identipat-ia/">' "$response_dir/client-route.html"

api_status="$(curl --silent --output "$response_dir/api-error" --write-out '%{http_code}' \
  "$base_url/analyses/not-an-endpoint/extra")"
test "$api_status" = "404"
if grep -Eiq '<!doctype html>|<app-root' "$response_dir/api-error"; then
  echo "Unknown API endpoint was incorrectly rewritten to the SPA" >&2
  exit 1
fi

admin_status="$(curl --silent --output "$response_dir/admin-error" --write-out '%{http_code}' \
  "$base_url/admin/not-an-endpoint")"
test "$admin_status" = "401"

docker run --rm --entrypoint sh "$image" -c \
  '! command -v node && ! command -v npm && ! command -v mvn && ! test -e /workspace/backend/mvnw'

echo "Integrated image smoke test passed: $image"
