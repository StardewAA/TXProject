#!/bin/sh
set -eu
skip_oss=0
check_config=0
case "${1:-}" in
    '') ;;
    --skip-oss-check) skip_oss=1 ;;
    --check-config-only) check_config=1 ;;
    *) printf '%s\n' 'Usage: start.sh [--skip-oss-check | --check-config-only]' >&2; exit 2 ;;
esac
cd "$(dirname "$0")"
if [ ! -f .env ]; then cp .env.example .env; fi
compose() { docker compose --env-file .env -f compose.yaml "$@"; }
compose config --quiet
compose run --rm --no-deps preflight
if [ "$check_config" = 1 ]; then
    printf '%s\n' 'Configuration preflight passed. No application was built/started; cloud access remains unverified.'
    exit 0
fi
if [ "$skip_oss" = 1 ]; then
    printf '%s\n' 'WARNING: OSS checks skipped. Local-only validation is NOT full mall acceptance.'
else
    compose run --rm --no-deps verify-assets
fi
for service in gateway system infra member pay product trade promotion statistics wms ai mall-pc mall-h5 admin; do
    compose build "$service"
done
compose up -d --wait --wait-timeout 900
if [ "$skip_oss" = 1 ]; then
    compose run --rm --no-deps verify-local
    printf '%s\n' 'Local checks passed; OSS remains unverified. See deploy/mall/README.md'
else
    compose run --rm --no-deps verify
    printf '%s\n' 'Startup smoke checks passed (not full business/device acceptance). See deploy/mall/README.md'
fi
