#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -f ".env" ]]; then
  echo "[startup] .env encontrado"
else
  echo "[startup] Aviso: .env não encontrado; usando apenas variáveis do ambiente"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILES_ACTIVE"

