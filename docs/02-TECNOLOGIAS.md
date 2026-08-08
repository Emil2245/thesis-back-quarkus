# Tecnologías del proyecto — `thesis-back-quarkus`

- **Fuente verificada:** `gradle/libs.versions.toml`, `build.gradle.kts`,
  `application.yml`, `docker-compose.yml`, y `../thesis-docs/plan/backend/01-quarkus-backend.md`.
- **Fecha:** 2026-08-02.

---

## 1. Stack principal (runtime)

| Tecnología | Versión | Rol |
|---|---|---|
| **Java** | 25 (toolchain Gradle; target de compilación) | Lenguaje. Origen: Java 21 en docs, migrado a 25 por decisión del autor (008). |
| **Gradle** | 9.5.1 (Kotlin DSL, wrapper) | Build. Sustituyó a Maven en 007. |
| **Quarkus** | 3.37.4 (BOM + plugin) | Framework cloud-native (JAX-RS/REST, CDI, config). |
| **PostgreSQL** | 16 (docs) / imagen compose `postgres:18-alpine` | Base de datos. |
| **Hibernate ORM + Panache** | Quarkus BOM | Persistencia (entidades `PanacheEntityBase` + `@Id IDENTITY`). |
| **Flyway** | Quarkus BOM | Migraciones versionadas V001…V003 (21 tablas + seeds). |
| **SmallRye JWT** | Quarkus BOM | Emisión/verificación JWT (claves PEM, TTL 60 min — D-02). |
| **Elytron** (`quarkus-elytron-security-common`) | Quarkus BOM | bcrypt para contraseñas. |
| **SmallRye OpenAPI + Swagger UI** | Quarkus BOM | Contrato REST en `/q/openapi` y `/q/swagger-ui/`. |
| **SmallRye Health** | Quarkus BOM | `/q/health` (usado por el plan de despliegue). |
| **Hibernate Validator** | Quarkus BOM | Bean Validation en el borde REST. |
| **RESTEasy/Quarkus REST + Jackson** | Quarkus BOM | Endpoints JAX-RS y serialización JSON (DTOs `record`). |

## 2. Librerías de negocio (futuro módulo `documento`, ya declaradas)

| Librería | Versión | Uso |
|---|---|---|
| **Apache POI (poi-ooxml)** | 5.3.0 | Generación de `.xlsx` SERCOP (estilos, celdas combinadas, fórmulas). |
| **OpenPDF** | 2.0.3 | Generación de `.pdf` (tablas; construido desde los mismos datos, no convirtiendo xlsx). |

> ⚠️ Riesgo conocido para native-image: POI/OpenPDF usan reflexión/fonts — se
> valida temprano (I-01/I-02 según docs) o se ejecuta el módulo en JVM.

## 3. Testing

| Tecnología | Versión | Uso |
|---|---|---|
| **JUnit 5** (`quarkus-junit5`) | BOM | Suite completa (56 tests). |
| **REST-assured** | (BOM) | Tests de integración `@QuarkusTest` (`AuthResourceIT`). |
| **Dev Services** | Quarkus | Postgres de test auto-levantado (Testcontainers) — sin setup manual. |
| **jqwik** | 1.9.0 | Property-based tests del motor (`MotorPropiedadesTest`). |
| **Jackson databind** | (BOM) | Fixtures JSON de golden masters. |

Golden masters GM-01…25: fixtures JSON/CSV en `src/test/resources/motor/fixtures/`
copiados de `thesis-docs/plan/domain/_artifacts/`.

## 4. Herramientas de desarrollo / operación

| Herramienta | Uso |
|---|---|
| **Docker Compose** | Postgres local (`:5436`, credenciales postgres/postgres). |
| **Bruno + IntelliJ `.http`** (`api/`) | Colecciones manuales de auth/salud. |
| **Swagger UI** | Docs interactivos de la API en dev. |
| **GraalVM/Mandrel (native)** | Build nativo `./gradlew build -Dquarkus.native.enabled=true` (imagen <120 MB, boot ~50 ms). |

## 5. Infraestructura / despliegue (planeada, aún no ejecutada)

| Componente | Elección | Notas |
|---|---|---|
| CI/CD | **GitHub Actions** (JVM + native) | **Pendiente** — `.github/` no existe; se reescribirá con `./gradlew`. |
| Backend prod | Contenedor Quarkus **native** | Render / Koyeb / Cloud Run (free-tier-friendly). |
| BD prod | **Neon** (Postgres) | $0/mo con una sola BD. |
| Frontend | **Cloudflare Pages** (React/Vite, repo aparte) | Origen CORS permitido: `localhost:5173/3000`. |
| Orquestación | **Ninguna** | Kubernetes: no. Traefik/Consul descartados (ver `analisis-microservicios-auth-core.md`). |

## 6. Configuración de claves y secretos

- Claves JWT dev: `src/main/resources/META-INF/resources/{publicKey,privateKey}.pem`.
  **Nunca commitear claves de prod.** Rotar antes de desplegar.
- Variables de entorno documentadas en `.env.example`: `DB_URL`, `DB_USER`,
  `DB_PASSWORD`, `MP_JWT_VERIFY_PUBLICKEY_LOCATION`, `SMALLRYE_JWT_SIGN_KEY_LOCATION`.

## 7. Cosas que el equipo decidió NO usar (con justificación)

| Tecnología | Motivo |
|---|---|
| **Lombok / record-builder** | DTOs se deserializan de JSON (nunca se construyen a mano); records ya cubren el valor; motor es JDK-only por requisito de tesis. |
| **Consul / Stork / service discovery** | Monolito de un servicio; no contemplado en `thesis-docs`. |
| **Micrometer / OpenTelemetry / K8s / Jib** | Fuera de alcance; solo health check (`smallrye-health`) necesario. |
| **ModelMapper** | Mapeo trivial (records/DTOs). |
| **`quarkus-security-jpa`** | Presente pero sin uso (auth es JWT-only) — dead weight candidato a limpieza. |

> Regla del repo: **no añadir dependencias a `build.gradle.kts` sin un plan que
> lo autorice.**
