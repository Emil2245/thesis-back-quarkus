#!/usr/bin/env bash
set -euo pipefail

# One Gradle configuration pass avoids paying startup twice in CI.
model="$(./gradlew --no-daemon -q projects :documentos:dependencies --configuration runtimeClasspath)"
if ! grep -Fq "Project ':documentos-contract'" <<<"$model"; then
  printf 'Missing pure Gradle contracts module :documentos-contract\n' >&2
  exit 1
fi
# Dependency-tree edges only ("+--- project ':x'" or "\--- project :x"); the
# "Project locations" section also prints "project ':x' - /x" and must not count.
depends_on() { grep -Eq -- "--- project '?:$1'?( |\$)" <<<"$model"; }
if ! depends_on documentos-contract; then
  printf 'Documentos must use the versioned contract module\n' >&2
  exit 1
fi
if depends_on core; then
  printf 'Documentos must never depend on Core\n' >&2
  exit 1
fi
source_dir='documentos-contract/src/main/java'
if [[ ! -d "$source_dir" ]] || grep -ERq 'jakarta\.persistence\.|io\.quarkus\.hibernate\.|ec\.uce\.propuestas\.(motor|presupuesto\.entity|apu\.entity|.*\.repository)\.|@Entity|extends PanacheEntity' "$source_dir"; then
  printf 'Document contracts must not reference business or persistence types\n' >&2
  exit 1
fi
if grep -ERq '(^|[^[:alnum:]_])(Long|long)[[:space:]]+[[:alnum:]_]*[Ii]d[[:space:],);]' "$source_dir"; then
  printf 'Document contracts must not carry internal numeric IDs\n' >&2
  exit 1
fi
printf 'Documentos versioned contracts have no Core/persistence dependency\n'
