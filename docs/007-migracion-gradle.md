# 007 — Migración Maven → Gradle (Kotlin DSL)

- **Status:** DONE (2026-08-01; verificaciones abajo en "Done criteria"; CI/CD diferido por decisión del autor)
- **Gradle wrapper:** 9.5.1 (`distributionUrl=gradle-9.5.1-bin.zip` — ya en caché en `~/.gradle/wrapper/dists`)
- **Quarkus:** 3.37.4 — el plugin `io.quarkus` 3.37.4 coincide con el BOM `quarkus-bom:3.37.4`
- **Depends on:** estado actual del build (planes 001, 003, 004, 005/006)
- **Blocks:** 008+ (todo plan futuro usará `./gradlew`)
- **Decisions del autor (2026-08-01):** (1) Kotlin DSL, (2) eliminar archivos Maven en el mismo cambio, (3) **CI/CD diferido** — los workflows de GitHub Actions se dejan para una iteración posterior (no se toca en este plan), (4) wrapper 9.5.1.

---

## Context

El repo se construye con Maven (`pom.xml`, `mvnw`, `.mvn/`). El objetivo es
pasar a Gradle 9.5.1 con **Kotlin DSL**, usando el sistema de dependencias de
Gradle (wrapper + plugin `io.quarkus` + BOM vía `enforcedPlatform`), y borrar
todo el scaffolding Maven en el mismo cambio.

Por qué funciona esta combinación (verificado contra la documentación):

- **Gradle 9.5.1 corre sobre JDK 26** (runtime); Gradle ≥9.4.0 soporta JDK 26.
  El `JAVA_HOME` local es `openjdk-26`. Sin tocar Maven, el proyecto ya
  compilaba con `maven.compiler.release=21`; Gradle lo replica con
  `options.release.set(21)`.
- **El plugin `io.quarkus` 3.37.4 soporta Gradle 9.x** (Quarkus ≥3.28.1
  certificó Gradle 8.14 y 9.1.0; 3.37.4, de 2026-07-22, sigue la línea 9.x).
- `@QuarkusTest` corre en la tarea `test` de Gradle sin configuración extra.
- El dev mode es `./gradlew --console=plain quarkusDev` (la doc recomienda
  `--console=plain` porque el daemon interfiere con la consola de Quarkus).
- El native es `./gradlew build -Dquarkus.native.enabled=true`; el artefacto
  sale en `build/` (antes `target/`).

## In scope (archivos tocados/creados)

| Archivo | Acción |
|---|---|
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `target/` | **Eliminar** (same change) |
| `settings.gradle.kts` | Nuevo — `rootProject.name = "propuestas-api"` |
| `gradle.properties` | Nuevo — coordenadas del BOM Quarkus |
| `build.gradle.kts` | Nuevo — plugins, repos, deps, tasks |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` | Nuevo (generado por `gradle wrapper`) |
| `.gitignore` | Sección `#Maven` → `# Gradle` (`.gradle/`, `build/`) |
| `src/main/docker/Dockerfile.jvm` | `target/` → `build/`; comentario `./mvnw package` → `./gradlew build` |
| `src/main/docker/Dockerfile.legacy-jar` | ídem (`./gradlew build -Dquarkus.package.jar.type=legacy-jar`) |
| `src/main/docker/Dockerfile.native` | `target/*-runner` → `build/*-runner`; comentario → `./gradlew build -Dquarkus.native.enabled=true` |
| `src/main/docker/Dockerfile.native-micro` | ídem |
| `.dockerignore` | `target/` → `build/` |
| `.github/workflows/ci.yml` + `native.yml` | **DIFERIDO** (decisión 2026-08-01): no se toca CI/CD en este plan; la deuda del plan 002 queda pendiente y se hará con `./gradlew` en una iteración posterior |
| `README.md`, `CLAUDE.md`, `plans/README.md` | Comandos y referencias a `./gradlew` |

## Out of scope — no tocar

- `src/main/java/**`, `src/test/java/**`, `src/main/resources/**` — sin cambios
  de código. El build debe dar exactamente los mismos resultados.
- `application.yml` — build-tool-agnóstico, no se toca.
- Los GM-19/GM-20 rojos (problema de dominio, escalado al director) — **no se
  tocan tolerancias ni `Motor.java`/`Consolidador.java`**.
- Frontend, despliegue, schema DB.

## Repo conventions a respetar

- Vocabulario de dominio en español; sin traducciones inventadas.
- Regla de no-deps: ahora aplica a `build.gradle.kts` (reemplaza a `pom.xml`):
  no añadir dependencias sin un plan que lo autorice.
- Los planes del repo son playbooks auto-contenidos; este documento lo es.

## Mapeo pom.xml → build.gradle.kts (traceability)

| Maven (`pom.xml`) | Gradle (`build.gradle.kts`) |
|---|---|
| `<maven.compiler.release>21</...>` | `options.release.set(21)` en `JavaCompile` |
| `<parameters>true</parameters>` (compiler plugin) | `options.compilerArgs.add("-parameters")` |
| `dependencyManagement` import `quarkus-bom:3.37.4` | `implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))` |
| `<scope>compile</scope>` (default) | `implementation("...")` |
| `<scope>test</scope>` | `testImplementation("...")` |
| `quarkus-maven-plugin` goals `build`/`generate-code`/`generate-code-tests`/`native-image-agent` | lo aporta el plugin `io.quarkus`; dev mode = `quarkusDev`; native = `-Dquarkus.native.enabled=true` |
| Surefire `<includes>**/*Test.java + **/*IT.java</includes>` | `tasks.withType<Test> { include("**/*Test.class"); include("**/*IT.class") }` — **Gradle NO incluye `*IT` por defecto y sus includes matchean archivos `.class`, no `.java`** (un `**/*Test.java` deja la tarea en `NO-SOURCE`) |
| Surefire `java.util.logging.manager` | `systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")` en `Test` |
| Failsafe (`*IT` en `integration-test`/`verify`) | innecesario (no hay `@QuarkusIntegrationTest`; `AuthResourceIT` es `@QuarkusTest`) |
| Profile `native` (`skipITs=false`, `quarkus.native.enabled=true`) | flag CLI `-Dquarkus.native.enabled=true` (sin bloque de perfil) |
| `project.build.sourceEncoding=UTF-8` | `options.encoding = "UTF-8"` |
| `target/quarkus-app/` (fast-jar) | `build/quarkus-app/` |
| `target/*-runner` (native) | `build/*-runner` |

Dependencias 1:1 (20 total; 14 extensiones sin versión = gobernadas por BOM,
3 con versión explícita):

- `implementation`: `quarkus-hibernate-orm-panache`, `quarkus-rest`,
  `quarkus-rest-jackson`, `quarkus-smallrye-health`, `quarkus-smallrye-jwt-build`,
  `quarkus-hibernate-validator`, `quarkus-smallrye-jwt`, `quarkus-security-jpa`,
  `quarkus-flyway`, `quarkus-smallrye-openapi`, `quarkus-jdbc-postgresql`,
  `quarkus-config-yaml`, `quarkus-arc`, `quarkus-elytron-security-common`,
  `org.apache.poi:poi-ooxml:5.3.0`, `com.github.librepdf:openpdf:2.0.3`
- `testImplementation`: `quarkus-junit5`, `io.rest-assured:rest-assured`,
  `com.fasterxml.jackson.core:jackson-databind`, `net.jqwik:jqwik:1.9.0`

## Pasos

### 1 — Línea base (antes de borrar Maven)

```bash
./mvnw -q -DskipTests package
```

Esperado: `BUILD SUCCESS`. Si falla, STOP — no migrar sobre un build roto.

### 2 — Escribir los archivos Gradle

**`settings.gradle.kts`** (verbatim):

```kotlin
pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}
rootProject.name = "propuestas-api"
```

**`gradle.properties`** (verbatim):

```properties
# Quarkus plugin + platform BOM — replican las <properties> del antiguo pom.xml
quarkusPluginId=io.quarkus
quarkusPluginVersion=3.37.4
quarkusPlatformGroupId=io.quarkus.platform
quarkusPlatformArtifactId=quarkus-bom
quarkusPlatformVersion=3.37.4
```

**`build.gradle.kts`** (verbatim):

```kotlin
plugins {
    id("java")
    id("io.quarkus")            // versión vía settings.gradle.kts pluginManagement ← gradle.properties
}

group = "ec.uce.propuestas"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-security-jpa")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-elytron-security-common")

    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("com.github.librepdf:openpdf:2.0.3")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("net.jqwik:jqwik:1.9.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)                  // era maven.compiler.release=21
    options.compilerArgs.add("-parameters")  // era <parameters>true</parameters>
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()                       // @QuarkusTest + jqwik (JUnit Platform)
    include("**/*Test.class")                // surefire: **/*Test.java (los includes de Gradle matchean .class)
    include("**/*IT.class")                  // surefire: **/*IT.java (AuthResourceIT)
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
```

### 3 — Bootstrap del wrapper (usa la distribución ya en caché; no instala nada)

```bash
GRADLE_BIN="$HOME/.gradle/wrapper/dists/gradle-9.5.1-bin/iq79hdu3mqx29lgffhp8bfmx/gradle-9.5.1/bin/gradle"
"$GRADLE_BIN" wrapper --gradle-version 9.5.1 --distribution-type bin
chmod +x gradlew
```

Genera `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` y
`gradle/wrapper/gradle-wrapper.properties`. Como `gradle-9.5.1-bin` ya está en
`~/.gradle/wrapper/dists`, el primer `./gradlew` no redescarga nada.

### 4 — Eliminar Maven

```bash
git rm pom.xml mvnw mvnw.cmd
git rm -r .mvn
rm -rf target
```

### 5 — `.gitignore`, `.dockerignore`, Dockerfiles

`.gitignore`: reemplazar la sección `#Maven` por:

```gitignore
# Gradle
.gradle/
build/
```

`.dockerignore`:

```dockerignore
*
!build/*-runner
!build/*-runner.jar
!build/lib/*
!build/quarkus-app/*
```

`Dockerfile.jvm`: comentario `# ./mvnw package` → `# ./gradlew build`; y
`COPY --chown=185 target/quarkus-app/...` → `build/quarkus-app/...`
(4 líneas).

`Dockerfile.legacy-jar`: `# ./mvnw package -Dquarkus.package.jar.type=legacy-jar`
→ `# ./gradlew build -Dquarkus.package.jar.type=legacy-jar`;
`COPY target/lib/*` → `COPY build/lib/*`; `COPY target/*-runner.jar` →
`COPY build/*-runner.jar`.

`Dockerfile.native` y `Dockerfile.native-micro`: comentario →
`# ./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true`;
`COPY ... target/*-runner ...` → `build/*-runner`.

### 6 — CI/CD (DIFERIDO — decisión 2026-08-01)

**No se toca en este plan.** Los workflows de GitHub Actions de la deuda del
plan 002 (`.github/workflows/ci.yml` + `native.yml`) se escribirán en una
iteración posterior. Cuando se hagan, usarán `./gradlew` (no Maven) y los
artefactos/reportes de Gradle (`build/test-results/`, `build/*-runner`).
**Nota para esa iteración:** `.gitignore` contiene una línea `.github/` que
ignora silenciosamente el directorio; si se retira esa línea, verificar que
los workflows sí quedan visibles para git (`git check-ignore`).

### 7 — Documentación

- `README.md`: stack `Java 21 · Gradle · Quarkus 3.37.4`; bloque "Running
  locally" con `./gradlew`; layout nota `build/`; link "How do I run tests" →
  `./gradlew test`.
- `CLAUDE.md`: bloque "Verification commands" → equivalentes Gradle
  (`./gradlew build -x test`, `./gradlew test`,
  `./gradlew test --tests 'ec.uce.propuestas.motor.*'`,
  `./gradlew test --tests 'ec.uce.propuestas.usuario.*'`,
  `./gradlew --console=plain quarkusDev`,
  `./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true`);
  regla "Do not add dependencies to `pom.xml`" → `build.gradle.kts`;
  trap `mvnw` sin bit de ejecución → `gradlew`; nuevo trap: salida en `build/`
  no en `target/`.
- `plans/README.md`: fila 007 + nota post-ejecución (link a
  `../docs/007-migracion-gradle.md`).

## Done criteria (todos verificados 2026-08-01)

1. `./gradlew --version` → Gradle 9.5.1. **✓**
2. `./gradlew build -x test` → `BUILD SUCCESSFUL` y existe
   `build/quarkus-app/quarkus-run.jar`. **✓**
3. `./gradlew test` → mismo resultado que Maven: **56 tests, 2 red (GM-19,
   GM-20), 2 skipped (GM-24, DIAG)**. Fallos = problema de dominio escalado,
   NO regresión de la migración. **✓** (desglose: MotorApuTest 21/0/0,
   MotorConsolidacionTest 5/2/2, MotorPropiedadesTest 5/0/0,
   AuthResourceIT 19/0/0, PasswordPolicyTest 6/0/0)
4. `./gradlew --console=plain quarkusDev` arranca y responde en `:8080`
   (levantar `docker compose up -d` si el Postgres local no está). **✓**
   health UP con DB UP (Flyway validó 3 migraciones), OpenAPI HTTP 200.
5. `git status` → no quedan `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`. **✓**
6. (Opcional, lento) `./gradlew build -Dquarkus.native.enabled=true
   -Dquarkus.native.container-build=true` → `build/*-runner` existe.
   **NO ejecutado** (build nativo de ~10 min; se deja para la iteración CI).

## Escape hatches

- **Ruta de Gradle en caché distinta** → `find "$HOME/.gradle/wrapper/dists" -maxdepth 2 -type d -name gradle-9.5.1`.
- **Quarkus falla sobre el JDK del launcher** (errores de codegen/build
  workers) → el `toolchain` en `build.gradle.kts` ya fija el JDK de build; el
  resolutor foojay (en `settings.gradle.kts`) descarga uno si falta:
  ```kotlin
  plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
  // en build.gradle.kts:
  java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
  ```
- **`quarkusDev` no conecta a Postgres** → `docker compose up -d` (expone
  `5436`; `.env` ya apunta a `DB_URL=...:5436/propuestas`). Credenciales del
  contenedor: `DB_USER=postgres DB_PASSWORD=postgres`.
- **GM-19/GM-20 rojos bajo Gradle** = esperado (mismo resultado que Maven).
  NO añadir tolerancia, NO editar `Motor.java`/`Consolidador.java`.
- **`./gradlew test` no descubre `AuthResourceIT`** → verificar el bloque
  `include("**/*IT.class")`. Detalle clave: los `include` de la tarea `Test`
  matchean nombres de **clase compilada** (`.class`), NO archivos `.java`;
  usar `**/*Test.java` deja la tarea en `NO-SOURCE`.

---

## 008 — Refinamientos modernos (2026-08-01)

Aplicados tras comparar con el proyecto de la clase de distribuida
(`app-authors/build.gradle.kts`). **Decisión del autor:** los planes y este
doc son una base, no un muro; se aplica lo moderno que aporta valor real.

### Qué se aplicó

1. **Version catalog** (`gradle/libs.versions.toml`) — fuente única de versiones
   (Quarkus 3.37.4, poi 5.3.0, openpdf 2.0.3, jqwik 1.9.0). El plugin Quarkus se
   declara con `alias(libs.plugins.quarkus)` en `build.gradle.kts`.
2. **Toolchain JDK 25** (`java { toolchain { languageVersion = 25 } }`) — el build
   corre siempre sobre JDK 25 (detectado local: sdkman `25.0.3-tem`; auto-descarga
   vía foojay si falta), en vez de `options.release`. `quarkusDev` corre sobre JDK 26
   del launcher y no se ve afectado. **Cambio de target: Java 21 → 25** (decisión
   del autor).
3. **`gradle.properties`** simplificado: se eliminaron `quarkusPluginId/Version`,
   `quarkusPlatform*` (ahora en el catalog). Verificado: el plugin Quarkus resuelve
   el BOM desde `enforcedPlatform(libs.quarkus.platform.bom)` sin esas props.
   Se añadió `org.gradle.caching=true` + `org.gradle.parallel=true` (build cache).

### Qué NO se aplicó y por qué

4. **Lombok / `@Builder` — RECHAZADO.** Criterio del autor: "solo si aporta valor
   real o reduce código". Evidencia en este repo:
   - Los DTO de entrada (RegistroRequest, LoginRequest, …) **nunca** se construyen a
     mano — Quarkus los deserializa de JSON. Builder = cero valor.
   - Solo **14 sitios** `new <Record>(...)` en todo `src/main/java` (ApuCalculado 1,
     FilaCalculada 5, TokenResponse 2, PerfilResponse 2, …). No hay listas posicionales
     repetidas que un builder alivie.
   - Los records ya dan construcción inmutable + type-safe; 15 de ellos tienen
     compact-constructor con validación (los `Objects.requireNonNull` del motor).
   - `motor/` es **Java puro, sin framework, JDK only** (requisito de tesis, GM
     acceptance). Añadir Lombok (o record-builder, un annotation processor) mete una
     dependencia + código generado en el código de mayor riesgo del proyecto.
   Conclusión: los records YA aportan el valor que un builder daría; añadirlo
   aumentaría código y riesgo. No se agrega.
5. **Consul / Stork / service-discovery — RECHAZADO.** El proyecto de clase es un
   monorepo multi-módulo de microservicios (necesita discovery); la tesis es un
   monólito de un solo servicio. `thesis-docs` no lo contempla. Ver nota del 008 en
   `plans/README.md`.
6. **Micrometer / OpenTelemetry / Kubernetes / Jib / ModelMapper — RECHAZADO.** No
   están en el alcance de `thesis-docs` (solo health check vía
   `quarkus-smallrye-health`, ya presente). Observabilidad/despliegue se decide en
   una iteración futura vía plan, no ahora.

### Verificación (post-refinamiento, 2026-08-01)

1. `./gradlew build` → BUILD SUCCESSFUL, fast-jar en `build/quarkus-app/quarkus-run.jar`.
2. `./gradlew test` → **56 tests, 2 red (GM-19, GM-20 — tema de dominio conocido),
   2 skipped (GM-24, DIAG)** — idéntico al baseline Maven y a la migración 007.
3. `./gradlew --console=plain quarkusDev` (JDK 26 del launcher) → arranca y
   escucha en `:8080`, "Installed features" completa. Credenciales de compose:
   `DB_USER=postgres DB_PASSWORD=postgres` (el `docker inspect` del contenedor).
4. `./gradlew -q javaToolchains` → JDK 25 detectado (sdkman `25.0.3-tem`,
   graalvm `25+37`) y JDK 21 auto-provisionado queda en caché de Gradle.
