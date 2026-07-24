# 004 — Auth module: registration, login, JWT, refresh, password reset, profile, invitation

- **Status:** TODO
- **Iteration:** I-01 (roadmap/01, weeks 1–2) — closes US-01…US-07 minus profile edits
- **Depends on:** 001 (Quarkus + smallrye-jwt + security-jpa), 003 (`usuario`, `refresh_token`, `token_usuario` tables)
- **Blocks:** every subsequent feature plan — all non-`/auth` endpoints require `@Authenticated`

---

## Context

This is the first plan that writes actual Java. It implements the auth
surface roadmap I-01 requires: registration + email verification, login,
JWT access + refresh, password recovery, profile read (+ email/password
change), and super-admin invitation acceptance. That's US-01…US-04 and
US-06…US-07 from roadmap/01.

**Business rules are non-negotiable and come from
`../../thesis-docs/plan/design/03-procesos-detalle.md §J`**:

| Decision | Value | Source |
|---|---|---|
| Password policy | ≥8 chars, ≥1 letter, ≥1 number | D-01 |
| Email verification token TTL | **24 h** | D-01 |
| Resend verification cooldown | **60 s** | D-01 |
| JWT access token TTL | **60 min** | D-02 |
| Refresh token TTL, "recordar sesión" = true | **30 days** | D-02 |
| Refresh token TTL, "recordar sesión" = false | Browser session (no server-side TTL enforcement — the token still exists, but the client discards it on close) | D-02 |
| Password change consequence | Revoke ALL user's refresh tokens | D-03 |
| Email change consequence | New email requires re-verification; account remains operable with old email until verified | D-03 |
| Invitation token TTL | **72 h** | D-11 |
| Password reset token TTL | 24 h (same as verification, D-01 doesn't split them) | D-01 (inferred) |
| Password hash | bcrypt, `VARCHAR(72)` | schema, RNF-05 |
| Two roles only | `USUARIO`, `SUPER_ADMIN` | schema |
| Failed-login response | 401, message "correo o contraseña incorrectos" (no channel identified) | P-02 |
| Unverified-email login | 403 `email-no-verificado` | P-02 |
| Deactivated account login | 403 `cuenta-desactivada` | P-02 |
| `POST /auth/recuperar` | Always returns **202** regardless of whether the email exists (anti-enumeration) | P-03 |

**Storage of tokens**: opaque strings; the DB stores SHA-256 hashes only.
Both `refresh_token.token_hash` and `token_usuario.token_hash` are
`VARCHAR(64)` — matches hex SHA-256 length. The raw token goes to the
user (in emails, or as the refresh-token response value) and is never
stored plaintext, ever.

**Email delivery**: the source doc names Brevo as the production adapter
and specifies a "grabadora" (recorder) adapter in test flows
(`../../thesis-docs/plan/architecture/08-codebase-design.md §1`). This
plan wires an **abstract port** with a **log-only dev adapter** and a
**recording test adapter**. Brevo integration is a separate later plan;
this one leaves a TODO and a Brevo-shaped seam.

## In scope

Java sources under `src/main/java/ec/uce/propuestas/`:

- `usuario/` — package for the module.
  - `Usuario.java` — Panache entity mapped to `usuario` table.
  - `UsuarioRepository.java` — Panache repository for common queries
    (findByEmail, isEmailTaken).
  - `Rol.java` — enum `{USUARIO, SUPER_ADMIN}` with `@Enumerated(EnumType.STRING)`.
  - `RefreshToken.java` — entity for `refresh_token`.
  - `TokenUsuario.java` — entity for `token_usuario`, with `TipoToken` enum.
  - `TipoToken.java` — enum
    `{VERIFICACION_EMAIL, RESET_PASSWORD, INVITACION, CAMBIO_EMAIL}`.
- `usuario/auth/` — auth-facing subpackage.
  - `AuthResource.java` — JAX-RS resource at `/api/v1/auth`.
  - `PerfilResource.java` — JAX-RS resource at `/api/v1/perfil`.
  - `AuthService.java` — orchestrates registration/login/reset flows.
  - `TokenService.java` — mints access tokens (JWT via
    `JwtClaimsBuilder`), issues opaque refresh + one-time tokens, hashes
    them, validates and consumes them.
  - `PasswordService.java` — bcrypt hash/verify. Just a thin wrapper —
    Quarkus ships bcrypt via `io.quarkus.elytron.security.common.BcryptUtil`.
  - `PasswordPolicy.java` — the "≥8 chars, ≥1 letter, ≥1 number" rule as
    a pure static function. Used by both registration and password change.
  - `dto/` — request/response records (see step 4).
  - `mail/` — the email port.
    - `EnviadorCorreo.java` — interface with `enviarVerificacion`,
      `enviarReset`, `enviarInvitacion`.
    - `LogEnviadorCorreo.java` — dev/prod-fallback adapter: writes the
      link to the log at INFO level. Marked `@Priority(1)` so tests can
      override it.
- `common/`
  - `ErrorPayload.java` — record `{codigo, mensaje}` for structured
    errors.
  - `GlobalExceptionMapper.java` — maps `WebApplicationException` and
    validation failures to `ErrorPayload` JSON with the right status.

JWT config:
- `src/main/resources/META-INF/resources/publicKey.pem` — **do not
  commit real keys**. Ship a placeholder key generated for **dev only**;
  document rotation in the maintenance note.
- `src/main/resources/META-INF/resources/privateKey.pem` — same caveat.
- `application.yml` — add JWT issuer/audience/keys config keys (see
  step 3).

Tests under `src/test/java/ec/uce/propuestas/usuario/auth/`:
- `AuthResourceIT.java` — `@QuarkusTest`, covers TC-P01-01…05,
  TC-P02-01…05, TC-P03-01…04, TC-P04-01…05.
- `RecordingEnviadorCorreo.java` — test-scoped alternative
  `@Alternative @Priority(10)` that records sent emails so the tests
  can extract the token.
- `PasswordPolicyTest.java` — pure JUnit test of the policy function.

## Out of scope — do NOT touch or add

- Brevo integration. Interface + log adapter only; real SMTP/API adapter
  is a separate plan.
- `POST /perfil/logo` and other profile fields beyond `nombre` + `email`
  (source doc P-04 only mentions those; project logo lives on
  `proyecto`, not `usuario`).
- Super-admin invitation *creation* endpoint (`POST /admin/usuarios` in
  P-38). This plan only implements `POST /auth/aceptar-invitacion` — the
  admin-side create endpoint is I-11 (plan not yet written).
- Any GDPR/LOPDP data-export or account-deletion endpoints. Not in the
  I-01 scope.
- Rate limiting beyond the 60 s resend cooldown. General rate limiting
  is a future concern; the cooldown is business logic.
- OAuth / SSO / MFA. All out of scope for this thesis (v1.1 §6).

## Repo conventions to match

- **Package layout**: source doc
  `../../thesis-docs/plan/backend/01-quarkus-backend.md §2` prescribes
  vertical slices per module (`usuario/`, `insumo/`, `apu/`, …) with
  Entity + Resource + DTO. **Deep modules only get Service/Repository**
  (`../../thesis-docs/plan/architecture/08-codebase-design.md §7`).
  Auth is a **deep module** — flows are non-trivial. Use `AuthService`,
  `TokenService`. Do NOT create a `UsuarioService` for CRUD-shaped
  profile ops; those go Resource → Panache directly.
- **DTOs are Java `record`s**, camelCase JSON. `@JsonInclude(NON_NULL)`
  at the class-level Jackson mixin (or just skip null fields in the
  response records — records are `record RegistroReq(String nombre,
  String email, String password, String passwordConfirmacion) {}`).
- **REST base path**: `/api/v1` — set via `@ApplicationPath("/api/v1")`
  in `ec.uce.propuestas.common.RestApplication`. `AuthResource` gets
  `@Path("/auth")`, `PerfilResource` gets `@Path("/perfil")`. Do **not**
  hardcode `/api/v1` inside each resource.
- **Bean validation** at DTO boundary using Jakarta annotations
  (`@Email`, `@NotBlank`, `@Size`). Custom password policy is enforced
  in the service, not in an annotation — the "≥1 letter and ≥1 number"
  rule is server-side business logic, not a DTO-format concern.
- **`@RolesAllowed`** on resources. `/auth/**` is `@PermitAll`; profile
  ops are `@RolesAllowed({"USUARIO","SUPER_ADMIN"})`; admin-only ops
  (none in this plan) would be `@RolesAllowed("SUPER_ADMIN")`.
- **Never log secrets**: no token values, no password values (raw or
  hashed) in logs (RNF-08 in the requirements doc). Log token *events*
  (issued/consumed/expired) with the token ID, never the value.

## Steps

### 1 — Confirm baseline

```bash
./mvnw -q -DskipTests package
```

Expected: BUILD SUCCESS. If not: STOP.

Confirm the schema is in place:

```bash
grep -c 'CREATE TABLE usuario' src/main/resources/db/migration/V001__baseline.sql
grep -c 'CREATE TABLE refresh_token' src/main/resources/db/migration/V001__baseline.sql
grep -c 'CREATE TABLE token_usuario' src/main/resources/db/migration/V001__baseline.sql
```

Expected: each returns `1`. If any returns `0`: STOP — plan 003 has not
landed.

### 2 — Generate JWT dev keys

Generate an RSA keypair for signing dev JWTs. **These are dev-only keys;
they can live in the repo, but production must inject its own via env
vars.**

```bash
mkdir -p src/main/resources/META-INF/resources
openssl genrsa -out /tmp/privkey.pem 2048
openssl rsa -in /tmp/privkey.pem -pubout -out src/main/resources/META-INF/resources/publicKey.pem
openssl pkcs8 -topk8 -inform PEM -in /tmp/privkey.pem -out src/main/resources/META-INF/resources/privateKey.pem -nocrypt
rm /tmp/privkey.pem
```

Add a `.gitattributes` note or a `# DEV-ONLY` line inside each key's
adjacent README, so future readers know these keys are not secrets. **If
these appear in `.env.example`**, they are already flagged as
`change-me-for-prod`.

If `openssl` is not on PATH: STOP and report. Do not fall back to a
weaker crypto (e.g. HS256 with a shared secret).

### 3 — Extend `application.yml` with JWT config

Add these keys **under `quarkus:`** (do not create a duplicate `quarkus:`
block; merge into the one plan 001 wrote):

```yaml
  smallrye:
    jwt:
      sign:
        key:
          location: privateKey.pem
      new-token:
        issuer: https://propuestas-api.local
        lifespan: 3600           # 60 min access token — D-02

mp:
  jwt:
    verify:
      publickey:
        location: publicKey.pem
      issuer: https://propuestas-api.local
```

Also add app-level auth config under `quarkus:`:

```yaml
  # Auth-related knobs (this app; not Quarkus core)
```

…and under a new top-level `app:` key:

```yaml
app:
  auth:
    verificacion-ttl: PT24H
    reset-ttl: PT24H
    invitacion-ttl: PT72H
    refresh-ttl-recordado: P30D
    refresh-ttl-sesion: PT12H     # generous session upper bound; client discards on close
    reenvio-cooldown: PT60S
```

These get injected into services via `@ConfigProperty(name = "app.auth.verificacion-ttl") Duration verificacionTtl`.

**Rationale for storing TTLs in config, not hardcoded:** the source doc
treats these as business decisions with named IDs (D-01, D-02, D-11); if
one changes, config keeps the change to one file. Do NOT ladder into a
CDI producer or "AuthConfig record" — direct `@ConfigProperty` on each
service field is enough.

### 4 — Write the DTOs

Path: `src/main/java/ec/uce/propuestas/usuario/auth/dto/`. Records only,
one per file:

```java
public record RegistroRequest(
    @NotBlank @Size(max = 200) String nombre,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank String password,
    @NotBlank String passwordConfirmacion) {}

public record VerificarEmailRequest(@NotBlank String token) {}
public record ReenviarVerificacionRequest(@NotBlank @Email String email) {}

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    boolean recordarSesion) {}

public record RefreshRequest(@NotBlank String refreshToken) {}

public record RecuperarPasswordRequest(@NotBlank @Email String email) {}
public record RestablecerPasswordRequest(
    @NotBlank String token,
    @NotBlank String password,
    @NotBlank String passwordConfirmacion) {}
public record AceptarInvitacionRequest(
    @NotBlank String token,
    @NotBlank String password,
    @NotBlank String passwordConfirmacion) {}

public record UsuarioResponse(Long id, String nombre, String email, String rol,
    boolean emailVerificado) {}

public record TokenResponse(String accessToken, long expiraEnSegundos,
    String refreshToken, UsuarioResponse usuario) {}

public record PerfilResponse(Long id, String nombre, String email, String rol,
    Instant fechaCreacion) {}

public record PerfilActualizarRequest(
    @NotBlank @Size(max = 200) String nombre,
    @NotBlank @Email @Size(max = 320) String email) {}

public record PasswordCambiarRequest(
    @NotBlank String passwordActual,
    @NotBlank String passwordNueva,
    @NotBlank String passwordConfirmacion) {}
```

`refreshToken` in `TokenResponse` is nullable — set to null when
`recordarSesion == false` and the client should not persist it.

### 5 — Write the entities

Path: `src/main/java/ec/uce/propuestas/usuario/`. Public fields on
Panache entities are the Quarkus-idiomatic style.

```java
// Rol.java
public enum Rol { USUARIO, SUPER_ADMIN }

// TipoToken.java
public enum TipoToken { VERIFICACION_EMAIL, RESET_PASSWORD, INVITACION, CAMBIO_EMAIL }

// Usuario.java
@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntity {  // id inherited as Long
    @Column(nullable = false, length = 200)                public String nombre;
    @Column(nullable = false, unique = true, length = 320) public String email;
    @Column(name = "password_hash", nullable = false, length = 72) public String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)                 public Rol rol = Rol.USUARIO;
    @Column(name = "email_verificado", nullable = false)   public boolean emailVerificado = false;
    @Column(nullable = false)                              public boolean activo = true;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false)         public Instant updatedAt;

    @PrePersist void onInsert() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate  void onUpdate() { updatedAt = Instant.now(); }
}
```

⚠ **DB defaults vs entity fields.** Postgres `DEFAULT 'USUARIO'` on `rol`
and `DEFAULT now()` on timestamps are DDL-level defaults; when Hibernate
sends an INSERT with explicit values (which it will, given the fields
above) those DB defaults are ignored. Both sides must agree on the
default value — that's what `Rol.USUARIO` and the `@PrePersist` setter do.

Similar for `RefreshToken` and `TokenUsuario`. Follow the schema
verbatim; snake_case columns map to camelCase fields via `@Column(name=)`.

**Do not use `PanacheEntityBase` and declare `id` yourself unless you
need a non-`Long` PK** — schema says `BIGINT IDENTITY`, so `PanacheEntity`
is right.

### 6 — Write `PasswordPolicy` and `PasswordService`

```java
// PasswordPolicy.java
public final class PasswordPolicy {
    private static final Pattern LETTER = Pattern.compile(".*[A-Za-zÁ-ú].*");
    private static final Pattern DIGIT  = Pattern.compile(".*\\d.*");
    private PasswordPolicy() {}
    public static boolean isValid(String password) {
        return password != null
            && password.length() >= 8
            && LETTER.matcher(password).matches()
            && DIGIT.matcher(password).matches();
    }
}

// PasswordService.java
@ApplicationScoped
public class PasswordService {
    public String hash(String plain) { return BcryptUtil.bcryptHash(plain); }
    public boolean verify(String plain, String hash) { return BcryptUtil.matches(plain, hash); }
}
```

`BcryptUtil` ships in `io.quarkus:quarkus-elytron-security-common`. If
that dep is NOT in `pom.xml` (plan 001 didn't add it — check), add it
here. This is the ONE pom edit permitted by this plan:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-elytron-security-common</artifactId>
</dependency>
```

Rebuild: `./mvnw -q -DskipTests package`.

### 7 — Write `TokenService`

Responsibilities:
- Mint access tokens (JWT) via `Jwt.issuer(...).upn(email).groups(rol).expiresIn(...).sign()`.
- Issue opaque refresh tokens: 32-byte `SecureRandom` → URL-safe base64
  → **return raw to caller, store SHA-256 hex in `refresh_token.token_hash`**.
- Issue one-time tokens for email verification / reset / invitation /
  email change: same shape, table `token_usuario`, with a `tipo` and
  `expira_en`.
- **Consume a one-time token**: look up by hash, check `tipo`, check
  `expira_en > now()`, check `usado_en IS NULL`, atomically set
  `usado_en = now()` and return the associated `usuario_id`. If any
  check fails → return empty, caller responds 410.
- **Refresh flow**: given a raw refresh token, look up by hash, check
  `expira_en > now()`, check `revocado_en IS NULL`, issue a new access
  token. Rotate the refresh token (invalidate old, issue new) per D-02
  best practice.
- **Revocation on password change** (D-03): `UPDATE refresh_token SET
  revocado_en = now() WHERE usuario_id = ? AND revocado_en IS NULL`.

Concrete hashing:
```java
static String sha256Hex(String raw) {
    try {
        var md = MessageDigest.getInstance("SHA-256");
        var bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        var sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
}
```

`VARCHAR(64)` — exactly 64 hex chars for SHA-256.

### 8 — Write `AuthService`

Orchestrates the flows. Each public method wraps its work in a
transaction and returns either a DTO or throws a `WebApplicationException`
mapped by `GlobalExceptionMapper` to a JSON `ErrorPayload`.

Method sketches:

```java
public UsuarioResponse registrar(RegistroRequest req);           // 201 + emails token
public void verificarEmail(String token);                        // 204
public void reenviarVerificacion(String email);                  // 202 unless cooldown → 429

public TokenResponse login(LoginRequest req);                    // 200 or 401/403
public TokenResponse refresh(String refreshTokenRaw);            // 200 or 401
public void logout(String refreshTokenRaw);                      // 204

public void iniciarRecuperacion(String email);                   // ALWAYS 202
public void restablecerPassword(RestablecerPasswordRequest req); // 204 or 400/410

public void aceptarInvitacion(AceptarInvitacionRequest req);     // 204 or 400/410
```

**Key rules to codify (verbatim from source doc):**
1. Registro: password matches passwordConfirmacion; passes PasswordPolicy;
   email not taken. Create `Usuario` with `email_verificado=false`.
   Issue `VERIFICACION_EMAIL` token (TTL 24 h). Call
   `enviadorCorreo.enviarVerificacion(email, token, expiresAt)`. Never
   log the token.
2. Reenviar-verificacion: look up user by email. **Cooldown check**:
   most recent `VERIFICACION_EMAIL` token created within 60 s → throw
   `WebApplicationException(429)`. Otherwise issue new token, send.
   *Anti-enumeration*: this endpoint does reveal existence via 429 vs
   202. That's accepted per source doc (an existing account trying to
   resend gets a helpful signal). If P-01 is later hardened to always
   return 202 like `/recuperar`, adjust here — for now, follow the doc.
3. Login:
   - `bcrypt.verify(password, usuario.passwordHash)` — if false, throw
     401 with `{codigo: "credenciales-invalidas"}`. **Never distinguish
     "unknown email" from "wrong password"** in the response (P-02).
     Implementation: look up by email; if not found, still call
     `bcrypt.verify` against a canned dummy hash to keep timing steady;
     then throw the same 401.
   - `emailVerificado == false` → 403 `email-no-verificado`.
   - `activo == false` → 403 `cuenta-desactivada`.
   - On success, issue access token (60 min). If `recordarSesion`,
     issue refresh token with TTL 30 d and store hash. Otherwise, issue
     refresh with TTL from `refresh-ttl-sesion` config (12 h) and
     return it too — client is expected to discard on browser close.
   - Return `TokenResponse` with both tokens.
4. Refresh: rotate. Old token → `revocado_en = now()`; new access +
   new refresh. If old token is revoked or expired → 401
   `token-invalido-o-expirado`.
5. Iniciar-recuperacion: look up by email.
   **If user exists**: issue `RESET_PASSWORD` token (TTL 24 h), email it.
   **If user does not exist**: do nothing (but return 202 identically —
   anti-enumeration).
6. Restablecer-password: validate passwords match + policy; consume
   RESET_PASSWORD token; update `passwordHash`; **revoke all refresh
   tokens for this user** (D-03).
7. Aceptar-invitacion: consume INVITACION token; set
   `passwordHash`; set `emailVerificado = true` (invitation implies
   the admin vouched for the email); set `activo = true`.

### 9 — Write the resources

```java
@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject AuthService svc;

    @POST @Path("/registro") @PermitAll
    public Response registro(@Valid RegistroRequest r) {
        return Response.status(201).entity(svc.registrar(r)).build();
    }
    // ... one method per endpoint per the auth spec table
}

@Path("/perfil")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO","SUPER_ADMIN"})
public class PerfilResource {
    @Inject SecurityIdentity identity;
    @Inject AuthService svc;

    @GET
    public PerfilResponse leer() {
        var email = identity.getPrincipal().getName();
        // look up by email, map to PerfilResponse
    }
    // PUT /perfil, PUT /perfil/password ...
}
```

Full endpoint list (matches the source doc table):

| Method | Path | @RolesAllowed | Request | Success | Notable errors |
|---|---|---|---|---|---|
| POST | `/auth/registro` | @PermitAll | RegistroRequest | 201 UsuarioResponse | 400 |
| POST | `/auth/verificar-email` | @PermitAll | VerificarEmailRequest | 204 | 410 |
| POST | `/auth/reenviar-verificacion` | @PermitAll | ReenviarVerificacionRequest | 202 | 429 |
| POST | `/auth/login` | @PermitAll | LoginRequest | 200 TokenResponse | 401, 403 |
| POST | `/auth/refresh` | @PermitAll | RefreshRequest | 200 TokenResponse | 401 |
| POST | `/auth/logout` | @Authenticated | RefreshRequest | 204 | — |
| POST | `/auth/recuperar` | @PermitAll | RecuperarPasswordRequest | 202 always | — |
| POST | `/auth/restablecer` | @PermitAll | RestablecerPasswordRequest | 204 | 400, 410 |
| POST | `/auth/aceptar-invitacion` | @PermitAll | AceptarInvitacionRequest | 204 | 400, 410 |
| GET | `/perfil` | USUARIO+SUPER_ADMIN | — | 200 PerfilResponse | — |
| PUT | `/perfil` | USUARIO+SUPER_ADMIN | PerfilActualizarRequest | 200 PerfilResponse | 400 |
| PUT | `/perfil/password` | USUARIO+SUPER_ADMIN | PasswordCambiarRequest | 204 | 400, 401 |

### 10 — Write `GlobalExceptionMapper` + `ErrorPayload`

```java
public record ErrorPayload(String codigo, String mensaje) {}

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    @Override public Response toResponse(Throwable t) {
        if (t instanceof WebApplicationException wae) {
            // If wae.getResponse().getEntity() is already an ErrorPayload, pass through.
            // Else wrap: derive codigo from the status.
        }
        if (t instanceof ConstraintViolationException cve) {
            // 400 with codigo "validacion" and the first violation's message.
        }
        // Fallthrough: 500 codigo "servidor" — log the throwable, don't leak it.
    }
}
```

Error codes used across the spec:
- `validacion` (400)
- `credenciales-invalidas` (401)
- `email-no-verificado` (403)
- `cuenta-desactivada` (403)
- `token-invalido-o-expirado` (410)
- `cooldown-activo` (429)
- `servidor` (500)

### 11 — Write the recording email adapter (test scope only)

Path: `src/test/java/ec/uce/propuestas/usuario/auth/RecordingEnviadorCorreo.java`.

```java
@Alternative
@Priority(10)
@ApplicationScoped
public class RecordingEnviadorCorreo implements EnviadorCorreo {
    public record Entrega(String destinatario, String tipo, String tokenRaw, Instant enviadoEn) {}
    private final List<Entrega> entregas = new CopyOnWriteArrayList<>();

    @Override public void enviarVerificacion(String email, String token, Instant expiresAt) {
        entregas.add(new Entrega(email, "verificacion", token, Instant.now()));
    }
    // ...

    public List<Entrega> entregas() { return List.copyOf(entregas); }
    public void clear() { entregas.clear(); }
}
```

Register in `beans.xml` (test resources) so the alternative activates:

```xml
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee">
  <alternatives>
    <class>ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo</class>
  </alternatives>
</beans>
```

### 12 — Write the tests

`AuthResourceIT.java`: one `@Test` per TC id from the source doc. Each
test uses RestAssured + injects `RecordingEnviadorCorreo` to fetch the
token that was "sent". Example:

```java
@QuarkusTest
class AuthResourceIT {
    @Inject RecordingEnviadorCorreo mailbox;
    @Inject DataSource ds;   // for JDBC cleanup

    @BeforeEach void reset() {
        mailbox.clear();
        // truncate usuario, refresh_token, token_usuario between tests
    }

    @Test
    void TC_P01_01_registro_verificacion_completo() {
        var req = new RegistroRequest("Ana", "ana@ex.com", "Pass1234", "Pass1234");
        given().contentType(JSON).body(req).when().post("/api/v1/auth/registro")
            .then().statusCode(201).body("emailVerificado", is(false));

        var token = mailbox.entregas().stream()
            .filter(e -> e.tipo().equals("verificacion")).findFirst().orElseThrow().tokenRaw();
        given().contentType(JSON).body(new VerificarEmailRequest(token))
            .when().post("/api/v1/auth/verificar-email")
            .then().statusCode(204);
        // then fetch user, assert emailVerificado=true, passwordHash != "Pass1234"
    }
    // TC_P01_02, TC_P01_03 (cooldown 429), TC_P01_04 (expired 410), TC_P01_05 (policy 400),
    // TC_P02_01…05, TC_P03_01…04, TC_P04_01…05
}
```

Test count: 5 + 5 + 4 + 5 = **19 integration tests** + at least 4 unit
tests in `PasswordPolicyTest` (empty, 7-char, no-digit, no-letter, valid).

**Truncate strategy**: run `TRUNCATE TABLE usuario, refresh_token,
token_usuario RESTART IDENTITY CASCADE` in `@BeforeEach`. Dev Services
gives you a fresh container per test-run, not per test — cleanup is on
you.

### 13 — Verify

```bash
./mvnw -q -DskipTests package
# expected: BUILD SUCCESS

./mvnw -q test
# expected: BUILD SUCCESS, 23 tests run, 0 failures
# (19 IT + 4 unit; may differ by ±2 if you split coverage differently)
```

Also spot-check the OpenAPI shape:

```bash
timeout 30 ./mvnw quarkus:dev &
DEV_PID=$!
sleep 20
curl -sf http://localhost:8080/q/openapi | grep -c '/auth/'
# expected: at least 9 (one per auth endpoint)
kill $DEV_PID 2>/dev/null || true
```

If Docker not available and Dev Services fails: SKIP the dev-mode check.
The Maven `test` gate is sufficient.

## Done criteria

- [ ] All source files in step 4/5/6/7/8/9/10 exist and compile.
- [ ] `./mvnw -q -DskipTests package` → BUILD SUCCESS.
- [ ] `./mvnw -q test` → BUILD SUCCESS, all TC-P01-*, TC-P02-*, TC-P03-*,
  TC-P04-* tests green.
- [ ] `application.yml` contains `smallrye.jwt.new-token.lifespan: 3600`
  and `app.auth.verificacion-ttl: PT24H`.
- [ ] `src/main/resources/META-INF/resources/publicKey.pem` and
  `privateKey.pem` exist (dev-only keys).
- [ ] `POST /auth/registro` returns 201, `POST /auth/login` on an
  unverified account returns 403 with `codigo=email-no-verificado`,
  `POST /auth/recuperar` on an unknown email returns 202.
- [ ] No token value appears in application logs during any test run
  (grep the surefire report for `"token":\s*"[A-Za-z0-9_-]{20,}"` — must
  be zero hits).

## Test plan

19 integration tests + 4 unit tests as itemized in step 12. Every TC id
from the source doc becomes exactly one `@Test`. Use one existing test
as a pattern; the test class is the sole owner of the auth
integration contract.

**Do not test bcrypt itself** — that's Quarkus/Elytron's responsibility.
Do test that `PasswordService.hash` produces a string that `verify`
accepts, and that raw password never appears in the DB.

## Maintenance note

- **Rotating dev JWT keys**: regenerate with the openssl commands in
  step 2. Any existing access token becomes invalid, but refresh tokens
  keep working (they're server-side, not JWT).
- **Production keys**: injected via `MP_JWT_VERIFY_PUBLICKEY_LOCATION` /
  `SMALLRYE_JWT_SIGN_KEY_LOCATION` env vars (already in `.env.example`).
  Never commit the prod private key.
- **Adding a role**: the `Rol` enum + the `CHECK (rol IN ('USUARIO',
  'SUPER_ADMIN'))` in the schema are the ONLY places roles are declared.
  Add there + write a migration to widen the CHECK.
- **Brevo adapter (future plan)**: implements `EnviadorCorreo`, becomes
  the `@Alternative @Priority(20)` bean in prod, config-driven activation.
- **`Anti-enumeration timing`**: if a security audit later requires
  fully constant-time login response, budget for `~ +50 ms` of dummy
  bcrypt work in the "email not found" branch — currently the plan
  does one dummy verify, which brings it close but not perfectly
  constant.
- **Rate limiting**: this plan implements only the 60 s resend cooldown.
  General rate limiting (login attempts, /recuperar spam) is deferred.
  Add via `quarkus-smallrye-fault-tolerance` or a reverse-proxy layer
  when the ops story matures.

## Escape hatches — STOP conditions

- `openssl` not on PATH → STOP; do not fall back to HS256.
- `quarkus-elytron-security-common` add breaks the pom → STOP; report.
  This is the only pom edit allowed by this plan.
- Any TC test fails to pass after two revision rounds → STOP; the plan
  or the schema has a gap. Do not "fix" the assertion to match the
  code — the assertion encodes a business rule from the source doc.
- A subagent working on this plan wants to add a UsuarioService for
  CRUD-shaped profile operations → STOP. `08-codebase-design.md §7`
  says Resource → Panache direct for CRUD.
- Any step wants you to touch `insumo/`, `apu/`, `presupuesto/`,
  `cronograma/`, `documento/` packages → STOP. Auth only.
- Any step wants to log a token value or a raw password → STOP.
  Non-negotiable per RNF-08.
