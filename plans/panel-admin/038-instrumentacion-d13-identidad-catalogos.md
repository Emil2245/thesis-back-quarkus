# 038 — Instrumentación D-13: identidad y catálogos

**Estado:** TODO · I-11 · segunda ola de emisores D-13.

> Cubre los eventos D-13 de los módulos de **identidad** y
> **catálogos** que 032 acta (decisión 18) asigna a este plan:
> `auth.login`, `auth.logout`, `auth.registro`,
> `auth.password_cambiada` (perfil + reset),
> `proyecto.creado|editado|eliminado|duplicado`,
> `insumo.creado|editado|eliminado`,
> `insumos.import_csv`, `base.copiada_a_proyecto`,
> `apu.creado|editado|eliminado`, y `usuario.activado` con origen
> `invitacion`. **Excluye** explícitamente los eventos ya
> emitidos por 034 (`usuario.invitado|activado|desactivado`),
> 035 (`admin.base_editada`), 036 (`admin.plantilla_editada`)
> y 037 (`admin.parametros_editados`). Emite siempre dentro de
> la misma transacción exterior (sin ghost events) a través
> de `LogActividadService` (033).
>
> **Solo operaciones exitosas emiten.** No hay emisor de failure;
> las operaciones rechazadas (401/403/404/409) **no** producen
> fila `log_actividad`. Los 4 nombres legacy de V004
> (`base.insumos.copiada`, `rubro.creado`, `cronograma.creado`,
> `presupuesto.vigente_marcado`) **no** entran al enum
> `EventoLogActividad` runtime (decisión 17 de 032).

## Proceso / historia / criterios

- **Proceso:** transversal (instrumentación).
- **Historia:** US-39 (cobertura completa; verificación final en
  040).
- **Iteración:** I-11.
- **Criterio de cobertura:** para cada uno de los eventos
  listados arriba en la decisión 18 de 032, existe un
  `@QuarkusTest` focal que ejecuta el flujo público y asserta una
  fila `log_actividad` con la clave exacta del catálogo D-13 y
  `detalle` sin PII ni secretos.

## Objetivo medible

Una ejecución futura debe demostrar que:

1. cada uno de los eventos asignados a 038 por el acta 032 se
   emite **exactamente una vez** por la mutación correspondiente
   (operación exitosa), dentro de la misma `@Transactional`
   exterior del servicio público;
2. ningún emisor añade una clave de evento fuera del catálogo
   cerrado (verificación con grep en el código de 038);
3. ningún emisor añade PII ni secretos al `detalle` JSONB
   (verificación con la regex TC-P42-02);
4. los emisores **no** se insertan dentro de `motor/` ni de
   `recalculo/` (regla de 032 decisión 13; verificación con
   `git diff --stat` sobre esos paquetes);
5. los emisores **no** se insertan dentro de los servicios
   que ya emiten por 034–037 (regla de no-duplicación);
6. cada servicio público (Auth, Proyecto, Insumo, Apu, CopiaBase)
   sigue pasando su `@QuarkusTest` existente (regresión cero);
7. cuando un servicio público no abre `@Transactional` (caso
   conocido: `AuthService.login`, `AuthService.aceptarInvitacion`,
   `AuthService.registrar`, `AuthService.logout`,
   `AuthService.cambiarPassword`), 038 ajusta el servicio para
   establecer una transacción exterior explícita antes de la
   emisión, y `LogActividadService.emitir(...)` (con
   `TxType.MANDATORY`) opera bajo esa transacción.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes. | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo; `MANDATORY` verificado; `EventoLogActividad` con 26 entradas. | Habilita emisión D-13. |
| G2 — 034..037 cerrados | Los eventos cubiertos por 034–037 ya emiten. | Evita duplicación. |
| G3 — sin migración | V001–V009 intactos. | Habilita 038 sin DDL. |
| G4 — `RecordingEnviadorCorreo` | Helper de test para captura de correos. | Habilita test de `auth.*` sin red. |
| G5 — UUIDv7 cerrado | `UuidV7.parse` en frontera; PK/FK BIGINT internas. | Habilita `detalle` con UUIDv7 sin BIGINT. |
| G6 — cierre | tests focales verdes; regresión por módulo verde; cobertura TC-P42-01..02 verde. | Evidencia medible. |

`STOP-038-EVENTO-FUERA-CATALOGO` se activa si una prueba exige un
evento que no está en los 26 del canon. Reabrir 032.

`STOP-038-DUPLICACION` se activa si 038 inserta un emisor para
un evento ya emitido por 034–037. Reabrir el plan responsable.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 1, 13, 17, 18, 19).
- `plans/panel-admin/033-log-actividad-base.md` (API de
  `LogActividadService.emitir`; enum `EventoLogActividad`).
- `plans/panel-admin/034-gestion-usuarios-invitaciones.md`
  (eventos ya emitidos por 034: `usuario.invitado`,
  `usuario.activado` origen `admin`, `usuario.desactivado`).
- `plans/panel-admin/035-bases-centrales-cierre.md` (evento ya
  emitido por 035: `admin.base_editada`).
- `plans/panel-admin/036-plantillas-apu-sistema.md` (evento ya
  emitido por 036: `admin.plantilla_editada`).
- `plans/panel-admin/037-parametros-valores-referencia.md`
  (evento ya emitido por 037: `admin.parametros_editados`).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §J
  (D-13 catálogo verbatim).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md`
  TC-P01..05 (auth), TC-P04 (perfil), TC-P05..P11 (proyecto),
  TC-P13..P18 (insumo), TC-P19..P23 (APU).
- `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
  (login, logout, registro, password cambiada,
  aceptarInvitacion).
- `src/main/java/ec/uce/propuestas/usuario/auth/TokenService.java`
  y `TipoToken.java`.
- `src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java`
  y `service/FirmanteService.java`.
- `src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java`
  (no se re-emite aquí; 037 ya emite).
- `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java`,
  `service/BaseInsumosService.java`,
  `service/CopiaBaseService.java`,
  `service/importacion/ImportacionInsumoService.java`.
- `src/main/java/ec/uce/propuestas/apu/service/ApuCrudService.java`,
  `service/ApuDuplicarService.java`.

## Estado inicial esperado

- `LogActividadService` operativo con `emitir` (`MANDATORY`).
- Eventos ya cubiertos: 3 de 034, 1 de 035, 1 de 036, 1 de 037.
- `motor/`, `recalculo/`, V001–V009 intactos.
- `auth`, `proyecto`, `insumo`, `apu` servicios **no** emiten
  todavía (gap previsto).

## Decisiones locked adicionales (038)

Se suman a las anteriores; no las contradicen:

50. **Mapa evento → servicio público (038):** los métodos listados
    son los **actualmente presentes en el código** (verificados
    contra `src/main/java/`); 038 **no inventa firmas**. Si el acta
    032 no cierra el `STOP-032-P09-DUPLICAR`, este plan **no**
    emite `proyecto.duplicado` (ver regla "cobertura runtime" al
    final de esta decisión).

    | Evento | Servicio público | Método (signatura real verificada) |
    |---|---|---|
    | `auth.login` | `AuthService` | `login(LoginRequest req)` |
    | `auth.logout` | `AuthService` | `logout(String rawRefreshToken)` |
    | `auth.registro` | `AuthService` | `registrar(RegistroRequest req)` |
    | `auth.password_cambiada` (perfil) | `AuthService` | `cambiarPassword(String email, PasswordCambiarRequest req)` con `detalle.origen = "perfil"` |
    | `auth.password_cambiada` (reset) | `AuthService` | `restablecerPassword(RestablecerPasswordRequest req)` con `detalle.origen = "reset"` |
    | `usuario.activado` (origen `invitacion`) | `AuthService` | `aceptarInvitacion(AceptarInvitacionRequest req)` con `detalle.origen = "invitacion"` |
    | `proyecto.creado` | `ProyectoService` | `crear(Long usuarioId, ProyectoCrearRequest req)` |
    | `proyecto.editado` | `ProyectoService` | `actualizar(Long usuarioId, UUID publicId, ProyectoEditarRequest req)` |
    | `proyecto.eliminado` | `ProyectoService` | `eliminar(Long usuarioId, UUID publicId)` |
    | `proyecto.duplicado` | `ProyectoService` | **`duplicar(...)` (P-09) NO EXISTE en el código actual; no se inventa. Gated por `STOP-032-P09-DUPLICAR` (acta 032): o se implementa el seam canónico o el evento se difiere como "no producer yet"**. |
    | `insumo.creado` | `InsumoCrudService` | `crear(...)` (en base PROYECTO) |
    | `insumo.editado` | `InsumoCrudService` | `actualizar(...)` |
    | `insumo.eliminado` | `InsumoCrudService` | `eliminar(...)` |
    | `insumos.import_csv` | `ImportacionInsumoService` | `importarCsv(Long baseId, byte[] contenido)` |
    | `base.copiada_a_proyecto` | `CopiaBaseService` | `copiar(CopiarBaseRequest req, Long callerUsuarioId)` |
    | `apu.creado` | `ApuCrudService` | `crear(...)` |
    | `apu.editado` | `ApuCrudService` | `actualizar(...)` |
    | `apu.eliminado` | `ApuCrudService` | `eliminar(...)` |

    **Cobertura runtime efectiva:** 038 emite **16 nombres únicos
    del catálogo D-13** **más** un camino emisor adicional para
    `usuario.activado` con origen `invitacion` (camino compartido
    con 034, que emite el mismo nombre con origen `admin`). El
    evento `proyecto.duplicado` **no entra a la cobertura runtime
    efectiva de 038** hasta que `STOP-032-P09-DUPLICAR` se cierre
    (ver abajo); el nombre canónico se conserva en el enum
    `EventoLogActividad` y en la matriz del test de cobertura 040
    para preservar la completitud del catálogo cerrado.

    **STOP-038-P09-PRODUCER:** este plan **no puede** afirmar
    cobertura runtime completa de los 26 eventos si el acta 032 no
    cierra `STOP-032-P09-DUPLICAR`. El completion checklist de 038
    declara explícitamente el estado de `proyecto.duplicado`
    (`producer implemented` / `no producer yet`) según la decisión
    del acta. 040 refleja esa declaración sin fabricación.

    Si un método distinto al listado existe, 038 documenta el
    método real contra el código (no inventa firmas).

51. **Detalle por evento (canónico):** 038 consume verbatim la matriz
    canónica publicada por el acta 032 (decisión 2); **no** redefine,
    **no** agrega ni **no** amplía claves. Los identificadores
    públicos de entidad (UUIDv7) prefieren vivir en la columna
    top-level `entidadId` de `LogActividadResponse`; los que aparecen
    en `detalle` son los estrictamente necesarios para distinguir
    entidades múltiples en el mismo evento. El cuadro local
    siguiente se cita solo a efectos de implementación; la autoridad
    absoluta es la matriz del acta 032.

    | Evento | Claves `detalle` permitidas | `entidadId` top-level |
    |---|---|---|
    | `auth.login` | `{ "resultado": "ok" }` (sin `ipOrigen` ni PII) | `null` |
    | `auth.logout` | `{ "resultado": "ok" }` (sin `ipOrigen` ni PII) | `null` |
    | `auth.registro` | `{ "usuarioId": <UUIDv7> }` (el correo **no** entra al detalle; vive solo en `EnviadorCorreo`) | `null` |
    | `auth.password_cambiada` (perfil) | `{ "origen": "perfil" }` | `null` |
    | `auth.password_cambiada` (reset) | `{ "origen": "reset" }` | `null` |
    | `usuario.activado` (origen `invitacion`) | `{ "origen": "invitacion" }` | UUIDv7 del `Usuario` activado |
    | `proyecto.creado` | `{}` (el `nombre` del proyecto es texto libre del usuario y **no** entra al detalle; vive en `ProyectoResponse.nombre`) | UUIDv7 del `Proyecto` |
    | `proyecto.editado` | `{}` | UUIDv7 del `Proyecto` |
    | `proyecto.eliminado` | `{}` | UUIDv7 del `Proyecto` |
    | `proyecto.duplicado` | `{ "proyectoOrigenId": <UUIDv7>, "proyectoDuplicadoId": <UUIDv7> }` | UUIDv7 del proyecto duplicado (gated por `STOP-032-P09-DUPLICAR`) |
    | `insumo.creado` | `{ "codigoInsumo": "<texto>", "tipo": "EQUIPO\|MANO_OBRA\|MATERIAL\|TRANSPORTE" }` | UUIDv7 del `Insumo` |
    | `insumo.editado` | `{}` | UUIDv7 del `Insumo` |
    | `insumo.eliminado` | `{}` | UUIDv7 del `Insumo` |
    | `insumos.import_csv` | `{ "creados": <int>, "actualizados": <int>, "errores": <int> }` | UUIDv7 de la `BaseInsumos` destino |
    | `base.copiada_a_proyecto` | `{ "baseOrigenId": <UUIDv7>, "proyectoDestinoId": <UUIDv7>, "cantidadInsumos": <int>, "cantidadOmitidos": <int> }` | UUIDv7 del `Insumo` o de la `BaseInsumos` resultante (server-authored) |
    | `apu.creado` | `{}` | UUIDv7 del `Apu` |
    | `apu.editado` | `{}` | UUIDv7 del `Apu` |
    | `apu.eliminado` | `{}` | UUIDv7 del `Apu` |

    `LogActividadDetalleValidator.validar(evento, detalle)` es un
    validador emisor-interno: como las claves de `detalle` son
    siempre server-authored (cruzan al JSONB sin pasar por REST),
    una clave ajena al conjunto es un **error de programación** y
    el validador levanta `IllegalStateException` (clave no
    permitida) o `IllegalArgumentException` (evento desconocido).
    La transacción exterior hace rollback y no se persiste el
    evento (consistente con la decisión 12 de 032). **No** se
    devuelve 400 al cliente: el detalle nunca cruza la frontera REST.
    Los únicos 400 sobre el filtro `evento=` de `GET /admin/logs`
    son `evento-formato-invalido` (charset inseguro) y `evento-largo`
    (> 60 caracteres); un valor con charset y longitud válidos
    pero ausente del catálogo devuelve 200 con página vacía, no
    400. El código `evento-desconocido` **no aplica** al filtro
    REST (decisión 26 de 033).

52. **Sin emisor en `motor/` ni `recalculo/`:** los eventos
    `cronograma.editado`, `presupuesto.version_creada`,
    `presupuesto.version_activada`, `documento.exportado` los
    emite 039 en sus servicios públicos (`CronogramaService`,
    `VersionadoService`, `DocumentoResource`). 038 **no**
    toca esos paquetes.

53. **Concurrencia:** la inserción en `log_actividad` participa
    de la tx exterior (`MANDATORY`). Si dos mutaciones
    concurrentes sobre el mismo recurso emiten a la vez, no
    hay colisión: la PK es `BIGINT IDENTITY` (V001 §2.15) y
    la tabla no tiene índice único sobre `(evento, entidad,
    entidad_id)`. El el test `@QuarkusTest` con dos invocaciones
    secuenciales verifica que `count(*)` aumenta en 2.

## Alcance

### Incluye

- Modificación de los servicios listados en la tabla de la
  decisión 50 para emitir el evento D-13 correspondiente **al
  final del método público** (cuando el commit es inminente,
  antes del return), solo en operaciones exitosas.
- Cuando el servicio público no abre `@Transactional` (caso
  conocido en `AuthService`), 038 ajusta el servicio para abrir
  la transacción exterior explícita antes de la emisión, con un
  test focal `MANDATORY-sin-tx-lanza-IllegalStateException` que
  pasa a verde tras el ajuste.
- Tests `@QuarkusTest`:
  - `AuthServiceLogAuditoriaIT` (5 escenarios: login ok, logout,
    registro, password cambiada desde perfil, password cambiada
    desde reset, `usuario.activado` con origen `invitacion`).
  - `ProyectoServiceLogAuditoriaIT` (4 escenarios).
  - `InsumoCrudServiceLogAuditoriaIT` (3 escenarios).
  - `ImportacionInsumoServiceLogAuditoriaIT` (1 escenario).
  - `CopiaBaseServiceLogAuditoriaIT` (1 escenario).
  - `ApuCrudServiceLogAuditoriaIT` (3 escenarios).
  - `LogActividadCoberturaD13IdentidadTest` (matriz: cada uno
    de los **16 nombres únicos + 1 camino emisor adicional**
    asignados a 038 debe tener ≥ 1 fila tras
    ejecutar su `@QuarkusTest` correspondiente).
- Validación de detalle (`LogActividadDetalleValidator`) con
  el set de claves permitidas por evento (decisión 51).

### No incluye

- Emisores para los eventos ya cubiertos por 034–037.
- Cambios en `auth/AuthResource`, `proyecto/ProyectoResource`,
  `insumo/InsumoResource`, `apu/ApuResource` (recursos); los
  emisores viven en los servicios.
- Cambios en `motor/`, `recalculo/`, `presupuesto/`,
  `cronograma/`, `documento/` (esos eventos los emite 039).
- Renombrar eventos existentes.
- Reordenar la lista D-13.
- Agregar nuevos eventos.
- Aceptar los 4 nombres legacy V004 al enum runtime.
- Migración nueva.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java` | Inyectar `LogActividadService`; emitir los eventos asignados (decisión 50). Ajustar la transacción exterior si no abre `@Transactional`. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java` | Inyectar `LogActividadService`; emitir 4 eventos. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java` | Inyectar `LogActividadService`; emitir 3 eventos. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/importacion/ImportacionInsumoService.java` | Inyectar `LogActividadService`; emitir 1 evento. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/CopiaBaseService.java` | Inyectar `LogActividadService`; emitir 1 evento. |
| Modificar | `src/main/java/ec/uce/propuestas/apu/service/ApuCrudService.java` | Inyectar `LogActividadService`; emitir 3 eventos. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/audit/EventoLogActividad.java` | STOP — el mapa `detallesEsperados()` está **congelado** desde 033; 038 **no** lo amplía, **no** redefine claves, **no** agrega eventos al enum. Si una clave faltara, 038 reabre 032. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/audit/service/LogActividadDetalleValidator.java` | STOP — el validador fue materializado por 033; 038 **solo** lo consume (`LogActividadService.emitir(...)` ya lo invoca con `MANDATORY`). 038 **no** crea, **no** modifica, **no** reescribe el validador. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/auth/AuthServiceLogAuditoriaIT.java` | 5 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/service/ProyectoServiceLogAuditoriaIT.java` | 4 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/insumo/service/InsumoCrudServiceLogAuditoriaIT.java` | 3 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/insumo/service/ImportacionInsumoServiceLogAuditoriaIT.java` | 1 escenario. |
| Crear | `src/test/java/ec/uce/propuestas/insumo/service/CopiaBaseServiceLogAuditoriaIT.java` | 1 escenario. |
| Crear | `src/test/java/ec/uce/propuestas/apu/service/ApuCrudServiceLogAuditoriaIT.java` | 3 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/LogActividadCoberturaD13IdentidadTest.java` | Matriz de cobertura. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 038 `DONE`. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**`, `recalculo/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/presupuesto/**`, `cronograma/**`, `documento/**` | STOP — eventos son de 039. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |

## Secuencia TDD (estricta)

### RED

Para cada servicio:

1. Escribir el `@QuarkusTest` que ejecuta el flujo público y
   asserta 1 fila `log_actividad` con la clave correcta.
2. Verificar que el test **falla** porque el emisor no existe
   aún (RED).
3. Verificar la validación de `detalle`: pasar una clave fuera
   del set permitido (emisor-interno; el validador levanta `IllegalStateException` y la transacción exterior hace rollback, sin persistir el evento; consistente con la decisión 12 de 032; ver `LogActividadDetalleValidatorTest`). Esto
   se prueba en `LogActividadDetalleValidatorTest` (clave no permitida ÷ `IllegalStateException`; evento desconocido ÷ `IllegalArgumentException`; sin 400 al cliente), no en los
   tests focales.

### GREEN

Inyectar `LogActividadService` en el servicio público. Llamar
`emitir(...)` con el evento y `detalle` canónicos. Si el
servicio público no abre `@Transactional`, ajustar el método
para abrir la tx exterior antes de la emisión; el test focal
`MANDATORY-sin-tx-lanza-IllegalStateException` pasa a verde
tras el ajuste. La inyección no cambia la firma pública del
servicio.

### TRIANGULATE

- Concurrencia: dos mutaciones simultáneas (test secuencial con
  `@Order`).
- `detalle` con `null` o `Map.of()`: el validador rechaza o
  acepta según el evento (los sets permitidos pueden ser
  vacíos para algunos eventos).
- `auth.password_cambiada` con origen `perfil` y con origen
  `reset` (dos tests, uno por origen).

### REFACTOR

- Si los emisores saturan los servicios, extraer un helper
  `LogActividadHelper.emit(usuarioId, evento, entidad,
  entidadId, detalle)` que pre-construye el `Map<String,Object>`
  desde los UUIDv7. 038 lo deja inline y decide en 040.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| `auth.login` ok | 1 fila con `detalle.resultado=ok`; sin PII. |
| `auth.logout` | 1 fila con `detalle.resultado=ok` (sin PII). |
| `auth.registro` | 1 fila con `detalle.usuarioId` UUIDv7 (sin `emailDestino`). |
| `auth.password_cambiada` (perfil) | 1 fila con `detalle.origen=perfil`. |
| `auth.password_cambiada` (reset) | 1 fila con `detalle.origen=reset`. |
| `usuario.activado` origen `invitacion` | 1 fila con `detalle.origen=invitacion` y `entidadId` UUIDv7. |
| `proyecto.creado` | 1 fila con `entidadId` UUIDv7 del `Proyecto`; `detalle` `{}`. |
| `proyecto.editado` | 1 fila con `entidadId` UUIDv7 del `Proyecto`. |
| `proyecto.eliminado` | 1 fila con `entidadId` UUIDv7 del `Proyecto`. |
| `proyecto.duplicado` | 1 fila con `entidadId` (UUIDv7 del proyecto duplicado) **y** `detalle.proyectoOrigenId` y `detalle.proyectoDuplicadoId`. **Gated** por `STOP-032-P09-DUPLICAR`: solo aplica si el acta 032 implementó el seam `ProyectoService.duplicar(...)`; en otro caso este caso queda declarado como `no producer yet` y no corre. |
| `insumo.creado` | 1 fila con `entidadId` UUIDv7 del `Insumo`; `detalle.codigoInsumo` y `detalle.tipo`. |
| `insumo.editado` | 1 fila con `entidadId` UUIDv7 del `Insumo`. |
| `insumo.eliminado` | 1 fila con `entidadId` UUIDv7 del `Insumo`. |
| `insumos.import_csv` | 1 fila con `entidadId` UUIDv7 de la `BaseInsumos` destino; `detalle.creados/actualizados/errores`. |
| `base.copiada_a_proyecto` | 1 fila con `detalle.baseOrigenId/proyectoDestinoId/cantidadInsumos/cantidadOmitidos`. |
| `apu.creado` | 1 fila con `entidadId` UUIDv7 del `Apu`. |
| `apu.editado` | 1 fila con `entidadId` UUIDv7 del `Apu`. |
| `apu.eliminado` | 1 fila con `entidadId` UUIDv7 del `Apu`. |
| Sin PII en `detalle` | 0 matches regex. |
| Sin emisión en lectura | `GET` no produce fila. |
| Cobertura matriz 038 | los 16 nombres únicos + 1 camino emisor adicional que tienen producer en el código tienen ≥ 1 fila; `proyecto.duplicado` queda declarado como `no producer yet` hasta el cierre de `STOP-032-P09-DUPLICAR`. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.usuario.audit.LogActividadCoberturaD13IdentidadTest' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.usuario.auth.AuthServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.service.ProyectoServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.service.InsumoCrudServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.service.ImportacionInsumoServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.service.CopiaBaseServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.service.ApuCrudServiceLogAuditoriaIT' \
  -Dquarkus.http.test-port=0 --console=plain

# Regresión por módulo.
./gradlew test --tests 'ec.uce.propuestas.usuario.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Ningún emisor en motor/ ni recalculo/ ni presupuesto/ ni
# cronograma/ ni documento/ (esos son de 039).
git diff --name-only -- 'src/main/java/ec/uce/propuestas/' \
  | grep -E 'motor|recalculo|presupuesto|cronograma|documento'
# esperado: solo `usuario/audit/` modificado.

# Ningún evento nuevo fuera del catálogo.
! grep -RInE '"\w+\.\w+"' \
  src/main/java/ec/uce/propuestas/ \
  | grep -v 'usuario/audit/' \
  | grep -vE 'auth\.|usuario\.|proyecto\.|insumo|insumos\.|base\.|apu\.|presupuesto\.|cronograma\.|documento\.|admin\.'

# Sin migración.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (038)

- [ ] 16 nombres únicos + 1 camino emisor adicional (`usuario.activado` origen `invitacion`) emitidos con la clave D-13 exacta
      (`auth.password_cambiada` cubre perfil y reset vía
      `AuthService.cambiarPassword` y `AuthService.restablecerPassword`;
      `usuario.activado` con origen `invitacion`).
- [ ] `proyecto.duplicado`: estado explícito en el completion
      checklist. Si el acta 032 cerró `STOP-032-P09-DUPLICAR`
      implementando el seam `ProyectoService.duplicar(...)` y
      `POST /api/v1/proyectos/{id}/duplicar`, este plan emite ese
      evento. Si el acta difirió el evento como "no producer yet",
      este plan **no** lo emite y registra el estado en este
      ítem. **No se fabrica el seam canónico en 038.**
- [ ] Cada evento tiene `detalle` con el set de claves canónicas
      de la matriz del acta 032 (decisión 2).
- [ ] Cuando un servicio público no abría `@Transactional`, se
      ajusta con test focal verde.
- [ ] Ningún emisor en `motor/`, `recalculo/`, `presupuesto/`,
      `cronograma/`, `documento/`.
- [ ] Ningún evento duplicado con 034–037.
- [ ] Regresión por módulo verde.
- [ ] Sin PII en `detalle` (TC-P42-02 verde en estos eventos).
- [ ] Ninguna migración nueva.
- [ ] `git diff --check` limpio.

## Handoff al siguiente plan

Cuando 038 cierre, el orquestador inicia **039** (instrumentación
D-13 presupuesto/cronograma/documento). 039 preserva la
transacción única y el TOCTOU de Plan 031.