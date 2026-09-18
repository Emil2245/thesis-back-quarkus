# Plan 042: Los capítulos y rubros se ordenan como texto, así que "1.12" sale antes que "1.2"

> **Instrucciones para quien ejecute**: sigue este plan paso a paso. Corre cada
> comando de verificación y confirma el resultado esperado antes de avanzar al
> siguiente paso. Si ocurre algo listado en "Condiciones STOP", detente y
> repórtalo — no improvises. Al terminar, actualiza la fila de este plan en
> [`README.md`](README.md).
>
> **Comprobación de deriva (ejecutar primero)**:
> `git diff --stat b4d2275..HEAD -- src/main/java/ec/uce/propuestas/presupuesto/ src/main/java/ec/uce/propuestas/cronograma/service/VistasCronogramaService.java`
> Si alguno de esos archivos cambió desde que se escribió este plan, compara los
> fragmentos de "Estado actual" contra el código real antes de continuar; si no
> coinciden, trátalo como condición STOP.

## Estado

- **Prioridad**: P1
- **Esfuerzo**: S
- **Riesgo**: LOW — cambia el orden de listas ya construidas. No toca importes,
  ni el motor de cálculo, ni ninguna escala decimal, ni ninguna migración.
- **Depende de**: ninguno
- **Categoría**: bug (correctness)
- **Planificado en**: commit `b4d2275`, 2026-09-16

## Por qué importa

`GET /presupuestos/{id}` devuelve el árbol del presupuesto con los capítulos en
este orden:

```
1.1, 1.10, 1.11, 1.12, 1.2, 1.3, …, 1.9
```

y los rubros del capítulo 2 así:

```
2.1, 2.10, 2.11, 2.12, …, 2.19, 2.2, 2.20, …
```

En el frontend eso se ve directamente: en
`/proyectos/{uuid}/workspace` del proyecto "Cetro Médico Tulcán", el
subcapítulo `1.12 · OBRA CIVIL PARA SISTEMA ELÉCTRICO` aparece **el primero**,
por delante de `1.2 · OBRA CIVIL`, y los 51 rubros del capítulo `2 · SISTEMA
ELECTRICO` salen barajados. El presupuesto parece corrupto aunque los datos
estén bien.

**Los datos están bien.** El seed `V004__seed_escenarios.sql` reproduce
fielmente el artefacto de origen
(`../thesis-docs/plan/domain/_artifacts/presupuesto-apus-cetro-medico-tulcan.json`,
331 nodos), y la columna `capitulo.orden` trae la secuencia correcta: `1.1` →
`orden = 2`, `1.2` → `3`, … `1.12` → `13`.

La causa es una comparación de cadenas donde hacía falta una numérica, y está
escrita en el Javadoc de la clase como si fuera una verdad:

`src/main/java/ec/uce/propuestas/presupuesto/mapper/PresupuestoMapper.java`:

```java
 *   <li>Árbol recursivo sin tope; hijos ordenados por {@code item}
 *       ascendente (lexicográfico coincide con orden natural "1", "1.1",
 *       "1.1.1", "2", …).</li>
```

**Es falso.** El orden lexicográfico coincide con el natural sólo mientras
todos los segmentos tienen una cifra. En cuanto hay un `1.10`, deja de
coincidir: `"1.12" < "1.2"` porque compara `'1'` contra `'2'` en la segunda
posición. Los ejemplos que cita el comentario —`"1"`, `"1.1"`, `"1.1.1"`,
`"2"`— son justamente los que no lo destapan. Nadie lo comprobó con dos cifras.

## Estado actual

Hay **cuatro** sitios con el mismo defecto. Los cuatro hay que arreglarlos: si
arreglas sólo el del presupuesto, el cronograma sigue barajado.

### 1 y 2 — `PresupuestoMapper.toPresupuestoResponseConInyecciones`

`src/main/java/ec/uce/propuestas/presupuesto/mapper/PresupuestoMapper.java`:

```java
        rubrosPorCapitulo.values().forEach(list -> list.sort(Comparator.comparing(r -> r.item)));
```

```java
        raices.sort(Comparator.comparing(c -> c.item));
        hijosPorPadre.values().forEach(list -> list.sort(Comparator.comparing(c -> c.item)));
```

Alimenta `GET /presupuestos/{id}`, que es el origen del árbol para tres
pantallas del frontend (workspace, presupuesto y resumen).

### 3 — `VistasCronogramaService`

`src/main/java/ec/uce/propuestas/cronograma/service/VistasCronogramaService.java`,
dos ordenaciones de rubros (líneas ~201 y ~529):

```java
        rubrosPlanos.sort(
                Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item).thenComparing(r -> r.id));
```

```java
            rubrosOrdenados.sort(Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item)
                    .thenComparing(r -> r.id));
```

y la carga de capítulos, que ordena en SQL:

`src/main/java/ec/uce/propuestas/presupuesto/repository/CapituloRepository.java`:

```java
    public List<Capitulo> listarPorPresupuestoOrdenado(Long presupuestoId) {
        return find("presupuestoId = :presupuestoId order by item", Parameters.with("presupuestoId", presupuestoId))
                .list();
    }
```

El `order by item` de SQL sobre un `VARCHAR` es exactamente la misma
comparación lexicográfica, y Postgres **no** tiene un orden natural para
cadenas.

Su Javadoc también afirma algo que no se sostiene:

```java
     * <p>Plan 030 (P-35/P-36) — variante acotada y ordenada por {@code item}
     * ascendente del listado de capítulos. La unicidad de
     * {@code (presupuesto_id, item)} (V001 §2.9) garantiza un orden total
     * estable: el árbol se reconstruye en memoria sin colisiones ni
     * desempates arbitrarios.
```

La unicidad garantiza que el orden sea **total y estable**, sí — pero no que
sea el orden **correcto**. Son dos cosas distintas, y el comentario las
confunde.

### 4 — `VersionadoService.construirItem` (comparación de versiones)

`src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java`:

```java
        List<Capitulo> raices = capitulos.stream()
                .filter(c -> c.parentId == null)
                .sorted(Comparator.comparing(c -> c.item == null ? "" : c.item))
                .toList();
```

Alimenta `GET /presupuestos/{id}/comparar?con=…`. Aquí sólo se ordenan raíces
(`1`, `2`, `3`…), así que hoy no se nota — pero un presupuesto con más de nueve
capítulos raíz lo destaparía, y dejarlo con la comparación mala es dejar la
trampa puesta.

## Qué hay que hacer

### Paso 1 — Un comparador natural, en un solo sitio

Crea `src/main/java/ec/uce/propuestas/common/ItemJerarquico.java`:

```java
package ec.uce.propuestas.common;

import java.util.Comparator;

/**
 * Plan 042 — orden natural de los {@code item} jerárquicos del presupuesto
 * ("1", "1.2", "1.12", "2.10"): segmento a segmento, numéricamente.
 *
 * <p>El orden lexicográfico NO sirve, aunque lo parezca con datos pequeños:
 * {@code "1.12" < "1.2"} como cadenas, porque compara {@code '1'} contra
 * {@code '2'} en la segunda posición. Con más de nueve subcapítulos el árbol
 * sale barajado. Ese era el defecto que este tipo retira, y estaba escrito como
 * verdad en el Javadoc de {@code PresupuestoMapper}.</p>
 *
 * <p>Un segmento no numérico se compara como texto contra el otro, para que un
 * item con letras no rompa el orden ni lance excepción.</p>
 */
public final class ItemJerarquico {

    /** Orden natural; {@code null} va al final. */
    public static final Comparator<String> ORDEN =
            Comparator.nullsLast(ItemJerarquico::comparar);

    private ItemJerarquico() {}

    public static int comparar(String a, String b) {
        String[] sa = a.split("\\.", -1);
        String[] sb = b.split("\\.", -1);
        int n = Math.max(sa.length, sb.length);
        for (int i = 0; i < n; i++) {
            // El más corto va primero: "1" antes que "1.1".
            if (i >= sa.length) return -1;
            if (i >= sb.length) return 1;
            Long na = enteroONull(sa[i]);
            Long nb = enteroONull(sb[i]);
            if (na != null && nb != null) {
                int c = Long.compare(na, nb);
                if (c != 0) return c;
            } else {
                int c = sa[i].compareTo(sb[i]);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private static Long enteroONull(String s) {
        if (s.isEmpty()) return null;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null; // segmento de más de 19 dígitos
        }
    }
}
```

`split("\\.", -1)` con límite negativo conserva los segmentos vacíos, para que
`"1."` y `"1"` no se confundan.

Verificación:

```bash
./gradlew build -x test
```

### Paso 2 — Un test unitario del comparador

Crea `src/test/java/ec/uce/propuestas/common/ItemJerarquicoTest.java`. Es una
clase pura: test unitario JUnit normal, **sin `@QuarkusTest`** (no necesita
contenedor y arrancarlo sería tirar segundos a la basura). Mira algún test de
`ec.uce.propuestas.motor.*` para el estilo.

Casos:

```java
  // 1.1, 1.2, 1.10, 1.12 — y NO 1.1, 1.10, 1.12, 1.2
  // 2, 2.1, 2.10 — el padre antes que los hijos
  // 2.1, 2.2, …, 2.9, 2.10, 2.11 — el caso del capítulo 2 de Tulcán
  // "1.a" contra "1.2" no lanza excepción
  // null va al final con ItemJerarquico.ORDEN
```

Verificación:

```bash
./gradlew test --tests 'ec.uce.propuestas.common.ItemJerarquicoTest' --console=plain
```

### Paso 3 — Aplicarlo en los cuatro sitios

**a) `PresupuestoMapper`** — las tres ordenaciones:

```java
        rubrosPorCapitulo.values().forEach(list -> list.sort(Comparator.comparing(r -> r.item, ItemJerarquico.ORDEN)));
        …
        raices.sort(Comparator.comparing(c -> c.item, ItemJerarquico.ORDEN));
        hijosPorPadre.values().forEach(list -> list.sort(Comparator.comparing(c -> c.item, ItemJerarquico.ORDEN)));
```

Y **corrige el Javadoc de la clase**, que es la mitad del defecto:

```java
 *   <li>Árbol recursivo sin tope; hijos ordenados por {@code item} en orden
 *       natural ({@link ec.uce.propuestas.common.ItemJerarquico}), no
 *       lexicográfico: "1.2" va antes que "1.12".</li>
```

**b) `VistasCronogramaService`** — las dos ordenaciones de rubros:

```java
        rubrosPlanos.sort(Comparator.comparing((Rubro r) -> r.item, ItemJerarquico.ORDEN)
                .thenComparing(r -> r.id));
```

Conserva el desempate `.thenComparing(r -> r.id)`: mantiene el orden
determinista cuando dos items empatan.

**c) `CapituloRepository.listarPorPresupuestoOrdenado`** — quita el `order by
item` del JPQL y ordena en memoria, porque Postgres no sabe hacerlo:

```java
    /**
     * Plan 030 (P-35/P-36) — listado acotado de capítulos de un presupuesto,
     * ordenado por {@code item} en orden <b>natural</b>
     * ({@link ec.uce.propuestas.common.ItemJerarquico}).
     *
     * <p>Plan 042 — el orden ya no se delega a {@code ORDER BY item} de SQL:
     * sobre {@code VARCHAR} eso es orden lexicográfico y devuelve "1.12" antes
     * que "1.2". Postgres no tiene orden natural para cadenas, así que la
     * comparación se hace en memoria. El conjunto es el árbol de un solo
     * presupuesto (≈30 filas en el caso IESS), no una tabla entera.</p>
     */
    public List<Capitulo> listarPorPresupuestoOrdenado(Long presupuestoId) {
        List<Capitulo> capitulos = new ArrayList<>(
                find("presupuestoId = :presupuestoId", Parameters.with("presupuestoId", presupuestoId)).list());
        capitulos.sort(Comparator.comparing((Capitulo c) -> c.item, ItemJerarquico.ORDEN));
        return capitulos;
    }
```

El `find(...).list()` de Panache puede devolver una lista inmutable: cópiala a
un `ArrayList` antes de ordenar, como arriba. No lo omitas.

**d) `VersionadoService.construirItem`**:

```java
        List<Capitulo> raices = capitulos.stream()
                .filter(c -> c.parentId == null)
                .sorted(Comparator.comparing((Capitulo c) -> c.item, ItemJerarquico.ORDEN))
                .toList();
```

Verificación tras los cuatro:

```bash
./gradlew spotlessApply && ./gradlew build -x test
```

### Paso 4 — Un test de integración que lo demuestre de punta a punta

Un comparador verde no prueba que el endpoint devuelva bien. En
`src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoResourceIT.java`
(**léelo entero antes**, y reutiliza sus helpers de fixture) añade **un** test:

> Un presupuesto con un capítulo raíz y doce subcapítulos `1.1 … 1.12` devuelve
> `subcapitulos[*].item` en el orden `1.1, 1.2, …, 1.9, 1.10, 1.11, 1.12`.

Doce, no tres: con menos de diez el bug no aparece y el test pasaría igual
estando roto. Ése es justo el motivo por el que esto sobrevivió hasta ahora.

Si te resulta más barato montar el caso con rubros que con subcapítulos, vale
igual: lo que tiene que quedar cubierto es un nivel con más de nueve hermanos.

Verificación:

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
```

### Paso 5 — Regresión

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' --console=plain
./gradlew spotlessCheck
./gradlew test --console=plain
```

**Línea base conocida y aceptada** — no la persigas, no la "arregles":

- `GM-19` rojo con residual `-$6.95`
- `GM-20` capítulo 1 rojo con residual `-$0.84`
- `GM-24` `@Disabled` (skipped)

El motor **no se reabre** (Plan 014, decisión cerrada). Reporta totales y delta
contra el baseline que midas al empezar.

Presta atención especial a los tests de exportación
(`ec.uce.propuestas.documento.*`, `CronogramaExportResourceIT`): los writers
XLSX/PDF/MSPDI escriben las filas **en el orden en que las reciben**, así que
este cambio también cambia el orden de las filas del documento generado — para
bien. Si alguno compara bytes o posiciones de fila, se pondrá rojo y hay que
actualizar su expectativa al orden nuevo, **no** revertir el arreglo.

## Condiciones STOP

- Si algún test se pone rojo porque **esperaba el orden lexicográfico**, ese
  test estaba codificando el bug. Actualízalo al orden natural y **dilo
  explícitamente en tu reporte**, con el nombre del test. Lo que no puedes hacer
  es cambiarlo en silencio ni desactivarlo.
- Si `capitulo.item` resulta poder ser `null` en datos reales (el esquema
  V001 §2.9 lo tiene en un `UNIQUE (presupuesto_id, item)`, así que no debería),
  `ItemJerarquico.ORDEN` ya lo manda al final con `nullsLast`. Si aun así
  aparece un NPE, para y reporta.
- **No añadas migración Flyway** ni cambies el tipo de la columna `item`. No
  hace falta.
- **No toques `CapituloService.renumerarArbol`** ni ninguna escritura de
  `item`/`orden`. Este plan cambia cómo se **lee** el orden, no cómo se asigna.
- Si `./gradlew test` estaba rojo más allá del baseline aceptado **antes** de
  tus cambios, regístralo y reporta; no lo arregles dentro de este plan.

## Fuera de alcance

- El motor de cálculo y la regla *workbook-consistent* (Plan 014).
- Cualquier importe, escala o redondeo. Este plan **no** cambia ni un decimal:
  si algún total se mueve, algo salió mal — para y reporta.
- La renumeración de capítulos al crear/mover (Plan 022).
- El frontend. El plan `100` de `../thesis-front-react` añade el mismo orden en
  el cliente como defensa; los dos son idempotentes entre sí y no hace falta
  coordinarlos.

## Criterios de terminado (comprobables por máquina)

```bash
./gradlew spotlessCheck                                         # verde
./gradlew build -x test                                         # verde
./gradlew test --tests 'ec.uce.propuestas.common.ItemJerarquicoTest'   # verde
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'        # verde
./gradlew test --tests 'ec.uce.propuestas.cronograma.*'         # verde
./gradlew test                                                  # baseline + 0 fallos nuevos
git diff --check                                                # limpio
```

Y este `grep`, que debe salir vacío:

```bash
grep -rn 'Comparator.comparing(c -> c.item)\|Comparator.comparing(r -> r.item)' src/main/java
```

## Verificación manual

Con la aplicación levantada (`./gradlew --console=plain quarkusDev`) y la base
sembrada con `V004__seed_escenarios.sql`:

1. Autentícate como `john@uce.edu.ec` / `User123123`.
2. `GET /api/v1/presupuestos/{id}` del presupuesto vigente de "Cetro Médico
   Tulcán". Los `subcapitulos` del capítulo `1` deben venir
   `1.1, 1.2, …, 1.9, 1.10, 1.11, 1.12`, con `1.12` **el último**.
3. Los `rubros` del capítulo `2` deben venir `2.1, 2.2, 2.3, …, 2.9, 2.10, …`.
4. `GET /api/v1/cronogramas/{id}/vistas` del mismo presupuesto: el mismo orden
   en la jerarquía del Gantt y del valorizado.
5. `GET /api/v1/documentos/cronograma/{id}?formato=xlsx`: abre el XLSX y
   comprueba que las filas salen en ese mismo orden.

## Nota de mantenimiento

Dos reglas, y la segunda es la que de verdad importa:

1. **Un identificador jerárquico con puntos no se ordena como texto** — ni en
   Java ni en `ORDER BY`. Usa `ItemJerarquico.ORDEN`.
2. **Un comentario que afirma que dos órdenes coinciden no es una prueba de que
   coincidan.** El Javadoc de `PresupuestoMapper` lo afirmaba, citaba cuatro
   ejemplos que no lo destapan, y sobrevivió a todas las revisiones. Cuando
   escribas «X coincide con Y», o lo prueba un test o no lo escribas.

El test del paso 4 usa **doce** hermanos por ese motivo: cualquier test con
menos de diez pasa con el bug puesto.
