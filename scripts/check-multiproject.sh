#!/usr/bin/env bash
# Public Gradle project contract for the two Quarkus applications.
set -euo pipefail
cd "$(dirname "$0")/.."
projects="$(./gradlew --no-daemon -q projects)"
for app in core documentos; do
  if [[ "$projects" != *"Project ':$app'"* ]]; then
    printf 'Missing Gradle application :%s\n' "$app" >&2
    exit 1
  fi
done
# The dependency graph, not just source layout, must exclude database code.
classpath="$(./gradlew --no-daemon -q :documentos:dependencies --configuration runtimeClasspath)"
if grep -Eq 'io\.quarkus:quarkus-(hibernate-orm(-panache)?|flyway|jdbc-postgresql|agroal)(:|[[:space:]])|org\.postgresql:postgresql:' <<<"$classpath"; then
  printf 'Documentos runtime must not include PostgreSQL, ORM or Flyway\n' >&2
  exit 1
fi
printf 'Gradle applications :core and :documentos are available without a Documentos database\n'
