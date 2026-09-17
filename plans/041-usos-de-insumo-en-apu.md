# Plan 041: `GET /proyectos/{id}/insumos/{id}/usos` es un stub que devuelve `[]`

> **Instrucciones para quien ejecute**: sigue este plan paso a paso. Corre cada
> comando de verificación y confirma el resultado esperado antes de avanzar al
> siguiente paso. Si ocurre algo listado en "Condiciones STOP", detente y
> repórtalo — no improvises. Al terminar, actualiza la fila de este plan en
> [`README.md`](README.md).
>
> **Comprobación de deriva (ejecutar primero)**:
> `git diff --stat b4d2275..HEAD -- src/main/java/ec/uce/propuestas/insumo/ src/main/java/ec/uce/propuestas/apu/entity/`
> Si alguno de esos archivos cambió desde que se escribió este plan, compara los
> fragmentos de "Estado actual" contra el código real antes de continuar; si no
> coinciden, trátalo como condición STOP.

## Estado

- **Prioridad**: P1
- **Esfuerzo**: M
- **Riesgo**: LOW — endpoint de sólo lectura, sin migración, sin cambio de DTO,
  sin tocar el motor de cálculo.
- **Depende de**: ninguno
- **Categoría**: funcionalidad no implementada (stub en producción)
- **Planificado en**: commit `b4d2275`, 2026-09-16

## Por qué importa

El endpoint existe, está enrutado, valida el UUID, comprueba la propiedad… y
devuelve una lista vacía literal. No consulta nada:

`src/main/java/ec/uce/propuestas/insumo/resource/InsumoResource.java`, línea ~155:

```java
    @GET
    @Path("/{insumoId}/usos")
    @Consumes(MediaType.WILDCARD)
    public java.util.List<InsumoUsoResponse> usos(
            @PathParam("proyectoId") String proyectoId, @PathParam("insumoId") String insumoId) {
        UUID insumoPublicId = UuidV7.parse(insumoId);
        ProyectoYBase contexto = resolverProyectoYBase(proyectoId);
        Insumo insumo = insumoRepository
                .findByPublicIdAndBase(insumoPublicId, contexto.base().id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Insumo no encontrado en esta base"));
        return java.util.List.of();
    }
```

Fíjate en que la variable `insumo` se resuelve y **no se usa para nada**: sólo
sirve para producir el 404. El `-Xlint:all` del build no lo marca porque la
variable sí se "usa" en el sentido del compilador (está asignada), pero
funcionalmente es un cascarón.

Consecuencia en producto: en el frontend, el botón **Ver uso** de cada insumo
(`/proyectos/{uuid}/insumos`, menú `[…]`) abre un diálogo cuya tabla
`Código · Descripción · Bloque · Precio` sale **vacía para todos los insumos,
siempre** — incluidos los que este mismo backend se niega a borrar porque están
referenciados en APUs. El usuario no tiene forma de saber dónde se usa un
insumo antes de tocarlo.

La contradicción es directa: `InsumoCrudService.eliminar` ya sabe contar esos
usos y usa el recuento para bloquear el borrado:

`src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java`:

```java
        long usos = insumoRepository.contarUsosEnApuDetalle(e.id);
        if (usos > 0) {
            throw ProblemaException.validacion(
                    "No se puede eliminar el insumo: está referenciado en " + usos + " parte(s) de APU");
        }
```

O sea: el dato existe y ya se consulta. Lo único que falta es **listarlo en vez
de contarlo**.

El proceso canónico es **P-18** («dónde se usa un insumo, bloque M/N/O/P /
heredado vs override»), y así lo documenta el propio DTO.

## Estado actual

### El DTO — ya existe, **no lo cambies**

`src/main/java/ec/uce/propuestas/insumo/dto/InsumoUsoResponse.java`:

```java
/**
 * P-18: dónde se usa un insumo (bloque M/N/O/P / heredado vs override).
 *
 * <p>Plan 07 — el {@code apuId} es la identidad externa UUIDv7 del APU; nunca
 * el {@code BIGINT} interno.</p>
 */
public record InsumoUsoResponse(UUID apuId, String codigo, String descripcion, String bloque, boolean override) {}
```

El frontend ya lo consume con este contrato exacto, validado con Zod
(`src/api/schemas.ts`, `insumoUsoSchema`, `.strict()`): **cualquier campo de más
o de menos rompe la pantalla**. Los cinco campos, con esos nombres y esos tipos.

Interpretación de cada campo, que es lo que hay que implementar:

| Campo | Qué es |
|---|---|
| `apuId` | `Apu.publicId` del APU que referencia el insumo |
| `codigo` | `Apu.codigo` |
| `descripcion` | `Apu.descripcion` |
| `bloque` | La letra del bloque SERCOP donde aparece la fila: `M`/`N`/`O`/`P` |
| `override` | `true` si esa fila tiene precio manual; `false` si hereda el del insumo |

### El recuento que ya funciona

`src/main/java/ec/uce/propuestas/insumo/repository/InsumoRepository.java`:

```java
    public long contarUsosEnApuDetalle(Long insumoId) {
        return getEntityManager()
                .createQuery("select count(d) from ApuDetalle d where d.insumoId = :insumoId", Long.class)
                .setParameter("insumoId", insumoId)
                .getSingleResult();
    }
```

### Las entidades por las que hay que navegar

`ApuDetalle` (`src/main/java/ec/uce/propuestas/apu/entity/ApuDetalle.java`):

```java
    @Column(name = "seccion_id", nullable = false)
    public Long seccionId;

    @Column(name = "insumo_id")
    public Long insumoId;

    @Column(name = "tarifa_jornal", precision = 14, scale = 6)
    public BigDecimal tarifaJornal;

    @Column(name = "precio_unitario_tarifa", precision = 14, scale = 6)
    public BigDecimal precioUnitarioTarifa;
```

`ApuSeccion` (`src/main/java/ec/uce/propuestas/apu/entity/ApuSeccion.java`):

```java
    @Column(name = "apu_id", nullable = false)
    public Long apuId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public SeccionTipo tipo;
```

`SeccionTipo` (`src/main/java/ec/uce/propuestas/motor/SeccionTipo.java`):

```java
public enum SeccionTipo {
    EQUIPO,
    MANO_OBRA,
    MATERIAL,
    TRANSPORTE
}
```

`Apu`: `publicId` (UUIDv7), `codigo`, `descripcion`, `presupuestoId`.
`Presupuesto`: `proyectoId`.

### Cómo se resuelve hoy un override

`src/main/java/ec/uce/propuestas/apu/service/ApuCalculoService.java`:

```java
    public static BigDecimal overrideDeDetalle(ApuDetalle d, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO, MANO_OBRA -> d.tarifaJornal;
            case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa;
        };
    }
```

y el precio efectivo es `COALESCE(override, Insumo.precioUnitario)` — o sea
`override != null` significa exactamente «esta fila tiene precio manual». Es la
misma semántica `null-means-inherit` que documenta
`ApuDetalleMapper` («`precioHeredado` = true si no hay override manual, DM §8»).

**Reutiliza `ApuCalculoService.overrideDeDetalle`.** Es `public static` y existe
precisamente para esto; no re-implementes el `switch`.

## Qué hay que hacer

### Paso 1 — Una consulta de listado en `InsumoRepository`

Junto a `contarUsosEnApuDetalle`, añade el listado. Dos requisitos que **no**
son negociables:

1. **Acota por proyecto.** Un insumo vive en la base PROYECTO del proyecto, pero
   el `insumo_id` de `apu_detalle` no lleva proyecto encima. Sin el filtro, un
   insumo copiado a dos proyectos enseñaría los APUs del otro — fuga de datos
   entre proyectos, y RNF-05 (owner-to-404) existe justamente para que eso no
   pase.
2. **Trae `ApuDetalle` y `ApuSeccion` en la misma consulta**, no en un bucle:
   un APU puede tener decenas de filas y el N+1 se nota.

Sugerencia de firma y consulta (ajústala al estilo que veas en el repositorio;
lo que importa es el resultado, no la forma exacta):

```java
    /**
     * Plan 041 (P-18) — filas de APU de <b>este proyecto</b> que referencian el
     * insumo, con su sección (bloque M/N/O/P) y el APU al que pertenecen.
     *
     * <p>El filtro por {@code proyectoId} no es decorativo: {@code apu_detalle}
     * no lleva proyecto encima, y un insumo copiado a dos proyectos enseñaría
     * los APUs del otro (RNF-05).</p>
     */
    public List<Object[]> listarUsosEnApuDetalle(Long insumoId, Long proyectoId) {
        return getEntityManager()
                .createQuery(
                        """
                        select a.publicId, a.codigo, a.descripcion, s.tipo, d
                        from ApuDetalle d
                        join ApuSeccion s on s.id = d.seccionId
                        join Apu a on a.id = s.apuId
                        join Presupuesto p on p.id = a.presupuestoId
                        where d.insumoId = :insumoId and p.proyectoId = :proyectoId
                        order by a.codigo, s.orden, d.orden
                        """,
                        Object[].class)
                .setParameter("insumoId", insumoId)
                .setParameter("proyectoId", proyectoId)
                .getResultList();
    }
```

**Comprueba antes de escribirla** si las entidades tienen asociaciones JPA
declaradas (`@ManyToOne`) o sólo columnas `Long`. Por lo que se ve en
`ApuDetalle` y `ApuSeccion`, son columnas `Long` sin asociación, así que los
`join` van por condición explícita (`join X on ...`), como arriba, y **no** con
navegación `d.seccion.apu`. Si te encuentras con que sí hay asociaciones, usa
la navegación: es más legible.

Si prefieres no devolver `Object[]`, declara un `record` de proyección y usa
`select new ec.uce...(…)` — es más limpio y el repo ya usa DTOs de proyección en
otros sitios. Elige uno y sé consistente.

Verificación:

```bash
./gradlew build -x test
```

Esperado: compila. (Hay ~31 *deprecation warnings* preexistentes; no son tuyos.)

### Paso 2 — El mapeo de bloque

El contrato dice `bloque` = `M`/`N`/`O`/`P`. Hoy **no existe** ninguna función
que traduzca `SeccionTipo` a esa letra: el `ApuCalculoSeccion` expone el enum y
la letra sólo vive en comentarios («Proyecta el cálculo del APU en cuatro
bloques canónicos M/N/O/P»).

Créala, en un solo sitio, junto al enum —
`src/main/java/ec/uce/propuestas/motor/SeccionTipo.java` — como método del
propio enum:

```java
public enum SeccionTipo {
    EQUIPO,
    MANO_OBRA,
    MATERIAL,
    TRANSPORTE;

    /**
     * Letra del bloque en la hoja SERCOP (dossier §B.8): M equipo, N mano de
     * obra, O materiales, P transporte. Es presentación del contrato P-18, no
     * del motor.
     */
    public String bloque() {
        return switch (this) {
            case EQUIPO -> "M";
            case MANO_OBRA -> "N";
            case MATERIAL -> "O";
            case TRANSPORTE -> "P";
        };
    }
}
```

**Antes de añadirlo**, corre
`grep -rn '"M"' --include=*.java src/main/java` para confirmar que no hay ya
otra traducción escondida. Si la hay, usa esa y **no** crees una segunda: dos
tablas de letras que pueden divergir es peor que ninguna.

Verificación:

```bash
./gradlew build -x test
```

### Paso 3 — El resource devuelve datos reales

En `InsumoResource.usos`, sustituye el `return java.util.List.of();` por el
mapeo. Sigue resolviendo el insumo primero, para conservar el 404 de
«insumo no encontrado en esta base»:

```java
        Insumo insumo = insumoRepository
                .findByPublicIdAndBase(insumoPublicId, contexto.base().id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Insumo no encontrado en esta base"));

        return insumoRepository.listarUsosEnApuDetalle(insumo.id, contexto.proyecto().id).stream()
                .map(fila -> {
                    // …desempaqueta la fila, calcula bloque con tipo.bloque()
                    // y override con ApuCalculoService.overrideDeDetalle(d, tipo) != null
                })
                .toList();
```

Reglas del mapeo:

- `apuId` = `Apu.publicId` (UUIDv7), **nunca** el `BIGINT` interno. Es la regla
  del Plan 07 y está escrita en el Javadoc del DTO.
- `override` = `ApuCalculoService.overrideDeDetalle(detalle, tipo) != null`.
- `bloque` = `tipo.bloque()`.
- **No filtres las filas de herramienta menor** (`esHerramientaMenor`): esas
  filas no tienen `insumoId`, así que la propia consulta ya las deja fuera.
- Si el mismo insumo aparece en dos filas del mismo APU (por ejemplo en dos
  secciones distintas), salen **dos** entradas. Es correcto: cada una es un uso
  con su bloque y su override. No dedupliques por `apuId`.

Si el `stream` con desempaquetado de `Object[]` queda ilegible, mete el mapeo en
un método privado del resource o, mejor, en un pequeño service — pero **no
añadas un módulo nuevo** por esto.

Verificación:

```bash
./gradlew spotlessApply && ./gradlew build -x test
```

### Paso 4 — Tests de integración

En `src/test/java/ec/uce/propuestas/insumo/resource/InsumoResourceIT.java`
(**léelo entero antes de tocarlo** para reutilizar sus helpers de fixture y su
patrón REST Assured). Añade estos casos, y sólo estos:

1. **Un insumo usado en un APU devuelve una entrada** con `apuId` = el
   `publicId` del APU, `codigo` y `descripcion` del APU, `bloque` correcto y
   `override` coherente.
2. **Bloque y override por sección.** Una fila en MATERIAL con
   `precio_unitario_tarifa` no nulo → `bloque = "O"`, `override = true`. Una
   fila en EQUIPO sin `tarifa_jornal` → `bloque = "M"`, `override = false`.
   Esto es el corazón de P-18 y es lo que se puede volver a romper.
3. **Un insumo sin usos devuelve `[]`** (200, no 404).
4. **Aislamiento entre proyectos.** Dos proyectos con una copia del mismo
   insumo; pedir los usos en el proyecto A **no** devuelve los APUs del B. Este
   es el test que justifica el filtro del paso 1; sin él, el filtro se cae en
   cualquier refactor futuro.
5. **Owner-to-404.** Otro usuario pidiendo el mismo insumo → 404, no 403. Es la
   convención RNF-05 del repo; comprueba cómo lo hacen los tests vecinos y
   cópialo.

Verificación:

```bash
./gradlew test --tests 'ec.uce.propuestas.insumo.*' --console=plain
```

Esperado: verde, incluidos los cinco casos nuevos.

### Paso 5 — Regresión focal

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
./gradlew spotlessCheck
```

Esperado: verde los tres.

Después, la suite completa:

```bash
./gradlew test --console=plain
```

**Línea base conocida y aceptada** — no la persigas, no la "arregles":

- `GM-19` rojo con residual `-$6.95`
- `GM-20` capítulo 1 rojo con residual `-$0.84`
- `GM-24` `@Disabled` (skipped)

El motor **no se reabre** (Plan 014, decisión cerrada). Cualquier otro fallo sí
es tuyo. Reporta totales y delta contra el baseline que encuentres al empezar.

## Condiciones STOP

- Si `ApuDetalle` / `ApuSeccion` / `Apu` resultan tener asociaciones JPA o
  columnas distintas de las citadas arriba, para: el plan está desactualizado.
- Si ya existe en algún sitio una traducción `SeccionTipo` → letra, **úsala** y
  salta el paso 2. Dilo en el reporte.
- **No cambies `InsumoUsoResponse`.** El frontend lo valida con `.strict()`:
  añadir un campo rompe la pantalla en producción. Si crees que le falta algo
  (por ejemplo la cantidad de la fila), para y reporta la propuesta; es un
  cambio de contrato y se decide con el frontend delante.
- **No añadas migración Flyway.** Toda la información que hace falta ya está en
  el esquema.
- Si el aislamiento entre proyectos resulta imposible con el modelo actual
  (porque `apu_detalle` no se puede acotar a un proyecto), para y reporta: sería
  un problema de diseño de datos, no de este endpoint.
- Si `./gradlew test` estaba rojo más allá del baseline aceptado **antes** de
  tus cambios, regístralo y reporta; no lo arregles dentro de este plan.

## Fuera de alcance

- El motor de cálculo (`ec.uce.propuestas.motor.*`) y la regla
  *workbook-consistent*. Sólo se le añade un método de presentación al enum.
- `InsumoCrudService.eliminar` y su mensaje de bloqueo: sigue siendo correcto.
- Cualquier otro endpoint de `InsumoResource`.
- El frontend. El plan `099` de `../thesis-front-react` le pone un estado vacío
  honesto al diálogo, y empezará a pintar filas reales en cuanto este plan
  aterrice, sin ningún cambio adicional allí.

## Criterios de terminado (comprobables por máquina)

```bash
./gradlew spotlessCheck                                          # verde
./gradlew build -x test                                          # verde
./gradlew test --tests 'ec.uce.propuestas.insumo.*'              # verde
./gradlew test --tests 'ec.uce.propuestas.apu.*'                 # verde
./gradlew test                                                   # baseline + 0 fallos nuevos
git diff --check                                                 # limpio
```

Y este `grep`, que debe salir vacío:

```bash
grep -n "return java.util.List.of();" src/main/java/ec/uce/propuestas/insumo/resource/InsumoResource.java
```

## Verificación manual

Con la aplicación levantada (`./gradlew --console=plain quarkusDev`) y la base
sembrada con `V004__seed_escenarios.sql`:

1. Autentícate como `john.doe@uce.edu.ec` / `Clave1234`.
2. Elige un insumo que el borrado rechace (el mensaje dice «referenciado en N
   parte(s) de APU»).
3. `GET /api/v1/proyectos/{proyectoId}/insumos/{insumoId}/usos` debe devolver
   **N entradas**, con el mismo N del mensaje de borrado. Si no coinciden, algo
   falta en la consulta — probablemente el filtro por proyecto está de más o de
   menos.
4. Un insumo recién creado y sin usar devuelve `[]` con 200.

## Nota de mantenimiento

Lo que deja este plan como regla: **un endpoint que devuelve una constante es un
endpoint apagado, y hay que poder verlo.** Éste llevaba meses enrutado,
documentado y validando permisos, con el `STOP backend: usos de insumo continúa
devolviendo una lista vacía por stub` escrito en el índice de planes del
frontend — y aun así ninguna prueba lo marcaba, porque un test que afirma
«devuelve una lista» pasa igual con la lista vacía.

Si vuelves a dejar un stub, que su test afirme el contenido, no la forma.
