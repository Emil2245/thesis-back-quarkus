# Arquitectura — organización del código y plan de módulos

- **Fuente canónica:** `../thesis-docs/plan/architecture/08-codebase-design.md`
  (módulos, costuras, ADR 8–10) y `../thesis-docs/plan/backend/01-quarkus-backend.md`.
  Este repo **implementa** esas decisiones; no las redefine.
- **Fecha:** 2026-08-02.

---

## 1. Veredicto corto

**La organización es buena y deliberada** — no es un repo "sin orden". Sigue una
arquitectura de **módulos profundos por feature (vertical slices)** con costuras
explícitas. El único "hueco" no es de diseño sino de **código aún no escrito**:
los módulos de negocio (proyecto, insumo, apu, presupuesto, cronograma,
documento) están planeados en el doc canónico pero no existen todavía.

Regla rectora heredada: **una capa existe solo si es profunda** (esconde
comportamiento real tras una interfaz pequeña). El CRUD plano va directo
`Resource → Panache`, sin Service/Repository intermedios.

---

## 2. Estilo de arquitectura

- **Monolito modular** (no microservicios). Cloud-native ≠ microservicios; el
  análisis completo de una eventual separación está en
  `docs/analisis-microservicios-auth-core.md` → **recomendación: no separar**
  (no compra ninguna variable de tesis; el auth ya es un módulo autocontenido y
  extraíble si algún día se necesita).
- **Vertical slices** por dominio de negocio (`usuario/`, `motor/`, futuros
  `insumo/`, `apu/`, `presupuesto/`, `cronograma/`, `documento/`).
- **Módulos profundos** (interfaz pequeña, mucha conducta escondida):
  `motor` ⭐, `recalculo`, `documento`, `versionado`, `importacion`,
  `invitacion-tokens`, y el puerto `correo`.
- **Un solo puerto real con el exterior:** `EnviadorCorreo` (adaptadores: Brevo
  en prod, grabadora en tests). Postgres es *local-sustituible* → Panache
  directo, sin repositorios intermedios (ADR 10).
- **Motor de cálculo:** Java puro, sin framework, `BigDecimal` a 6 dp; el
  redondeo a 2 dp ocurre **solo en export**. Helper internos package-private.

---

## 3. Mapa de módulos planeado (backend)

```
REST (JAX-RS, /api/v1)
  ├── recalculo      «qué cambió» → derivados persistidos (write-through)
  ├── versionado     deep copy versión / proyecto
  ├── importacion    CSV validar / aplicar
  ├── documento      generar( tipo, formato ) → bytes  (POI xlsx / OpenPDF pdf)
  ├── invitacion-tokens   D-01/D-02/D-11 (verificación, reset, invitación, refresh)
  └── CRUD plano     Resource → Panache (sin capas)
         │
  recalculo ──► motor ⭐  (calcularApu / consolidar) ──► documento
  invitacion-tokens ─► correo (puerto: Brevo / grabadora)
  todo ─► Postgres (Flyway V001..V003)
```

### Interfaces clave del motor (ADR 8 — dos granos)

```java
ApuCalculado      calcularApu(ApuSnapshot, ParametrosCalculo)
VersionCalculada  consolidar(VersionSnapshot)
```

`consolidar` reutiliza `calcularApu` internamente. Golden masters GM-01…25
definen la aceptación (0.00 de tolerancia).

---

## 4. Estructura de carpetas actual vs. planeada

```
src/main/java/ec/uce/propuestas/
├── common/        ✅  infra compartida (RestApplication /api/v1, errores)
├── usuario/       ✅  identidad + auth (entidades, auth/, dto/, mail/)
├── motor/         ✅  motor puro + internal/ (CalculadorFila, Consolidador)
│   └── internal/  ✅  package-private, puro Java
├── proyecto/      ⬜  I-03 (firmantes, parámetros, ciclo de vida)
├── insumo/        ⬜  I-04 (CRUD + CSV + bases centrales)
├── apu/           ⬜  I-05/I-06 (editor + %CI + descuentos + auxiliares)
├── presupuesto/   ⬜  I-07 (capítulos, rubros, consolidación)
├── cronograma/    ⬜  I-08/I-09 (actividades, avance ponderado)
└── documento/     ⬜  I-10 (export .xlsx/.pdf)
```

Reglas de paquete que ya se cumplen y deben mantenerse (costuras futuras):
- Los recursos core futuros **no inyectan** nada de `usuario/` ni importan
  `Usuario` — lo que necesiten del usuario debe venir en el **claim del JWT**
  (hoy el token emite `upn=email` y `groups=rol`; recomendación del análisis:
  añadir claim numérico `usuarioId` — cuesta 2 líneas y desbloquea I-03).
- Los DTOs del contrato REST son `record`s Java, camelCase, con `@Valid` en el
  borde. Nunca entidades por el borde.
- Base REST `/api/v1` definida una vez en `common/RestApplication` (nunca
  hardcodear en cada `@Path`).
- Roles: solo `USUARIO` y `SUPER_ADMIN`; `@RolesAllowed` en recurso/método y
  `@PermitAll` explícito en lo público.

---

## 5. Dónde va cada parte del código (receta concreta)

> **Decisión 2026-08-02:** todo dominio nuevo usa `entity/ + repository/ +
> mapper/ + dto/ + service/ + resource/`. Los repositorios son obligatorios
> (1 por entidad) para que el Service no mezcle SQL y las consultas nuevas no
> exijan refactor.

Regla de oro: **la separación es POR DOMINIO (vertical), no por capas
horizontales**. No existen paquetes globales `controller/`, `service/`,
`repository/`, `model/`. El paquete `ec.uce.propuestas.<dominio>/` contiene todo
lo de ese dominio; dentro, la "capa" se indica con subpaquetes solo donde
aportan (`dto/`, `mail/`) y el resto convive en el mismo paquete con sufijos de
nombre.

El módulo `usuario/` es el patrón de referencia **ya implementado**:

```
ec/uce/propuestas/usuario/                     ← dominio "usuario"
├── Usuario.java            ENTIDAD Panache (PanacheEntityBase + @Id IDENTITY)
├── Rol.java                enum
├── RefreshToken.java       entidad
├── TokenUsuario.java       entidad
├── TipoToken.java          enum
├── UsuarioRepository.java  REPOSITORIO — SOLO si hay queries no triviales
└── auth/                   ← feature/casos de uso del dominio
    ├── AuthResource.java       REST (@Path("/auth"))
    ├── PerfilResource.java     REST (@Path("/perfil"))
    ├── AuthService.java        LÓGICA DE NEGOCIO (reglas D-01/D-03, @Transactional)
    ├── TokenService.java       LÓGICA (JWT, tokens de un solo uso)
    ├── PasswordService.java    LÓGICA (bcrypt)
    ├── PasswordPolicy.java     REGLA PURA (sin CDI)
    ├── dto/                    DTOs — records, camelCase, @Valid
    │   ├── LoginRequest.java   (entrada)
    │   ├── TokenResponse.java  (salida)
    │   └── …
    └── mail/                   PUERTO + ADAPTADOR (único puerto del sistema)
        ├── EnviadorCorreo.java        (interfaz/puerto)
        └── LogEnviadorCorreo.java     (adaptador dev)
```

### Tabla: pieza → dónde va

| Pieza | Dónde | Ejemplo real |
|---|---|---|
| **REST endpoint** | `ec.uce.propuestas.<dominio>/<feature>/*Resource.java` | `usuario/auth/AuthResource.java` |
| **DTO request/response** | `.../<feature>/dto/` — `record`, camelCase, `@Valid` | `usuario/auth/dto/LoginRequest.java` |
| **Lógica de negocio (módulo profundo)** | `.../<dominio>/<feature>/*Service.java` junto al Resource | `AuthService`, `TokenService` |
| **Reglas puras (sin CDI)** | mismo paquete, clase estática/inmutable | `PasswordPolicy` |
| **Entidad (BD)** | `.../<dominio>/entity/`, `PanacheEntityBase` | `proyecto/entity/Proyecto.java` |
| **Repositorio (BD)** | `.../<dominio>/repository/`, `PanacheRepositoryBase` — **siempre, 1 por entidad**; todas las queries viven aquí, el Service nunca escribe SQL/Panache | `proyecto/repository/ProyectoRepository.java` |
| **CRUD plano (sin lógica)** | `Resource → Service → Repository`, sin lógica extra en Service | `insumo/.../InsumoCrudService` |
| **Puerto (interfaz hacia afuera)** | `.../<feature>/<puerto>/` | `auth/mail/EnviadorCorreo` |
| **Adaptador del puerto** | junto al puerto | `auth/mail/LogEnviadorCorreo` |
| **Motor de cálculo** | `ec/uce/propuestas/motor/` puro + `internal/` package-private | `Motor`, `internal/Consolidador` |
| **Infra compartida** | `ec/uce/propuestas/common/` | `RestApplication` (`/api/v1`), `ErrorPayload`, `GlobalExceptionMapper` |
| **Migraciones BD** | `src/main/resources/db/migration/V{NNN}__*.sql` | `V001__baseline.sql` |
| **Config** | `src/main/resources/application.yml` (perfiles `%dev`/`%prod`) | — |
| **Tests** | espejo del árbol bajo `src/test/java/...` | `usuario/auth/AuthResourceIT.java` |
| **Fixtures de test** | `src/test/resources/...` | `motor/fixtures/*.json` |

### Reglas que deciden cuándo existe una capa

1. **Resource delgado:** valida con `@Valid` y delega al Service; nunca pone
   reglas de negocio ni consulta Panache si hay lógica real.
2. **Service separa capas:** contiene reglas y orquestación de transacciones,
   pero **nunca consultas SQL/Panache** — todo el acceso a datos pasa por el
   `Repository` de la entidad.
3. **Repository siempre (1 por entidad):** toda consulta específica vive como
   método del repo (p. ej. `findByBaseYcodigo`, `listarDePropietario`). Así,
   cuando una consulta nueva aparezca, no hay refactor de capas — solo se añade
   un método. Preferir la API repositorio de Panache (`repo.find(...)`) sobre
   los finders estáticos (`Entidad.find(...)`) en los Services.
4. **DTO siempre:** las entidades nunca cruzan el borde REST.
5. **Transacciones** (`@Transactional`) en el Service, nunca en el Resource.
6. **No hay "capa de conexión" propia:** es Panache/Hibernate vía Quarkus
   (Dev Services en tests, Flyway para el esquema). Postgres es
   *local-sustituible* → sin puerto (ADR 10).

> **Nota:** `usuario/` es el caso previo a esta regla (usa finders estáticos
> junto con `UsuarioRepository`). Alinear `usuario/` al patrón repository es
> limpieza pendiente, no bloqueo.

> Lo que NO está escrito todavía: los **nombres concretos** de los paquetes de
> los dominios futuros (08-codebase-design.md los deja al repo de aplicación
> "siguiendo este doc"). El patrón queda fijado por `usuario/` y aplica igual a
> `proyecto/`, `insumo/`, `apu/`, `presupuesto/`, `cronograma/`, `documento/`.

## 6. Frontend (repo aparte, según el mismo diseño)

No vive en este repo, pero el diseño canónico lo fija: React + Vite, generación
de tipos desde el OpenAPI del backend (`openapi-typescript`), **sin motor de
cálculo en el cliente** (ADR 9: el grid del editor hace commit por celda →
`PATCH` → pinta el `ApuResponse` recalculado), módulos por feature (`auth`,
`proyectos`, `insumos`, `apu-editor`, `presupuesto`, `cronograma`, `exportar`,
`admin`, `shell`, `ui`), estado de servidor con TanStack Query, validación Zod
con los rangos RNF-09.

---

## 7. Infraestructura de datos y despliegue (planeada)

- **BD:** PostgreSQL vía Neon (nube, free-tier); Docker Compose local con
  `postgres:18-alpine` en puerto 5436 (nota: el README dice PG16 — revisar la
  versión intencionada).
- **Migraciones:** Flyway `V{NNN}__snake.sql`; nunca editar una aplicada.
- **Despliegue:** contenedor **Quarkus native** (~64 MB RAM, boot ~50 ms) →
  Render/Koyeb/Cloud Run + Cloudflare Pages (frontend). Sin orquestador
  (Kubernetes: no). Traefik/Consul descartados por el análisis (no están en
  `thesis-docs`).
- **CI/CD:** GitHub Actions planeado (JVM + native), **pendiente de escribirse**
  con `./gradlew` (`.github/` no existe aún).

---

## 8. Evaluación de la organización actual (síntesis)

| Aspecto | Estado |
|---|---|
| Coherencia con el doc canónico | ✅ Total — código e docs nunca divergen |
| Módulos profundos vs. capas huecas | ✅ Motor aislado; auth autocontenido |
| Costuras (seams) explícitas | ✅ Correo es el único puerto; Postgres directo |
| Testabilidad por costura | ✅ Unit (motor/jqwik) · api (@QuarkusTest + Dev Services) |
| Paquetes del dominio core | ⬜ Pendiente de escribir (I-03 en adelante) |
| Divergencias conocidas | ⚠️ `quarkus-security-jpa` sin uso (dead weight); warnings de config menores |
