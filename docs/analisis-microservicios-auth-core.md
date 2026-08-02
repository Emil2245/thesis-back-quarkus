# Análisis: separación en microservicios (auth-service vs core/propuesta-service)

**Fecha:** 2026-08-01
**Base:** código en `src/main/java/`, DDL en `database/db_schemas/`, planes en `plans/`
y docs canónicos en `../thesis-docs/plan/`.
**Alcance:** diagnóstico de separabilidad, punto óptimo de gestión JWT y
recomendación final con base en el código real.

---

## 0. Resumen ejecutivo

| Pregunta | Respuesta corta |
|---|---|
| ¿Está el código listo para separarse? | **Sí, y es casi trivial**: el módulo `usuario/` es 100 % autocontenido y el `motor/` no tiene ningún acoplamiento. El problema no es el código de hoy — es que **el servicio core todavía no existe** (no hay una línea de código de `proyectos`, `presupuestos`, `apus`, etc.). |
| ¿Dónde vive la fricción real? | En el **esquema** (3 FKs `core → usuario` con semántica `ON DELETE` que se perderían) y en la **infraestructura** (2 builds, 2 BD, Traefik/Consul, JWKS) que **no está contemplada en los docs canónicos**. |
| ¿Es válida la estrategia JWT planteada? | **Sí, con 2 correcciones**: (1) el JWT de hoy **no lleva el `usuarioId` numérico** que el core necesita para escribir `proyecto.usuario_id` sin join; (2) con clave PEM de firma no hay `kid`, y el JWKS necesita una historia de `kid` para funcionar de forma robusta. |
| ¿Revocación entre servicios? | **No hace falta propagar nada.** Los refresh tokens (`refresh_token.revocado_en`) son opacos y solo los ve auth-service; el core nunca los toca. La única ventana es la del access token (≤ 60 min), que se documenta. |
| ¿Traefik + Consul? | Correcto que Traefik solo enruta y la validación vive en cada servicio — pero **ni Traefik ni Consul existen en `thesis-docs`**; el plan de despliegue canónico es un contenedor Quarkus + Neon. Es una premisa nueva que debe documentarse antes de implementarse (regla "The One Rule"). |
| **Recomendación final** | **No separar ahora.** Mantener el monolito modular, adoptar 2 reglas de costura gratuitas (claim `usuarioId` + disciplina de paquetes), y — solo si el jurado exige evidencia de microservicios — hacer la extracción de auth como **ejercicio controlado al final** (I-11/I-12), nunca como backbone del despliegue. |

**El argumento en una línea:** la separación es *técnicamente* barata hoy (es el
momento más barato de toda la tesis), pero *estratégicamente* no compra ninguna de
las 4 variables de tesis (`exactitud_calculo`, `integridad_referencial`,
`conformidad_formato`, SUS) y roba 1 iteración completa de las 10 que quedan de
funcionalidad núcleo.

---

## 1. Estado real del repositorio (lo verificado, no lo supuesto)

### 1.1 Lo que existe hoy en código

```
src/main/java/ec/uce/propuestas/
├── common/        # RestApplication, ErrorPayload, GlobalExceptionMapper  (infra compartida)
├── usuario/       # Usuario, Rol, RefreshToken, TokenUsuario, TipoToken, UsuarioRepository
│   └── auth/      # AuthResource, PerfilResource, AuthService, TokenService,
│       ├── dto/   #   PasswordService, PasswordPolicy, EnviadorCorreo (puerto)
│       └── mail/  #   LogEnviadorCorreo (adaptador dev)
└── motor/         # Motor, snapshots/resultados, internal/CalculadorFila, internal/Consolidador
    └── internal/  #   (paquete-privado, puro Java, sin framework)
```

Hallazgo crítico para este análisis: **el dominio core (proyecto, presupuesto,
capitulo, rubro, apu, insumo, cronograma, etc.) NO tiene ninguna línea de código
en este repositorio.** Solo existe como DDL (V001–V003) y como contrato REST en
`thesis-docs`. Por lo tanto:

- La pregunta "¿qué tan entreverada está la separación hoy?" se responde con un
  **cero absoluto**: no hay imports cruzados, no hay transacciones que toquen
  ambos dominios, no hay servicios mezclados. El único acoplamiento de `usuario/`
  es hacia `common/` (3 clases de infraestructura, triviales de duplicar).
- "Separar ahora" no es refactorizar código: es **decidir la topología del código
  core que aún no se ha escrito**. Es el momento más barato posible — y también
  el único momento en que sigue siendo barato.

### 1.2 Lo que existe como esqueleto y como drift

- `admin/` contiene un **scaffold Gradle de Quarkus vacío** (`ExampleResource`)
  sin ninguna dependencia ni lógica. Es la única señal de que el equipo ya
  exploró dividir (probablemente el panel Super-Admin, I-11). Hoy no pesa nada,
  pero es un fragmento de decisión no documentada.
- `docker-compose.yml` solo levanta Postgres (puerto 5436). No hay rastro de
  Traefik ni Consul en todo el repositorio.

### 1.3 La divergencia con los docs canónicos (importante)

El usuario plantea "Traefik + Consul para service discovery y health checks".
Verificado en `thesis-docs/plan/architecture/`:

- `02-cloud-deployment.md` planifica **un solo contenedor Quarkus native** en
  Render + Neon. "Kubernetes: no." "The PaaS is the orchestrator."
- `01-system-architecture.md` §2 muestra un **único backend** con
  Resource/Service/Calc engine/Doc-gen/Security dentro.
- El mismo doc §3 anota: *"Bounded contexts: likely a single context for the
  thesis scope"*, y §0: *"Built by 2 authors in 24 weeks under XP → favor
  simplicity over cleverness."*
- `grep -r "Traefik\|Consul" thesis-docs/plan/` → 0 resultados reales.

Conclusión: **la premisa de infraestructura del prompt no está en los docs
canónicos.** Si la separación avanza, lo primero que hay que hacer es un cambio
en `thesis-docs` (la regla de oro de este repo). Se detalla en §6.

---

## 2. Diagnóstico de separabilidad del código actual

### 2.1 Acoplamiento por dominio (verificado con grep)

| Dirección | Resultado |
|---|---|
| `motor/ → usuario/` | **Cero.** `grep -rn "usuario\|Usuario" src/main/java/ec/uce/propuestas/motor/` → sin hits. El motor es puro, recibe snapshots, no conoce identidad. |
| `usuario/ → motor/` | Cero. Auth no usa el motor (correcto: nada de cálculo en auth). |
| `usuario/ → common/` | `ErrorPayload` (usado en `AuthService`), `GlobalExceptionMapper` (se comparte vía CDI), `RestApplication` (define `/api/v1`). Infra, no dominio. |
| `motor/ → common/` | Cero (es puro, ni siquiera usa el mapper). |
| `usuario/auth/ → usuario/*` | Intradominio. `AuthService` inyecta `UsuarioRepository`, `TokenService`, `PasswordService`, `EnviadorCorreo` — todos dentro de `usuario/`. |

**Diagnóstico de código:** la separación a nivel de clases es una operación de
**mover carpetas**. `usuario/` + `common/` forman un auth-service completo.
`motor/` + el core futuro forman el core-service. No hay deuda que pagar.

### 2.2 Transacciones que cruzan dominios

Ninguna. Las únicas transacciones (`@Transactional`) viven en `AuthService` y
`TokenService` y tocan solo `usuario`, `refresh_token`, `token_usuario`
(verificado línea por línea en `AuthService.java` y `TokenService.java`).

Importante a futuro: **toda la cascada write-through del core
(`apu_detalle.costo → … → presupuesto.total → actividad.peso_ponderado`) ocurrirá
íntegramente dentro del core-service.** La decisión del punto 1 del prompt (no
subdividir el core) es correcta y el código de hoy ya la respeta: el motor es un
módulo puro llamado *desde* el core, en la misma transacción de la mutación. Eso
no se toca.

### 2.3 Acoplamiento a nivel de esquema (la fricción real)

Solo **3 FKs del core apuntan a `usuario(id)`** (todas en `V001__baseline.sql`):

| FK | Semántica actual | Qué se pierde al separar |
|---|---|---|
| `proyecto.usuario_id → usuario(id)` | `ON DELETE RESTRICT` | No se puede borrar un usuario con proyectos. Sin FK, esto **debe reimplementarse en la app** (consulta al core desde auth, o excepción a nivel servicio). |
| `plantilla_apu.usuario_id → usuario(id)` | `ON DELETE CASCADE` (nullable) | Borrar usuario limpia sus plantillas personales. Sin FK, hay que borrarlas vía llamada al core o dejarlas huérfanas. |
| `log_actividad.usuario_id → usuario(id)` | `ON DELETE SET NULL` (nullable) | Al borrar usuario, el log queda con `usuario_id = NULL`. Sin FK, hay que nullear vía app. |

Además:
- `refresh_token.usuario_id` y `token_usuario.usuario_id` son **intradominio
  auth** — se van completas con el auth-service, sin fricción.
- **No hay ninguna FK inversa** (`usuario` no referencia tablas del core). El
  grafo de dependencias del esquema es un árbol dirigido *hacia* el usuario, lo
  que hace la separación estructuralmente limpia.
- En el monolito, **mantener estas FKs es lo correcto** y no cuesta nada. Son
  parte de la variable `integridad_referencial` de la tesis. No hay que
  pre-eliminarlas "por si acaso": el costo de eliminarlas se paga **una sola vez,
  cuando y si** se hace la separación (una migración `V00X`), no antes.

### 2.4 Queries cross-domain que exigiría el core (hoy o en el futuro)

Sin JOIN de BD entre dominios, cada uno de estos casos de uso necesita una
respuesta. Los identifico porque son la lista de trabajo real de una separación:

| Caso de uso | Dónde vive hoy (contrato) | Solución sin JOIN (en separación) |
|---|---|---|
| "Mis proyectos" (`GET /proyectos?q…`, TC-P05) | core filtra `proyecto.usuario_id = <yo>` | **Resolver el id desde el claim del JWT** (cambio `usuarioId` de §4.3). Cero llamadas. |
| "Crear proyecto" (`POST /proyectos`) | core persiste `proyecto.usuario_id` | Mismo: claim del JWT. Cero llamadas. |
| "Creado por X" en logs (`GET /admin/logs` → `LogActividadResponse.usuarioNombre`) | core cruza `log_actividad.usuario_id → usuario.nombre` | **Denormalizar**: snapshot de `usuario_nombre` al escribir el log (§5.5). Recomendado. |
| Borrar usuario con proyectos (`DELETE /admin/usuarios/{id}` → 409) | auth necesita saber si el core tiene proyectos/plantillas | Llamada síncrona opcional `GET /core/usuarios/{id}/uso` (§5.4). Operación admin rara → aceptable. |
| Desactivar usuario (`POST /admin/usuarios/{id}/desactivar`) | auth marca `activo=false` | Sin propagación; ventana ≤ 60 min del access token (§4.4). |
| Plantillas personales al borrar usuario | auth necesita borrar/desactivar plantillas del core | Llamada síncrona o dejar la columna como referencia débil y limpiar en batch. |

Regla de oro que se deriva: **todo lo que el core necesita del usuario debe estar
en el claim del JWT, no en una llamada.** Todo lo que el auth necesita del core
debe ser raro (admin) o denormalizado (logs).

---

## 3. Mapa de endpoints por servicio (si se separa)

Basado en el inventario real de `thesis-docs/plan/architecture/07-api-contract.md`
y en lo que existe hoy en `AuthResource`/`PerfilResource`.

### 3.1 auth-service (base `/api/v1`)

| Endpoint | Proceso | Estado |
|---|---|---|
| `POST /auth/registro`, `/verificar-email`, `/reenviar-verificacion` | P-01 | ✅ Existe hoy |
| `POST /auth/login`, `/refresh`, `/logout` | P-02 | ✅ Existe hoy |
| `POST /auth/recuperar`, `/restablecer` | P-03 | ✅ Existe hoy |
| `GET/PUT /perfil`, `PUT /perfil/password` | P-04 | ✅ Existe hoy |
| `POST /auth/aceptar-invitacion` | P-38 (parte) | ✅ Existe hoy |
| `GET/POST /admin/usuarios`, `PUT/DELETE /admin/usuarios/{id}`, `…/desactivar`, `…/reactivar` | P-38 | 📝 I-11 |
| `GET /.well-known/jwks.json` | (nuevo, solo en separación) | ➕ §5.6 |

**Mueve consigo:** `usuario`, `refresh_token`, `token_usuario` (BD), bcrypt,
`EnviadorCorreo`, los TTLs D-01/D-02/D-11.

### 3.2 core-service (base `/api/v1`)

Todo lo demás del contrato, incluido el **admin de negocio** (que NO es de
usuarios):

- `/proyectos` (P-05…P-11) — dueño del usuario como claim, sin JOIN.
- `/insumos`, `/bases-centrales`, `/proyectos/{id}/insumos/*` (P-13…P-18).
- `/apus`, `/presupuestos`, `/capitulos`, `/rubros` (P-19…P-32) — **aquí vive la
  cascada write-through + el motor**.
- `/cronogramas`, `/actividades` (P-33…P-36).
- `/documentos/*` (P-37) — POI/OpenPDF.
- `/admin/bases-centrales`, `/admin/plantillas-apu`, `/admin/parametros-sistema`,
  `/admin/valores-referencia`, `/admin/logs` (P-39…P-42).

**Punto de cruce único:** `DELETE /admin/usuarios/{id}` (auth) necesita saber si
el core tiene referencias → el único endpoint de negocio nuevo que exige la
separación es uno de **consulta de uso** (p. ej. `GET /core/usuarios/{id}/uso` →
`{proyectos, plantillas}`), síncrono y restringido a roles admin.

---

## 4. Estrategia JWT: confirmación y correcciones

### 4.1 ¿Dónde vive la emisión y renovación? → auth-service. Confirmado.

Los refresh tokens y los tokens de un solo uso son **opacos, con hash en BD**
(`refresh_token`, `token_usuario`) y TTLs de negocio (D-01/D-02/D-11). Su
validación exige la BD de usuarios → **no pueden vivir en el core**. La decisión
planteada es correcta: el auth-service emite y renueva; el core solo valida.

### 4.2 Configuración exacta del core-service (validación local, sin red por request)

Lo que hoy tiene el monolito (`application.yml:44-60`):

```yaml
smallrye:
  jwt:
    sign:
      key:
        location: META-INF/resources/privateKey.pem
mp:
  jwt:
    verify:
      publickey:
        location: META-INF/resources/publicKey.pem
      issuer: https://propuestas-api.local
```

En la separación, el **core-service elimina la sección `sign`** (nunca firma) y
apunta la verificación al JWKS del auth-service. SmallRye JWT / Quarkus soportan
`mp.jwt.verify.publickey.location` apuntando a una **URL HTTP(S) de JWKS**, con
caché interna — es decir, la validación se hace local por request, y el fetch del
JWKS ocurre con refresco periódico, no por request. Configuración:

```yaml
# core-service/src/main/resources/application.yml
mp:
  jwt:
    verify:
      publickey:
        location: ${JWT_JWKS_URL:https://auth.propuestas.local/.well-known/jwks.json}
      issuer: https://propuestas-api.local
      algorithm: RS256

smallrye:
  jwt:
    jwks:
      refresh-interval: 60            # min; si el JWKS no trae Cache-Control
      forced-refresh-interval: 30     # min; re-fetch ante token con kid desconocido
      retain-cache-on-error-duration: 5
    resolve-remote-keys-at-startup: false   # arranque sin depender de auth

quarkus:
  smallrye-jwt:
    blocking-authentication: true     # el fetch de llaves es bloqueante
```

Notas de precisión:
- `smallrye.jwt.jwks.*` solo aplican cuando la location es URL HTTP(S) — que es
  el caso del core. No aplican al monolito actual (location local).
- **Dependencia de arranque:** con `resolve-remote-keys-at-startup=false` (default)
  el core arranca aunque auth esté caído; la primera validación reintentará el
  fetch. Correcto para no acoplar ciclos de vida.
- **Alternativa pragmática para tesis:** en vez de JWKS, montar el mismo
  `publicKey.pem` en ambos contenedores vía
  `MP_JWT_VERIFY_PUBLICKEY_LOCATION` como variable de entorno, y rotar la clave
  con el redeploy. Elimina el endpoint JWKS y la caché a cambio de rotación por
  despliegue. Si el jurado no pide rotación en caliente, esta variante es la más
  barata. La decisión del prompt (JWKS) es válida y más "microservicios reales";
  solo exige la historia de `kid` de §4.3.

### 4.3 Las 2 correcciones al JWT de hoy (cuestan 2 líneas y desbloquean todo)

**Corrección 1 — claim `usuarioId` numérico.** Hoy `TokenService.mintAccessToken`
(`TokenService.java:47-53`) emite:

```java
Jwt.issuer("https://propuestas-api.local")
    .upn(usuario.email)               // principal = email
    .groups(Set.of(usuario.rol.name()))
    .expiresIn(ACCESS_TOKEN_TTL_SECONDS)
    .sign();
```

El core necesita el **id numérico** para escribir `proyecto.usuario_id` y para
filtrar "mis proyectos" sin resolver email→id (que en separación implicaría una
llamada o un JOIN imposible):

```java
Jwt.issuer("https://propuestas-api.local")
    .subject(usuario.id.toString())   // sub = id numérico (SERCOP core usa id)
    .upn(usuario.email)               // se conserva para perfil/display
    .groups(Set.of(usuario.rol.name()))
    .claim("usuarioId", usuario.id)   // explícito y self-descriptive
    .expiresIn(ACCESS_TOKEN_TTL_SECONDS)
    .sign();
```

Este cambio es **recomendable incluso sin separación** (gratis, borra la única
dependencia oculta email→id y prepara la costura). Los tests de `AuthResourceIT`
deben actualizarse para validar el claim.

**Corrección 2 — historia de `kid` para el JWKS.** Con clave de firma PEM no hay
`kid` en el header del token, y la selección de clave en un JWKS se hace por
`kid`. Para un JWKS de una sola clave SmallRye suele resolverla, pero es frágil.
La receta robusta:
1. Firmar con JWK en lugar de PEM (`smallrye.jwt.sign.key.location` → archivo
   `.jwk` con `kid` fijo, p. ej. `"propuestas-2026-01"`), o forzar el header
   `kid` en el builder (`Jwt.issuer(...).jws().header("kid", "...")`).
2. El recurso JWKS sirve la misma `kid` (una sola entrada).

Con eso, la rotación de claves en el auth-service (cambiar el JWK + redeploy)
propaga al core por la caché con `forced-refresh-interval`.

### 4.4 Revocación e invalidación entre servicios (respondido con precisión)

La pregunta del prompt: *"¿vale la pena caché/invalidación de tokens revocados
entre servicios, dado que `refresh_token.revocado_en` existe, y cómo se propaga
sin acoplamiento síncrono?"*

Respuesta corta: **no hay nada que propagar, porque los dos tipos de token tienen
semánticas distintas y no comparten servicio:**

- **Refresh tokens** (`refresh_token.revocado_en`): opacos, con hash, validados
  **solo por auth-service**. El core nunca los recibe ni los valida. La revocación
  (logout, cambio de contraseña D-03, rotación) es **local a auth** y efectiva de
  inmediato — para la *sesión* (la renovación). No requiere caché compartida ni
  eventos.
- **Access tokens** (JWT stateless): validados localmente por el core con la
  llave pública. Un JWT válido **no puede revocarse sin una denylist**, porque el
  core no consulta la BD de auth por request. Con TTL de 60 min (D-02), la
  ventana máxima de "usuario desactivado / password cambiado sigue teniendo
  acceso" es **≤ 60 min**.

**Recomendación para la tesis:** aceptar y documentar la ventana de 60 min. Es
el trade-off estándar y honesto del JWT stateless. La sesión muere en el próximo
refresh (auth bloquea el refresh por `activo=false` o tokens revocados), así que
la fuga real es acotada.

Si más adelante se exige revocación inmediata del access token, las opciones en
orden de costo son: (a) denylist de `jti` en la BD del core + auth escribe a esa
denylist vía un endpoint interno de auth→core (acoplamiento mínimo, no por
request); (b) tabla compartida/Redis entre servicios. **No** se recomienda
ninguna de las dos para la tesis: agregan infraestructura sincrónica por un caso
que el TTL ya acota.

### 4.5 ¿Gateway/middleware específico?

Correcto el planteamiento: **Traefik solo enrutamiento declarativo + validación
local en cada servicio.** No hace falta middleware de validación JWT en el borde;
un plugin en Traefik sería solo defensa en profundidad y agregaría un punto de
configuración extra. La validación autoritativa en el core es `@RolesAllowed`
sobre el JWT ya verificado por SmallRye — que es exactamente lo que el monolito
hace hoy y lo que el core-service haría igual.

⚠️ **Pero** (ver §1.3): Traefik + Consul no están en los docs canónicos. El plan
`02-cloud-deployment.md` despliega un único contenedor con Render + Neon y
rechaza explícitamente orquestadores ("Kubernetes: no"). Si se introduce
Traefik/Consul, es una **decisión de arquitectura nueva** que debe:
1. Agregarse primero a `thesis-docs` (regla de oro de `CLAUDE.md`), y
2. Justificarse contra el plan free-tier que hoy da $0/mo con un solo servicio.

Para 2 servicios, la alternativa honesta es más barata: un reverse proxy simple
(path-based routing) en el mismo host, o incluso mantener un único dominio y
separar por ruta sin Consul. Consul/Traefik aportan service discovery real solo
cuando hay N>3 servicios que escalan por separado — no es el caso de la tesis.

---

## 5. Propuesta concreta de cambios (si se decide separar)

> Plan ejecutable de la separación, con rutas exactas. Solo aplica si se toma la
> decisión de §7; si no, **no hacer nada de esto**. Se incluye para que el equipo
> pueda ejecutarlo con precisión el día que la decisión exista.

### 5.1 Layout de repos/módulos

Dos opciones; se recomienda **un solo repo con dos módulos Maven** para no
multiplicar CI:

```
thesis-back-quarkus/
├── auth-service/        (módulo Quarkus — depende de common, nunca de core)
│   └── src/main/java/ec/uce/propuestas/
│       ├── common/      # ErrorPayload, GlobalExceptionMapper, RestApplication (copia)
│       └── usuario/     # entidades + auth/ (todo lo que hoy existe, sin cambios)
├── core-service/        (módulo Quarkus — depende de common, nunca de usuario)
│   └── src/main/java/ec/uce/propuestas/
│       ├── common/      # copia
│       ├── motor/       # tal cual hoy (puro)
│       └── <dominios core futuros>
└── pom.xml              (parent agregado)
```

Regla estructural que debe quedar escrita: **`core-service` no compila contra
nada de `auth-service`** (ni entidades, ni servicios). El único puente entre ellos
es el JWT. Si algún día un recurso core quiere el nombre del usuario, es
denormalización (§5.5), no import.

### 5.2 `common/`

Tres clases diminutas. Duplicarlas en ambos módulos es legítimo (son plantillas
de ~10 líneas cada una); un módulo compartido `common.jar` es también válido y
evita drift. No es decisión de fondo.

### 5.3 Flyway: división de baselines

Hoy `V001__baseline.sql` define **las 19 tablas** en una sola BD. Para separar:

| BD | Migraciones | Tablas |
|---|---|---|
| **BD auth** | baseline nuevo (o `V001` reescrito solo con usuario) | `usuario`, `refresh_token`, `token_usuario` + `flyway_schema_history` |
| **BD core** | baseline nuevo (todo lo demás) | `proyecto`, `presupuesto`, `capitulo`, `rubro`, `apu*`, `insumo`, `base_insumos`, `cronograma`, `actividad`, `valor_referencia`, `unidad_catalogo`, `parametros_*`, `firmante`, `log_actividad`, `plantilla_apu` + `flyway_schema_history` |
| **BD core** (migración de conversión) | `V00X__split_drop_usuario_fks.sql` | ver 5.4 |

Importante: en la BD core, el **historial de Flyway** debe quedar limpio (los
checksums del baseline original no son reproducibles si se eliminan tablas). El
camino limpio es: el core arranca con un baseline nuevo (migración inicial que
incluye todo menos las tablas auth), y NO se intenta conservar el historial de la
BD monolito — se trata como migración única con export/import de datos.

### 5.4 Migración de FKs + reimplementación de la semántica `ON DELETE`

Migración en la BD core (nunca editar V001; **no** eliminar las FKs hoy — solo al
separar):

```sql
-- V00X__split_drop_usuario_fks.sql  (BD core)
ALTER TABLE proyecto      DROP CONSTRAINT proyecto_usuario_id_fkey;
ALTER TABLE plantilla_apu DROP CONSTRAINT plantilla_apu_usuario_id_fkey;
ALTER TABLE log_actividad DROP CONSTRAINT log_actividad_usuario_id_fkey;
-- las columnas usuario_id se quedan como BIGINT simple (referencia débil)
```

La semántica que las FKs daban gratis pasa a la app:

| Semántica original | Reimplementación |
|---|---|
| `proyecto.usuario_id ON DELETE RESTRICT` | `DELETE /admin/usuarios/{id}` (auth) llama a `GET /core/usuarios/{id}/uso` antes de borrar; si `proyectos > 0` → 409. Único acoplamiento síncrono del sistema, y es admin-only + raro. |
| `plantilla_apu.usuario_id ON DELETE CASCADE` | auth pide a core borrar/desactivar las plantillas (`DELETE /core/plantillas-apu?usuarioId=…`) o las deja huérfanas con un barrido periódico. Recomendado: barrido (evita transacción distribuida). |
| `log_actividad.usuario_id ON DELETE SET NULL` | Al borrar usuario, auth llama a `POST /core/logs/anonimizar-usuario` (UPDATE `usuario_id = NULL`) o el core nullea en batch. Recomendado: batch. |

> Nota a la variable de tesis `integridad_referencial`: al perder las FKs, la
> garantía pasa de "la BD la impone" a "el código la mantiene". Para la tesis es
> un argumento *en contra* de separar: la integridad referencial es variable de
> evaluación y el monolito la tiene gratis en la BD.

### 5.5 "Creado por X" sin JOIN → denormalización

`GET /admin/logs` devuelve `LogActividadResponse.usuarioNombre` (contrato línea
433). Opciones:

1. **Recomendada — snapshot:** agregar `usuario_nombre VARCHAR(200)` a
   `log_actividad` y copiarlo al escribir el log. El core lo escribe porque el
   nombre viene en el claim del JWT (`upn` = email, nombre vía claim nuevo
   opcional `nombre`). Cero llamadas, cero eventos, funciona offline.
2. Llamada a auth por fila — descartada (N+1 y acoplamiento por request).

Ojo con RNF-08/D-13: la tabla de log ya guarda `usuario_id`; un snapshot de
nombre es PII en la tabla de auditoría. Si el comité lo rechaza, la alternativa
es exponer solo `usuarioId` en la API y que el admin consulte el nombre en auth
(auth es quien tiene el directorio). Decisión de doc, no de código.

### 5.6 JWKS en auth-service

Con la firma en JWK (con `kid`), SmallRye publica el JWKS; si se firma con PEM,
un recurso explícito es lo más simple y determinista:

```java
// auth-service — publica la clave pública como JWKS
@Path("/.well-known/jwks.json")
@PermitAll
public class JwksResource {
    @Inject
    @ConfigProperty(name = "mp.jwt.verify.publickey.location") String pubLoc;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject jwks() {
        // lee la llave pública, deriva n/e, arma {"keys":[{kty:"RSA",kid:"…",n:"…",e:"…"}]}
        // el kid debe coincidir con el header kid del token firmado (§4.3)
    }
}
```

Con la estrategia alternativa (§4.2, PEM compartido por env var), este recurso no
se necesita.

### 5.7 Traefik + Consul (solo si se decide mantener esa premisa)

Si se confirma la premisa, el mínimo viable (docker-compose):

```yaml
# docker-compose.microservices.yml (esquema)
services:
  traefik:
    image: traefik:v3
    command:
      - --providers.consulCatalog.endpoint=consul:8500
    ports: ["80:80"]
  consul:
    image: hashicorp/consul:1.19
  auth-service:
    build: ./auth-service
    labels:
      - traefik.http.routers.auth.rule=PathPrefix(`/api/v1/auth`) || PathPrefix(`/api/v1/perfil`)
      - traefik.http.services.auth.loadbalancer.server.port=8080
  core-service:
    build: ./core-service
    labels:
      - traefik.http.routers.core.rule=PathPrefix(`/api/v1/proyectos`) || PathPrefix(`/api/v1/apus`) || …
      - traefik.http.services.core.loadbalancer.server.port=8080
```

Para que Consul catalog funcione con Traefik hace falta registrar los servicios
en Consul (registro manual, Consul SDK, o un agente en cada contenedor). Quarkus
no registra en Consul de fábrica; habría que añadir el agente o un sidecar. Es
trabajo real (~1–2 días) que hoy **no paga ninguna funcionalidad**.

### 5.8 Impacto en tests

- `AuthResourceIT` (hoy en `src/test/.../usuario/auth/`) se mueve tal cual al
  auth-service; su `@BeforeEach` truncando `token_usuario, refresh_token, usuario`
  sigue igual con Dev Services en la BD auth.
- El core tendrá sus propios `@QuarkusTest` con Dev Services en la BD core. **Dos
  bases de test** → o dos pipelines/instancias de Testcontainers, o un solo
  compose de test con ambas BD.
- Los tests del motor no cambian (puro, sin BD).
- Los tests de integración que hoy cruzan "registro → crear proyecto" (futuros)
  ya no pueden ser un solo `@QuarkusTest`: requerirían montar los dos servicios o
  un stub del auth.

### 5.9 Estimación de esfuerzo (dev-días, para 2 desarrolladores)

| Ítem | Esfuerzo |
|---|---|
| División de módulos Maven + mover paquetes | 1–2 d |
| CI (build por módulo) | 0.5 d |
| Baselines Flyway + export/import + migración FKs | 1–2 d |
| Semántica `ON DELETE` reimplementada + endpoint `/uso` | 1–2 d |
| JWT (claim `usuarioId`, `kid`, JWKS, config core) | 1–2 d |
| Denormalización logs / nombres | 0.5–1 d |
| Test infraestructura (2 BD, 2 servicios) | 1–2 d |
| Traefik + Consul + compose | 1–2 d |
| **Total** | **≈ 7–13 d ≈ 1 iteración XP completa** |

Comparación con lo que se está dejando de hacer en ese tiempo: **I-03 (proyectos)
es una iteración entera de 2 semanas.** Y la semana 3–4 ya está comprometida con
el hito del motor (GM-19/20 escalados al director, plan 006). Separar ahora
significa empezar I-03 con un hueco de 1 iteración.

---

## 6. Análisis costo/beneficio para la tesis (concreto, no genérico)

### 6.1 Qué se compra

- Ninguna de las **4 variables de tesis** (`exactitud_calculo`,
  `integridad_referencial`, `conformidad_formato`, SUS) mejora con la separación.
  La primera depende del motor puro (ya correcto); la segunda **empeora** porque
  se pierden FKs reales; la tercera depende de POI/OpenPDF (no cambia); la cuarta
  de la UX (no cambia).
- El requisito de arquitectura *"decoupled, cloud-native"* ya está satisfecho por
  el diseño actual: monolito modular, vertical slices, motor aislado, contenedor
  native-image, CI/CD, 12-factor. "Cloud-native ≠ microservicios"; la propia
  `thesis-docs/plan/architecture/01` dice que el bounded context es único.
- Potencial narrativo de tesis ("migré el módulo de auth a un microservicio") —
  es **evidencia adicional, no requisito evaluado**. Ver §7.

### 6.2 Qué cuesta

- **~1 iteración completa** de las 10 que quedan (ver 5.9).
- **Dos bases de datos** para operar/migrar en lugar de una (contra el plan
  free-tier `02-cloud-deployment.md`, que hoy es $0/mo con una sola BD Neon).
- **Sincronía admin** (el 409 de borrar usuario, la limpieza de plantillas, el
  nulleo de logs) que hoy es gratis en la BD.
- **Historia de claves** (JWK/`kid`/rotación) que hoy no existe porque la firma y
  verificación comparten repo.
- **Traefik/Consul**: premisa nueva que contradice los docs canónicos y agrega
  superficie operativa (registro de servicios, health checks, routing) sin
  funcionalidad nueva. Si se quiere demostrar, es un ejercicio docker-compose
  local, no el backbone de producción.
- **Riesgo de distracción:** la semana 3–4 tiene el hito de tesis del motor
  (GM-01…25 verdes) pendiente de decisión del director. Dividir la atención de
  ambos devs ahora compromete lo único que la tesis mide con dureza.

### 6.3 El timing (el dato que casi nadie mira)

- El **momento más barato** para separar es **hoy**, porque el core aún no tiene
  código: no hay FKs en entidades Java que romper, no hay queries que reescribir.
- Pero el **momento de máximo valor de una separación** (escalamiento real,
  equipos independientes) **nunca va a llegar** en esta tesis.
- Por tanto la elección real no es "ahora vs. después": es **"ahora (costo: 1
  iteración; beneficio: narrativa)" vs. "nunca (costo: 0; beneficio: 0)"**.
  "Después" es estrictamente peor que "ahora" en costo e idéntico en beneficio —
  es la trampa del plan "separar después".

---

## 7. Recomendación final

### **No separar ahora.** Mantener el monolito modular con el auth dentro.

Fundamentos, en orden de peso (todos verificados en el repo/docs):

1. **No compra ninguna variable de tesis** y degrada `integridad_referencial`
   (pierde 3 FKs reales con semántica `ON DELETE`).
2. **El core no existe todavía**: separar no es desacoplar código acoplado, es
   duplicar infraestructura para un servicio que aún no se ha escrito.
3. **Cuesta ~1 iteración** de las 10 restantes, en el momento exacto en que el
   hito del motor (GM-19/20) está escalado y sin resolver.
4. **La premisa Traefik/Consul contradice los docs canónicos** (`02-cloud-
   deployment.md`: un contenedor, "Kubernetes: no"; `01`: bounded context único).
   Introducirla requiere primero cambiar `thesis-docs` — y la justificación de
   fondo no existe a esta escala.

### Reglas de costura que sí conviene adoptar hoy (costo ≈ 0, desbloquean todo)

Estas tres cosas se hacen **sin separar** y dejan la puerta abierta si el
escenario cambia:

1. **Claim `usuarioId` en el JWT** (`TokenService.mintAccessToken`,
   `TokenService.java:47`). El core lo va a necesitar igual (para escribir
   `proyecto.usuario_id` en I-03 con el id numérico, en vez de resolver por
   email), y es la única dependencia oculta email→id que hoy existe.
2. **Disciplina de paquetes explícita:** los futuros recursos core no inyectan
   servicios de `usuario/` ni importan `Usuario`; todo lo que necesitan del
   usuario vive en el claim del JWT. Hoy ya se cumple; se escribe como regla en
   el plan de I-03.
3. **No eliminar FKs "preventivamente"** — mantener `proyecto.usuario_id` con
   `REFERENCES` mientras sea monolito. Si algún día se separa, la migración de
   drop es un cambio puntual (una V00X), no una deuda que crezca.

### Condición de escape (si el jurado/exigencia lo obliga)

Si el evaluador exige **evidencia de microservicios** (no es el caso hoy según
los docs), hacerlo como **ejercicio controlado al final, no como backbone**:

- **Cuándo:** en I-11/I-12 (semanas 21–24), con el core funcional y las 4
  variables medidas. El costo de entonces es marginal si se siguieron las 3
  reglas de arriba (el claim ya está, los paquetes ya están aislados, las FKs ya
  se migran en un solo cambio).
- **Cómo:** extraer SOLO el auth como segundo artefacto desplegable (JWKS + claim
  `usuarioId` ya listos), en un compose local con Traefik/Consul como *demo* de
  infraestructura, documentando el trade-off en la tesis. El despliegue de
  producción se queda en el plan free-tier de un servicio.
- **Nunca:** "separar después" como plan de trabajo para ahora. Es la opción que
  paga el costo de hoy y no recibe el beneficio de ningún escenario.

### Veredicto en una tabla

| Opción | Costo | Beneficio | Veredicto |
|---|---|---|---|
| **Separar ahora** | ~1 iteración + 2 BD + Traefik/Consul + JWT/`kid` | Narrativa (no evaluado) | ❌ No |
| **Separar después (I-11/I-12)** | ~1 iteración al final, sobre un core estable | Evidencia opcional si el jurado la pide | ⏳ Condicional |
| **No separar** (mantener costura limpia) | 0 | Todo el core a tiempo; integridad en BD | ✅ **Recomendado** |

---

## 8. Anexos

### 8.1 Fuentes verificadas en este análisis

| Tema | Fuente |
|---|---|
| Inventario de código | `src/main/java/ec/uce/propuestas/**` (glob del repo) |
| Acoplamiento motor↔usuario | `grep -rn "usuario" src/main/java/ec/uce/propuestas/motor/` → 0 hits |
| FKs core→usuario | `src/main/resources/db/migration/V001__baseline.sql` (§2.3, §2.12, §2.15) y `database/db_schemas/public/*.sql` |
| JWT actual | `src/main/resources/application.yml:44-60`, `TokenService.java:47-53` |
| Contrato de endpoints | `thesis-docs/plan/architecture/07-api-contract.md` (inventario §3) |
| Módulos profundos y costuras | `thesis-docs/plan/architecture/08-codebase-design.md` (§1, §5, §7) |
| ADRs | `thesis-docs/plan/architecture/01-system-architecture.md` §9 (ADR 1/2/7/8/10) |
| Despliegue | `thesis-docs/plan/architecture/02-cloud-deployment.md` (sin Traefik/Consul; un contenedor) |
| Roadmap / timeline | `thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` (I-01…I-12) |
| Estado del motor | `plans/README.md` (GM-19/20 escalados; plan 006) |
| Soporte JWKS en Quarkus | `mp.jwt.verify.publickey.location` HTTP(S) + `smallrye.jwt.jwks.*` (SmallRye JWT docs) |
