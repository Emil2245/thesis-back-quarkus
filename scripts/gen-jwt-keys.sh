#!/usr/bin/env bash
# Genera un par de claves RSA-2048 (PKCS#8 / SPKI) para firmar y verificar JWT.
# Uso: scripts/gen-jwt-keys.sh [directorio] [--force]   (default: .secrets/jwt)
set -euo pipefail

dir=".secrets/jwt"
force=0
for arg in "$@"; do
  case "$arg" in
    --force) force=1 ;;
    *) dir="$arg" ;;
  esac
done

priv="$dir/privateKey.pem"
pub="$dir/publicKey.pem"
mkdir -p "$dir"
if [[ $force -eq 0 && ( -e "$priv" || -e "$pub" ) ]]; then
  echo "Ya existen claves en $dir; usa --force para sobrescribir." >&2
  exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$priv"
openssl pkey -in "$priv" -pubout -out "$pub"
chmod 600 "$priv"
echo "Claves generadas en $dir"
