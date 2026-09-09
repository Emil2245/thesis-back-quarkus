# 037 — Parámetros del sistema y valores de referencia (P-41)

**Estado:** DONE (2026-09-08) · I-11 · P-41 / US-38 / TC-P41-01..02.

> Conserva estrictamente `GET/PUT /proyectos/parametros-sistema`
> ya implementado (lectura pública, escritura
> `@RolesAllowed("SUPER_ADMIN")`; `ParametrosSistemaEditarRequest`
> con 12 columnas + 8 rangos). Agrega **solo**:
> 1. emisión D-13 `admin.parametros_editados` en el `PUT`
>    existente (operaciones **exitosas**);
> 2. CRUD `valor_referencia` bajo `/admin/valores-referencia`
>    (tabla ya existe en V001 §2.14; **no** se siembra CAMICON);
> 3. verificación TC-P41-01 (defaults solo afectan proyectos
>    nuevos) sobre el flujo ya implementado.
>
> **`valor_referencia`:** cualquier `clave` única válida es
> aceptada siempre que `fuente` no esté blank. **No** se aplica un
> allowlist vacío pre-aprobado que bloquee escrituras (el acta 032
> decisión 14 (Sin CAMICON en seed ni en `valor_referencia`) mantiene el principio "sin CAMICON sembrado", pero
> no convierte el allowlist en una barrera de bloqueo). El
> principio "no CAMICON" se cumple **al no sembrar datos**: la
> única siembra está prohibida, no las escrituras admin.
>
> **DTO del `GET /proyectos/parametros-sistema`:** el acta 032
> selecciona el DTO canónico **`ParametrosSistemaResponse`** (D-10;
> ya no es condicional). `GET /proyectos/parametros-sistema` deja
> de exponer la entidad JPA: 037 introduce el DTO con los 12 campos
> numéricos + 8 rangos canónicos (lista exact en el plan 037) y el
> cliente consumidor (frontend, otros recursos) consume el DTO. La
> ruta canónica `/proyectos/parametros-sistema` se mantiene; **no**
> se mueve a `/admin/parametros-sistema`.
>
> Emisión D-13 `admin.parametros_editados`: solo operaciones
> **exitosas**. Las operaciones rechazadas no emiten.

## Proceso / historia / criterios

- **Proceso:** P-41.
- **Historia:** US-38.
- **Iteración:** I-11.
- **Criterios de aceptación (quality/02):**
  - **TC-P41-01:** admin cambia defaults del sistema → crear
    proyecto nuevo vs abrir uno viejo → el nuevo copia los
    defaults; el viejo intacto (DM §15).
  - **TC-P41-02:** admin →
    `PUT /admin/valores-referencia/SBU` actualiza la fila
    sembrada por V004 (200 OK; `updatedAt` cambia) **y**
    `PUT /admin/valores-referencia/<clave-nueva>` con
    `fuente` no blank inserta una fila nueva (201 Created).
    En ambos casos, el valor **nunca** entra al motor
    (v1.1 §4.5); Plan 037 usa las 4 filas V004 como estado inicial
    auditable, sin editar la migración ni ejecutar re-seed.

## Objetivo medible

Una ejecución futura debe demostrar que:

1. `GET /proyectos/parametros-sistema` sigue devolviendo la fila
   singleton (id=1) sin cambios de ruta; ahora **retorna el DTO
   canónico** `ParametrosSistemaResponse` (12 campos numéricos + 8
   rangos; lista exacta de campos más abajo) en el cuerpo de la
   respuesta. La entidad JPA ya **no** cruza la frontera REST;
2. `PUT /proyectos/parametros-sistema` con
   `ParametrosSistemaEditarRequest` (12 columnas + 8 rangos) sigue
   `@RolesAllowed("SUPER_ADMIN")`; ahora **además** emite
   `admin.parametros_editados` (solo en operaciones exitosas) con
   `detalle = { "operacion": "defaults.update",
   "camposModificados": ["iva"] }`
   consumiendo verbatim la matriz congelada por el acta 032;
3. TC-P41-01: tras `PUT`, un proyecto **nuevo** copia los nuevos
   defaults en su `ParametrosProyecto` (verificación sobre el
   flujo `POST /proyectos` ya implementado en Plan 021 +
   auto-create v1 vigente); un proyecto **viejo** mantiene sus
   `ParametrosProyecto` originales (verificación leyendo la fila
   `parametros_proyecto` directamente);
4. `GET /admin/valores-referencia` lista todas las filas con
   `Page<ValorReferenciaResponse>` (clave, valor, descripción,
   fuente, `updatedAt`); paginación canónica
   (`items,total,page,size,totalPaginas`);
5. `PUT /admin/valores-referencia/{clave}` con
   `ValorReferenciaRequest{valor, descripcion, fuente}`:
   - acepta cualquier `clave` única válida;
   - exige `fuente` no blank (400 `validacion`
     `fuente-requerida` si blank);
   - si la clave no existe → 201 (insert);
   - si existe → 200 (update);
   - **siempre** `@RolesAllowed("SUPER_ADMIN")`;
   - 400 `validacion` si `clave` es blank o excede la longitud
     máxima, o si los campos requeridos están vacíos;
6. `DELETE /admin/valores-referencia/{clave}` → 204; 404 si la
   clave no existe;
7. **nada** de `valor_referencia` entra al motor de cálculo
   (TC-P41-02): tests confirman que cambiar un valor
   referencial no modifica ningún cálculo; el motor opera solo
   sobre `parametros_sistema` y `parametros_proyecto`;
8. **sin CAMICON:** no se siembra ningún dato. La ausencia de
   allowlist cerrado **no** es una barrera para escrituras
   admin (cualquier clave única con fuente no blank se acepta);
   la regla "sin CAMICON" se cumple **al no sembrar datos**;
9. emisión D-13 `admin.parametros_editados` (solo operaciones
   exitosas), consumiendo verbatim la matriz congelada por el
   acta 032 (claves permitidas exactas: `operacion`,
   `camposModificados`, `clave`; sin elipsis, sin claves
   dinámicas top-level, sin valores `previa`/`nueva`, sin PII):
   - en `PUT /proyectos/parametros-sistema`: `detalle = {
     "operacion": "defaults.update",
     "camposModificados": ["iva"] }`
     con los nombres canónicos de los campos que **realmente
     cambiaron** (array estable; ningún par `previa`/`nueva`; el
     diff vive en el caller admin, no en el log);
   - en `PUT /admin/valores-referencia/{clave}` (insert o
     update): `detalle = { "operacion":
     "valor_referencia.insert|valor_referencia.update",
     "clave": "<clave>" }`;
   - en `DELETE /admin/valores-referencia/{clave}`: `detalle = {
     "operacion": "valor_referencia.delete",
     "clave": "<clave>" }`.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes (en particular 10 y 12: ruta canónica; sin CAMICON sembrado). | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo. | Habilita emisión D-13. |
| G2 — `ParametrosSistema`/`ParametrosProyecto` DONE | Singleton `id=1` (V001 §2.5); `PUT` con `@RolesAllowed("SUPER_ADMIN")` y validación de rangos `min ≤ max` ∈ `[0, 1]` ya implementados; `ProyectoService.crear` copia defaults. | Habilita TC-P41-01 sin reescritura. |
| G3 — `valor_referencia` tabla | V001 §2.14 (PK `clave`, columnas `valor/descripcion/fuente/updated_at`). | Habilita CRUD sin migración. |
| G4 — DM §15 | Defaults del sistema solo afectan proyectos nuevos. | Habilita TC-P41-01 (ya implementado). |
| G5 — v1.1 §4.5 | `valor_referencia` es informativo; nunca entra al motor. | Habilita TC-P41-02. |
| G6 — cierre | Focales verdes; regresión `proyecto.*` verde; emisión D-13 verde. | Evidencia medible. |

`STOP-037-MOTOR-VALORREFERENCIA` se activa si una prueba
intenta usar `valor_referencia` dentro de un cálculo. Reabrir.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 10, 12).
- `plans/panel-admin/033-log-actividad-base.md` (API de emisión).
- [`docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md`](../../docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md)
  (estilo del helper `ProblemaException.conflicto` para errores
  tipados; reuso del patrón admin).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H/P-41
  y §J/D-13.
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9
  filas P-41.
- `../../../thesis-docs/plan/architecture/06-database-schema.md`
  §2.5 (`parametros_sistema`, `parametros_proyecto`) y §2.14
  (`valor_referencia`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md`
  TC-P41-01..02.
- `src/main/java/ec/uce/propuestas/proyecto/entity/ParametrosSistema.java`
  y `entity/ParametrosProyecto.java`.
- `src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java`
  (especialmente `actualizarSistema(...)` y
  `validarParesDeRangos(...)`).
- `src/main/java/ec/uce/propuestas/proyecto/resource/ProyectoResource.java`
  (rutas `GET/PUT /proyectos/parametros-sistema` ya implementadas).
- `src/main/java/ec/uce/propuestas/proyecto/repository/ParametrosSistemaRepository.java`
  y `ParametrosProyectoRepository.java`.
- `src/main/resources/db/migration/V001__baseline.sql` §2.5 y
  §2.14 (DDL verbatim).

## Estado inicial esperado

- `ParametrosSistema` y `ParametrosProyecto` DONE; suite
  `proyecto.*` verde.
- `valor_referencia` contiene las cuatro filas informativas sembradas por V004;
  el fixture las verifica antes de probar update, delete y creación por upsert.
- `ParametrosSistemaEditarRequest` ya valida los 12 + 8 campos
  con `@DecimalMin/@DecimalMax` y el servicio valida pares.
- `ProyectoService.crear(...)` copia defaults de `ParametrosSistema`
  al crear un nuevo proyecto (auto-create v1 vigente; Plan 021).
- Sin recurso admin `/admin/valores-referencia`.
- Sin emisión D-13 para P-41.

## Decisiones locked adicionales (037)

Se suman a las anteriores; no las contradicen:

44. **Ruta canónica preservada:** `GET/PUT /proyectos/parametros-sistema`
    **no** se mueve a `/admin/parametros-sistema`. La ruta es pública
    para lectura (USUARIO) y `SUPER_ADMIN` para escritura. Esta es
    la decisión 10 de 032.
45. **DTO de `GET /proyectos/parametros-sistema`:** el acta 032
    selecciona el DTO canónico `ParametrosSistemaResponse` (D-10;
    ya no es condicional; `STOP-032-DTO-PARAMETROS` queda CLOSED).
    037 introduce el DTO con los 12 campos numéricos + 8 rangos
    canónicos y deja de exponer la entidad JPA. La ruta canónica
    `/proyectos/parametros-sistema` se mantiene; **no** se mueve
    a `/admin/parametros-sistema`.
46. **`valor_referencia` — escritura abierta con `fuente` no
    blank:** cualquier `clave` única válida se acepta en
    `PUT /admin/valores-referencia/{clave}` siempre que `fuente`
    no esté blank (longitud confirmada contra la columna). **No**
    hay allowlist cerrado de claves pre-aprobadas que bloquee
    escrituras. La regla "sin CAMICON" (decisión 14 de 032) se
    cumple **al no sembrar datos**, no al no permitir escrituras
    admin. Cualquier futura restricción de claves se hace vía
    `STOP-032-CAMICON` con un acta humana explícita, no en este
    plan.
47. **Emisión D-13 (037) — solo operaciones exitosas:** consume
    verbatim la matriz canónica publicada por el acta 032 (decisión 2);
    037 **no** redefine, **no** agrega ni **no** amplía claves. Ver
    objetivo 9. El detalle es **diferencial** para
    `PUT /proyectos/parametros-sistema` (solo campos cambiados;
    `previo → nuevo`); evita inflar el log con 22 columnas cuando
    solo cambia 1. `entidadId` top-level es `null` tanto para
    `parametros_sistema` como para `valor_referencia`, porque ninguno
    expone UUIDv7 público; `entidad` distingue el agregado y
    `detalle.clave` identifica el valor de referencia cuando aplica.
48. **No cambio en `validarParesDeRangos`:** la validación `min ≤
    max` por par ya existe. 037 no la reabre.
49. **`PUT /proyectos/parametros-sistema` con diff mínimo:** la
    emisión D-13 usa la comparación `previo.compareTo(nuevo) != 0`
    (mismo helper `cambioNumerico` que ya existe en
    `ParametrosProyectoService.actualizar`). Para campos no
    numéricos (`moneda`), comparación `Objects.equals`.

## Alcance

### Incluye

- Emisión D-13 `admin.parametros_editados` (solo operaciones
  exitosas) desde `ParametrosProyectoService.actualizarSistema(...)`
  con `detalle = { "operacion": "defaults.update",
  "camposModificados": ["iva"] }` consumiendo
  verbatim la matriz congelada por el acta 032.
- DTO canónico `ParametrosSistemaResponse` introducido en
  `GET /proyectos/parametros-sistema` (12 campos numéricos + 8
  rangos; ya no es condicional — acta 032 D-10 selecciona el DTO
  y `STOP-032-DTO-PARAMETROS` queda CLOSED). La respuesta ya no
  expone la entidad JPA.
- Recurso JAX-RS `ValorReferenciaAdminResource` con
  `@Path("/admin/valores-referencia")` y
  `@RolesAllowed("SUPER_ADMIN")`.
- DTO `ValorReferenciaResponse(clave, valor, descripcion, fuente,
  updatedAt)`.
- DTO `ValorReferenciaRequest(@NotBlank @Size(max=100) valor,
  @NotBlank descripcion (TEXT; sin tope arbitrario; no se aplica
  `@Size(max=...)` salvo que acta 032/canon fije una frontera defensible
  para el cuerpo de la request), @NotBlank @Size(max=200)
  fuente)`.
- Repositorio `ValorReferenciaRepository` (Panache) con
  `listar(page, size)`, `count()`, `findByClave(String)`,
  `upsert(ValorReferencia)`, `deleteByClave(String)`.
- Emisión D-13 `admin.parametros_editados` (solo operaciones
  exitosas) en `PUT/DELETE /admin/valores-referencia/{clave}`.
- Tests `@QuarkusTest`:
  - `ParametrosSistemaLogAuditoriaIT` (TC-P41-01 + 2 escenarios:
    PUT parcial vs vs PUT total → diff correcto).
  - `ValorReferenciaAdminResourceIT` (TC-P41-02 + 5 escenarios:
    GET, PUT insert, PUT update, DELETE, `fuente` blank → 400).
  - `ValorReferenciaNoEntraAlMotorTest`: cambiar SBU no
    modifica ningún cálculo (el agregado XML del comando del motor sigue
    mostrando los mismos GM-19/GM-20 aceptados y GM-24 omitido que en la
    línea base; sin desviación nueva).

### No incluye

- Mover `GET/PUT /proyectos/parametros-sistema` (decisión 44).
- Siembra CAMICON o cualquier otro valor sin licencia.
- Allowlist cerrado de claves pre-aprobadas (decisión 46).
- Cambiar la validación de rangos.
- Crear un endpoint para "renombrar clave" (la `clave` es PK; si
  se quiere renombrar, se borra y se crea).
- Re-seed o modificación de V001/V004; las cuatro filas son estado
  inicial y luego quedan sujetas al CRUD admin de P-41.
- Migración nueva.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminResource.java` | `@Path("/admin/valores-referencia")` + `@RolesAllowed("SUPER_ADMIN")`. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminService.java` | Orquesta repo + `LogActividadService`. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/dto/ParametrosSistemaResponse.java` | DTO canónico obligatorio (acta 032 D-10; `STOP-032-DTO-PARAMETROS` CLOSED): 12 campos numéricos + 8 rangos; sustituye a la entidad JPA en `GET /proyectos/parametros-sistema`. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/dto/ValorReferenciaResponse.java` | Record canónico. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/dto/ValorReferenciaRequest.java` | Record canónico. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/repository/ValorReferenciaRepository.java` | Panache; CRUD. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java` | Inyectar `LogActividadService`; emitir D-13 diferencial en `actualizarSistema(...)`. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/resource/ProyectoResource.java` | Adaptar `GET /proyectos/parametros-sistema` para devolver `ParametrosSistemaResponse` (no la entidad JPA). La ruta no cambia; solo cambia el tipo del cuerpo. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminResourceIT.java` | TC-P41-02 + 5 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaNoEntraAlMotorTest.java` | TC-P41-02: motor no lee valor_referencia. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/service/ParametrosSistemaLogAuditoriaIT.java` | TC-P41-01 + diff. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 037 `DONE`. |
| Modificar (opcional) | `api/bruno/12-admin/TC-12-P41-01..02.bru` | En 040. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**`, `recalculo/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/proyecto/resource/ParametrosProyectoResource.java` (no-admin) | STOP — ruta canónica intacta. |

## Contrato REST

```
GET    /api/v1/proyectos/parametros-sistema          # público (USUARIO)
PUT    /api/v1/proyectos/parametros-sistema          # SUPER_ADMIN (existente)
GET    /api/v1/admin/valores-referencia?q=&page=&size=
PUT    /api/v1/admin/valores-referencia/{clave}
DELETE /api/v1/admin/valores-referencia/{clave}
```

### Errores

| Código | Tipo | Cuándo |
|---|---|---|
| 400 `validacion` | `rango-invalido` | `min > max` o fuera de `[0,1]` (existente). |
| 400 `validacion` | `clave-excedida` | `clave` excede la longitud máxima de la columna PK. |
| 400 `validacion` | `valor-requerido` / `descripcion-requerida` / `fuente-requerida` | blank o > límite. |
| 400 `validacion` | `tamano-pagina-invalido` | `size > 200` o `size < 1`. |
| 404 `no-encontrado` | — | `{clave}` no existe (DELETE). |
| 403 `forbidden` | — | caller `USUARIO` en `/admin/*`. |

> **No hay código `fuente-requerida-no-pre-aprobada`:** cualquier
> clave única con `fuente` no blank se acepta. La regla "sin CAMICON"
> se cumple al no sembrar datos.

## Secuencia TDD (estricta)

### RED

1. `ParametrosSistemaLogAuditoriaIT`:
   - `PUT /proyectos/parametros-sistema` con un cambio en
     `porcentajeHerramientaMenor` (0.05 → 0.07) y otro en
     `moneda` ("USD" → "USD" sin cambio) → 200; 1 fila
     `log_actividad` con
     `detalle.operacion = "defaults.update"` y
     `detalle.camposModificados = ["porcentajeHerramientaMenor"]`
     (array estable; **sin** claves top-level dinámicas;
     **sin** pares `previa`/`nueva`; `moneda` no aparece en el
     array porque no cambió).
   - TC-P41-01: tras el `PUT`, crear un proyecto nuevo
     (`POST /proyectos`) → su `ParametrosProyecto` copia
     `porcentajeHerramientaMenor=0.07`. Un proyecto viejo
     creado **antes** del `PUT` mantiene `0.0500`.
2. `ValorReferenciaAdminResourceIT`:
   - `GET` → 0 filas (vacío por defecto).
   - `PUT /admin/valores-referencia/SU` con `valor`,
     `descripcion`, `fuente` → 201; fila en `valor_referencia`.
   - `PUT /admin/valores-referencia/SU` otra vez → 200
     (update); `updatedAt` cambia.
   - `DELETE /admin/valores-referencia/SU` → 204; 0 filas.
   - `DELETE /admin/valores-referencia/SU` otra vez → 404.
   - `PUT` con `clave` excediendo longitud máxima → 400
     `clave-excedida`.
   - `PUT` con `fuente` blank → 400 `fuente-requerida`.
   - Caller `USUARIO` → 403 en cualquier endpoint.
3. `ValorReferenciaNoEntraAlMotorTest`:
   - Carga una fila `valor_referencia` (con la clave `SU`
     pre-aprobada por test).
   - Corre el set de GM-01..GM-18 + GM-22/23/25 con
     `valor_referencia.SU=999.99`. Resultado: mismas
     desviaciones que sin la (Plan 014 + Plan 015,
     comparar contra el agregado XML del comando del motor: GM-19/GM-20 aceptados
    y GM-24 omitido según la línea base; sin desviación nueva).
     Si alguna GM falla con desviación nueva, 037 aborta y
     reabre.

### GREEN

Construir DTOs, servicio, recurso, repo. Inyectar
`LogActividadService` en `ParametrosProyectoService`. Reusar el
helper `cambioNumerico` ya existente.

### TRIANGULATE

- `PUT` con cambio solo en `iva` (decimal); `camposModificados`
  contiene exactamente `["iva"]`.
- `PUT` con `porcentajeIndirecto` `null → 0.10`;
  `camposModificados` contiene `["porcentajeIndirecto"]` (un
  cambio `null → valor` cuenta como cambio; el detalle sigue
  siendo el array de nombres canónicos).
- `PUT` con dos cambios (`porcentajeHerramientaMenor` y
  `porcentajeIndirecto`) → `camposModificados` contiene
  `["porcentajeHerramientaMenor", "porcentajeIndirecto"]` en
  orden determinista (mismo orden de columnas V001 §2.5).
- `PUT /admin/valores-referencia` con la misma `clave` pero
  `fuente` distinta → 200 (update); la `fuente` cambia (es
  upsert, no diff).
- Concurrencia: dos `PUT /proyectos/parametros-sistema`
  simultáneos sobre la misma fila singleton → lock pesimista de
  fila; el segundo ve la fila actualizada por el primero.

### REFACTOR

- Extraer el helper `camposModificadosDe(previo, nuevo) →
  List<String>` si la duplicación se vuelve molesta (>20 líneas).
  037 lo deja inline.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| TC-P41-01 nuevos defaults | Proyecto nuevo copia defaults; viejo intacto. |
| TC-P41-02 update SBU sembrado | 200; `updatedAt` cambia; el fixture confirma primero las 4 filas V004 (`SBU`, `APORTE_PATRONAL`, `FAS`, `HORAS_OPERACION_ANUAL`) y el update no ejecuta re-seed. |
| TC-P41-02 insert clave nueva | 201; valor guardado; **no entra al motor**. |
| TC-P41-02 delete SBU | 204. |
| TC-P41-02 delete SBU segunda vez | 404. |
| TC-P41-02 fuente blank | 400 `fuente-requerida`. |
| Emisión D-13 parámetros | `detalle.operacion == "defaults.update"` y `detalle.camposModificados` solo con los nombres canónicos de campos cambiados (sin `previa`/`nueva`, sin PII). |
| Emisión D-13 valores_referencia | `operacion=insert/update/delete`. |
| USUARIO 403 | `/admin/*` responde 403. |
| Motor intacto | GM-19/GM-20 aceptados y GM-24 omitido según línea base; sin desviación nueva. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.proyecto.admin.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.service.ParametrosSistemaLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.admin.ValorReferenciaNoEntraAlMotorTest' \
  -Dquarkus.http.test-port=0 --console=plain

# Regresión proyecto (defaults + rangos + auto-create v1 vigente).
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Motor: la suite agrega GM-19/GM-20 aceptados y GM-24 omitido según la línea base.
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Sin migración nueva.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.

# Ruta canónica intacta.
grep -RIn '/proyectos/parametros-sistema' \
  src/main/java/ec/uce/propuestas/proyecto/resource/
# esperado: sigue existiendo en `ProyectoResource.java`;
#           NO existe `/admin/parametros-sistema`.
! grep -RIn '/admin/parametros-sistema' \
  src/main/java/ec/uce/propuestas/

# Forma canónica Page<T>.
grep -RInE 'items,|total,|page,|size,|totalPaginas' \
  src/main/java/ec/uce/propuestas/proyecto/admin/

# Sin CAMICON sembrado en código admin ni en migración.
! grep -RInE 'CAMICON' \
  src/main/java/ec/uce/propuestas/proyecto/admin/ \
  src/main/resources/db/migration/
# esperado: 0 matches (o solo referencias a la regla
    #           "no CAMICON sembrado").
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (037)

- [x] `GET/PUT /proyectos/parametros-sistema` conserva la ruta canónica y
      retorna `ParametrosSistemaResponse`, no la entidad JPA.
- [x] DTO canónico completo introducido; `STOP-032-DTO-PARAMETROS` CLOSED.
- [x] `admin.parametros_editados` usa diff estable para defaults y
      `operacion` + `clave` para valores de referencia; rechazos y no-op no emiten.
- [x] `GET/PUT/DELETE /admin/valores-referencia` protegido con
      `@RolesAllowed("SUPER_ADMIN")`.
- [x] TC-P41-01 y TC-P41-02 verdes; defaults se materializan solo en proyectos nuevos.
- [x] Cualquier clave única con `fuente` no blank se acepta; blank retorna 400.
- [x] Cero siembra de CAMICON; las cuatro filas V004 se preservan como estado inicial.
- [x] Ninguna migración nueva; `motor/` y `recalculo/` intactos.
- [x] `git diff --check` limpio.

### Evidencia medida de cierre

- RED focal: **7 tests = 6 failures esperados + 1 pass estructural**, sin errors/skips.
- GREEN focal Plan 037: **7/7 pass**.
- Regresión `ec.uce.propuestas.proyecto.*`: **25/25 pass**.
- Regresión focal de fixtures ajustados (`ResumenComponentesResourceIT` y
  `RubroResourceIT`): **BUILD SUCCESSFUL**.
- Suite completa fresca: **732 = 729 pass + 2 failures aceptados**
  (GM-19/GM-20) **+ 1 skipped** (GM-24), **0 errors**.
- `spotlessCheck`, `build -x test` y `git diff --check`: PASS.
- Sin cambios en migraciones, `motor/` ni `recalculo/`; sin commit.

## Handoff al siguiente plan

Cuando 037 cierre, el orquestador puede iniciar **038**
(instrumentación D-13 identidad y catálogos). 038 **no** duplica
los eventos emitidos por 034–037: cubre `auth.*`, `proyecto.*`,
`insumo.*`, `apu.*`, `base.copiada_a_proyecto` (ver
`EventoLogActividad` en 033 y la tabla de cobertura de eventos
del acta 032 decisión 18).