#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 || ! $2 =~ ^[A-Za-z0-9_-]{22}$ ]]; then
    printf 'Usage: %s PRIVATE_KEY.pem REQUEST_CODE\n' "$0" >&2
    exit 2
fi

printf 'goldpayamak-license-v1:%s' "$2" |
    openssl dgst -sha256 -sign "$1" |
    openssl base64 -A |
    tr '+/' '-_' |
    tr -d '='
printf '\n'
