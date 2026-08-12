# 012 — Formatter (Spotless + Palantir Java Format) y lint basico (`-Xlint:all`)

## Context

El repo no tiene formatter ni linter: `build.gradle.kts` solo compila con
`-parameters`, sin garantizar estilo consistente ni revisión estática
temprana. Con 147 archivos Java y varios módulos (`usuario/`, `proyecto/`,
`insumo/`, `apu/`, `motor/`, `common/`), la inconsistencia de estilo crece
con cada slice vertical.

Se adopta:

- **Formatter: Spotless** (`com.diffplug.spotless`) + **palantir-java-format**
  (formato no-discutible, el mismo estilo que usa Palantir/Red Hat). Corre en
  `./gradlew spotlessApply` (formatea) y `./gradlew spotlessCheck` (verifica,
  ideal para CI).
- **Linter: `-Xlint:all`** en `JavaCompile` — revisión estática del propio
  javac, cero dependencias nuevas. Respeta la regla del repo de no agregar
  dependencias sin plan; Error Prone quedaría como mejora futura con su propio
  plan.

Decisiones de version:

- **Spotless 8.9.0** — soporta Gradle 9.5 y JDK 25 (daemon JVM). No se usa
  configuration cache en este build (`gradle.properties` solo tiene
  `org.gradle.caching` + `org.gradle.parallel`), por lo que no aplica la
  flakiness conocida de `FeatureClassLoader` (zip file closed) que ocurre
  con config-cache + parallel en Gradle 9.x.
- **palantir-java-format sin versión explícita** (`palantirJavaFormat()`) —
  Spotless selecciona la versión por defecto según el JVM que corre Gradle.
  El daemon corre en JDK 25 (igual que el toolchain), así que usa la versión
  reciente (≥ 2.57, que soporta JEP-441 switch patterns). Al no fijar versión,
  las actualizaciones no exigen editar el build.

## In-scope

- `gradle/libs.versions.toml` — version del plugin Spotless.
- `build.gradle.kts` — aplicar plugin, config `spotless { java { palantirJavaFormat() } }`,
  acoplar `spotlessCheck` a `check`, y `-Xlint:all` en `JavaCompile`.
- `plans/README.md` — registro de este plan + nota post-ejecución.
- `docs/012-format-lint.md` — este documento.

## Out-of-scope

- Error Prone / Checkstyle / PMD (linters de terceros → otro plan).
- Formateo de código no-Java (YAML, SQL, JSON). Se puede añadir
  `spotless { format(...) }` después si se desea; este plan solo Java.
- `editorconfig` (recomendable, pero no bloquea; ver post-ejecución).
- NO `-Werror`: el objetivo es ver warnings, no romper el build por un
  warning cosmético preexistente.

## Steps

### 1. `gradle/libs.versions.toml`

Agregar en `[versions]` y en `[plugins]`:

```toml
[versions]
spotless = "8.9.0"

[plugins]
quarkus = { id = "io.quarkus", version.ref = "quarkus" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

### 2. `build.gradle.kts`

```kotlin
plugins {
    java
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}
```

Al final del archivo:

```kotlin
spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.withType<Check>().configureEach {
    dependsOn("spotlessCheck")
}
```

Nota: se formatea **main y tests** (sin `targetExclude`); mantiene todo el
árbol consistente. palantir-java-format tiene problemas históricos con
ciertos constructos; si un archivo no es soportable por el formatter, la
salida es **reportarlo y parar** (escape hatch abajo), no excluir en
silencio. Si hubiera impacto, evaluar `googleJavaFormat()` como alternativa
documentada en post-ejecución.

En el bloque `JavaCompile` existente, agregar:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:all")
}
```

### 3. Formatear

```bash
./gradlew spotlessApply
```

Revisar el diff (`git diff --stat`): el cambio debe ser **solo** formato
(indentación, imports, wrapping), nunca semántica. Si Spotless mueve código
que cambia el significado → STOP y reportar.

### 4. Verificar

```bash
./gradlew spotlessCheck      # debe pasar sin ediciones pendientes
./gradlew build -x test      # sanity build
./gradlew test               # suite completa, sin regresión
```

## Done criteria (commands with expected output)

```bash
./gradlew spotlessCheck
# BUILD SUCCESSFUL (sin lints ni diffs pendientes)

./gradlew build -x test
# BUILD SUCCESSFUL

./gradlew build -Xlint:all 2>&1 | grep -c warning   # si se cronometra el linter
./gradlew test
# BUILD SUCCESSFUL — 77 tests, 2 red (GM-19/GM-20 preexistentes), 2 skipped
```

## Post-execution notes

- **Aplicado 2026-08-11.** Spotless 8.9.0 + `palantirJavaFormat()` sin versión
  explícita (daemon JDK 25 → palantir-java-format reciente). `spotlessApply`
  formateó **142 archivos Java** (main + tests): +1465/−1196 líneas, todo
  rewrapping/imports/blank-lines. Revisión por `git diff -w` y suite:
  sin cambios semánticos.
- **Motor SIN exclusión (decisión del autor 2026-08-11):** se mantiene
  `motor/` formateado aunque CLAUDE.md limita editar `Motor.java`/
  `Consolidador.java`. Cambios son whitespace-only (verificado: `MotorApuTest`
  + properties siguen verdes; GM-19/GM-20 siguen red por el tema de rounding,
  no por formato). Registrar en el diff que se revisará el director: solo
  whitespace.
- **`-Xlint:all`:** 0 warnings en `clean compileJava` (main). No rompe build
  (sin `-Werror`).
- **Gradle `tasks.withType<Check>()`:** no compila en Kotlin DSL (Gradle 9);
  se usó `tasks.named("check") { dependsOn("spotlessCheck") }`.
- **Verificación final:** `./gradlew spotlessCheck` OK, `./gradlew build -x test`
  OK, `./gradlew test` → 77 tests, 2 red (GM-19/GM-20 preexistentes), 2 skipped.

## Escape hatches

- Si `spotlessApply` modifica archivos de forma que cambie semántica o falle
  un test existente → revertir (git) y reportar en post-execution notes; no
  ajustar tests a ciegas.
- Si `-Xlint:all` produce un warning que revele un bug real (p. ej. uso de
  `float/double` en `motor/`) → reportar, no silenciar.
- Si palantir-java-format no soporta algún constructo del código →
  reportar; alternativa documentada: `formatJavadoc(false)` primero, y si
  persiste, `googleJavaFormat()` (mismo trato, formato único).