#!/usr/bin/env bash
# Exercises check-document-contract.sh against fake Gradle output in the
# formats printed by current Gradle (quoted project paths and a
# "Project locations" section that must not count as a dependency).
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/check-document-contract.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

projects_report() {
  cat <<'EOF'
Projects:
Root project 'propuestas-api'
+--- Project ':core'
+--- Project ':documentos'
\--- Project ':documentos-contract'
Project locations:
project ':core' - /core
project ':documentos' - /documentos
project ':documentos-contract' - /documentos-contract
EOF
}

run_case() { # name, expected exit (0|1), dependency tree lines, expected stderr
  local name="$1" expected="$2" deps="$3" message="${4:-}" dir="$work/$1" rc=0
  mkdir -p "$dir/documentos-contract/src/main/java/ec/uce"
  printf 'package ec.uce; public record Dto(java.util.UUID publicId) {}\n' \
    >"$dir/documentos-contract/src/main/java/ec/uce/Dto.java"
  { printf '#!/usr/bin/env bash\ncat <<'"'"'EOF'"'"'\n'; projects_report
    printf 'runtimeClasspath\n%s\nEOF\n' "$deps"; } >"$dir/gradlew"
  chmod +x "$dir/gradlew"
  (cd "$dir" && bash "$script" >/dev/null 2>"$dir/stderr") || rc=$?
  if [[ "$rc" -ne "$expected" ]] || { [[ -n "$message" ]] && ! grep -Fq -- "$message" "$dir/stderr"; }; then
    printf 'FAIL %s: expected exit %s with "%s", got %s: %s\n' \
      "$name" "$expected" "$message" "$rc" "$(cat "$dir/stderr")" >&2
    return 1
  fi
  printf 'ok %s\n' "$name"
}

fails=0
run_case valid-quoted 0 "+--- project ':documentos-contract'" || fails=1
run_case valid-unquoted 0 "+--- project :documentos-contract" || fails=1
run_case depends-on-core 1 "+--- project ':documentos-contract'
\\--- project ':core'" 'never depend on Core' || fails=1
run_case transitive-core 1 "+--- project ':documentos-contract'
|    \\--- project ':core'" 'never depend on Core' || fails=1
run_case missing-contract-dep 1 "\\--- io.quarkus:quarkus-rest:3.40.1" 'versioned contract module' || fails=1
exit "$fails"
