# Plan 043: La renumeración de rubros reordena el presupuesto en la base de datos

> **Instrucciones para quien ejecute**: sigue este plan paso a paso. Corre cada
> comando de verificación y confirma el resultado esperado antes de avanzar. Si
> ocurre algo listado en "Condiciones STOP", detente y repórtalo — no improvises.
> Al terminar, actualiza la fila de este plan en [`README.md`](README.md).
>
> **Comprobación de deriva (ejecutar primero)**:
> `git diff --stat 3ab7e53..HEAD -- src/main/java/ec/uce/propuestas/presupuesto/service/RubroService.java src/main/java/ec/uce/propuestas/presupuesto/service/CapituloService.java src/main/java/ec/uce/propuestas/presupuesto/repository/RubroRepository.java src/main/java/ec/uce/propuestas/common/ItemJerarquico.java`
> Si alguno cambió, compara los fragmentos de "Estado actual" contra el código
> real antes de continuar; si no coinciden, trátalo como condición STOP.

## Estado

- **Prioridad**: P0 — corrompe datos del usuario de forma silenciosa y permanente
- **Esfuerzo**: S
- **Riesgo**: MEDIUM — toca una escritura. El cambio en sí es de una línea por
  sitio, pero esas líneas deciden qué `item` se persiste.
- **Depende de**: el plan `042`, **ya integrado en esta rama** (commit
  `3ab7e53`). De él sale `ItemJerarquico`, que este plan reutiliza. Sin él, este
  plan no compila.
- **Categoría**: bug (corrupción de datos)
- **Planificado en**: commit `3ab7e53`, 2026-09-17

## Por qué importa

El plan `042` arregló cómo se **lee** el orden de los `item`. Quedaron dos sitios
que el plan no enumeró, y los encontró su ejecutor. Uno de los dos no es de
lectura: **escribe**.

`RubroService.compactarRubrosDelCapitulo` lee los rubros de un capítulo, los
ordena **lexicográficamente** y les reasigna el `item` según su posición en esa
lista:

```java
    public void compactarRubrosDelCapitulo(Long capituloId) {
        List<Rubro> rubros = rubroRepository.listarPorCapitulo(capituloId);
        …
        // Orden estable por item lexicográfico (desempata por id) — coincide
        // con la regla de renumerarArbol en CapituloService para que ambos
        // caminos produzcan la misma secuencia de items 1..n.
        rubros.sort(Comparator.comparing((Rubro r) -> r.item).thenComparing(r -> r.id));
        boolean cambios = false;
        for (int i = 0; i < rubros.size(); i++) {
            String objetivo = ordinalItem(capitulo.item, i + 1);
            if (!objetivo.equals(rubros.get(i).item)) {
```

Y `listarPorCapitulo` ya venía ordenando lexicográficamente desde SQL:

```java
    /** Plan 023 — devuelve los rubros de un capítulo ordenados por {@code item} ascendente. */
    public List<Rubro> listarPorCapitulo(Long capituloId) {
        return find("capituloId = :capituloId order by item", Parameters.with("capituloId", capituloId))
                .list();
    }
```

**Qué pasa en la práctica.** El capítulo `2 · SISTEMA ELECTRICO` del presupuesto
"Cetro Médico Tulcán" tiene **51 rubros**, `2.1 … 2.51`. Están bien en la base.
En cuanto alguien añade o borra un rubro de ese capítulo —`compactarRubrosDelCapitulo`
se llama en los tres caminos, `RubroService` líneas 130, 170 y 235— ocurre esto:

| `item` actual | posición lexicográfica | `item` que se escribe |
|---|---|---|
| `2.1`  | 1  | `2.1` |
| `2.10` | 2  | **`2.2`** |
| `2.11` | 3  | **`2.3`** |
| `2.12` | 4  | **`2.4`** |
| …      | …  | … |
| `2.2`  | 12 | **`2.12`** |
| `2.20` | 13 | **`2.13`** |

El rubro que era `2.10` pasa a llamarse `2.2`, y el que era `2.2` pasa a `2.12`.
**No es un problema de visualización: el `item` se persiste.** El orden de los
rubros del presupuesto queda permanentemente barajado en la base de datos, y con
él el orden de las filas del documento SERCOP que se exporta. El usuario no
recibe ningún aviso.

La guarda `if (!objetivo.equals(...))` no protege de nada aquí: los valores **sí**
son distintos, así que la escritura ocurre.

El mismo defecto, en el mismo patrón, en `CapituloService.normalizarItemsDeRubros`
(llamado desde `renumerarArbol`, línea 327):

```java
            rubros.sort(Comparator.comparing((Rubro r) -> r.item).thenComparing(r -> r.id));
            for (int i = 0; i < rubros.size(); i++) {
                String objetivo = c.item + "." + (i + 1);
```

Fíjate en el comentario de `compactarRubrosDelCapitulo`: dice que el orden
lexicográfico «coincide con la regla de `renumerarArbol`». Coincide, sí — **las
dos están mal de la misma manera**. Es la tercera vez en esta tanda que un
comentario afirma una equivalencia que no se cumple con dos cifras.

### El tercer sitio, y por qué va aparte

`recalculo/internal/VersionSnapshotBuilder.java:187` también hace
`order by item`. Ése **sólo alimenta sumas** del motor, y la suma de `BigDecimal`
no depende del orden, así que no corrompe nada. Se arregla igual por coherencia,
pero con cuidado: el motor está cerrado por decisión (Plan 014) y **no se
reabre**. Cambiar el orden de una lista que se suma no es reabrirlo; si al
hacerlo se mueve algún importe, es que la suposición era falsa y hay que parar.

## Qué hay que hacer

### Paso 1 — Reproducir la corrupción con un test, antes de arreglar nada

Esto es TDD y aquí sí toca: es una escritura que corrompe datos, exactamente el
caso que [`CLAUDE.md`](../CLAUDE.md) reserva para prueba primero.

En `src/test/java/ec/uce/propuestas/presupuesto/resource/RubroResourceIT.java`
si existe; si no, en el IT de rubros que encuentres (búscalo:
`ls src/test/java/ec/uce/propuestas/presupuesto/resource/`). **Léelo entero
antes** y reutiliza sus helpers de fixture.

El test:

> Un capítulo con **doce** rubros `X.1 … X.12`. Se añade un rubro más (o se
> borra uno del medio). Después, los `item` de los rubros que ya existían
> **siguen identificando al mismo rubro**: el que era `X.10` sigue siendo el
> décimo por orden natural, no el segundo.

La forma más clara de afirmarlo es guardar antes el mapeo `publicId → item` y
comprobar después que el orden natural de los `item` respeta el mismo orden
relativo de `publicId` que antes. Doce, no tres: con menos de diez el defecto no
aparece.

Verificación:

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
```

Esperado en este punto: **el test nuevo falla**, y falla enseñando ítems
permutados. Si pasa a la primera, **para y repórtalo**: o el test no reproduce
el caso, o el defecto no es el que describe este plan.

### Paso 2 — Ordenar de forma natural en los dos sitios que escriben

Usa `ItemJerarquico.ORDEN`, que el plan `042` dejó en
`src/main/java/ec/uce/propuestas/common/ItemJerarquico.java`. **No escribas otro
comparador.**

**a) `RubroService.compactarRubrosDelCapitulo`:**

```java
        rubros.sort(Comparator.comparing((Rubro r) -> r.item, ItemJerarquico.ORDEN)
                .thenComparing(r -> r.id));
```

Y corrige el comentario de encima: ya no dice «lexicográfico», y la coincidencia
con `renumerarArbol` sigue siendo cierta porque el paso (b) la cambia igual.

**b) `CapituloService.normalizarItemsDeRubros`:** el mismo cambio.

**c) `RubroRepository.listarPorCapitulo`:** quita el `order by item` del JPQL y
ordena en memoria, como hizo el plan `042` con `CapituloRepository`. Postgres no
tiene orden natural para cadenas. Copia el resultado a un `ArrayList` antes de
ordenar: `find(...).list()` puede devolver una lista inmutable.

Documenta en el Javadoc que el orden es **natural**, no lexicográfico, y por qué
no se delega en SQL.

Verificación:

```bash
./gradlew spotlessApply && ./gradlew build -x test -Dorg.gradle.java.home=<jdk25>
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
```

Esperado: el test del paso 1 ahora en verde.

### Paso 3 — El sitio del motor, con cuidado

`recalculo/internal/VersionSnapshotBuilder.java:187`. Mismo cambio que (c):
fuera el `order by item`, orden natural en memoria.

Verificación, y esta importa más que las otras:

```bash
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

Los dos residuales aceptados del motor deben seguir **exactamente** iguales:
GM-19 `-$6.95` y GM-20 capítulo 1 `-$0.84`, con los mismos números absolutos
(`395108.37` y `158907.21`). **Si alguno se mueve un céntimo, revierte el paso 3
y repórtalo**: significaría que el resultado del motor sí depende del orden, que
es justo lo que este paso da por falso.

Si prefieres no arriesgar, **es aceptable saltarse el paso 3** y reportarlo: no
corrompe nada y puede ir en su propio plan. Los pasos 1 y 2 son los que importan.

### Paso 4 — Regresión

```bash
./gradlew test --console=plain
```

**Línea base en esta rama: `776 tests, 2 failed, 1 skipped, 0 errors`**, donde
los 2 rojos son GM-19 y GM-20 y el skipped es GM-24. Mídela tú antes de empezar
y reporta el delta.

**Antes de medir nada, copia las claves JWT de dev**
(`src/main/resources/META-INF/resources/{privateKey,publicKey}.pem`) desde
`C:\Users\emilv\OneDrive\Documents\.thesis\thesis-back-quarkus\src\main\resources\META-INF\resources\`:
están en `.gitignore`, no viajan al worktree, y sin ellas la suite entera
revienta con cientos de `MalformedURLException` y el baseline sale envenenado.

Y `./gradlew build -x test` necesita `-Dorg.gradle.java.home=<el jdk 25 de
~/.gradle/jdks>`: el toolchain compila a Java 25 y el daemon corre sobre el JDK
21 del PATH. Es preexistente; **no cambies `build.gradle.kts`** para arreglarlo.

## Condiciones STOP

- **Si el test del paso 1 pasa a la primera**, para. No has reproducido el
  defecto y arreglar a ciegas es peor que no arreglar.
- **Si algún importe se mueve** en cualquier momento, para y revierte ese paso.
  Este plan no puede cambiar ni un céntimo.
- **Si al ordenar naturalmente algún test existente de renumeración se pone
  rojo**, léelo con cuidado antes de tocarlo: puede estar codificando la
  permutación mala. Si es así, actualízalo y **di su nombre y qué esperaba** en
  el informe. Si no lo tienes claro, para y pregunta.
- **No añadas migración Flyway.** Este plan **no** repara los datos ya
  corrompidos, si los hubiera; sólo deja de corromperlos. Una migración de
  reparación es otra decisión, y se toma con el usuario delante.
- No toques `CapituloService.renumerarArbol` más allá del `sort` de
  `normalizarItemsDeRubros`, ni el aparcado de items bajo el prefijo `~<id>`
  (Plan 022): esa mecánica es correcta y delicada.

## Fuera de alcance

- Reparar datos ya barajados en una base existente.
- El motor de cálculo más allá del `sort` del paso 3.
- `CapituloService.renumerarArbol` y su aparcado de items.
- El frontend.

## Criterios de terminado (comprobables por máquina)

```bash
./gradlew spotlessCheck
./gradlew build -x test -Dorg.gradle.java.home=<jdk25>
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
./gradlew test --tests 'ec.uce.propuestas.motor.*'
./gradlew test
git diff --check
```

Y este grep, que debe salir vacío:

```bash
grep -rn "order by item" src/main/java
```

(Si te saltas el paso 3, quedará la línea de `VersionSnapshotBuilder`. Dilo en el
informe.)

## Nota de mantenimiento

Es la **tercera** vez en esta tanda que aparece el mismo error, y conviene
nombrarlo: en este código hay comentarios que afirman equivalencias entre
órdenes —«lexicográfico coincide con natural», «coincide con la regla de
renumerarArbol»— que **nadie comprobó con dos cifras**, porque con uno a nueve
hermanos sí coinciden.

La regla: **un identificador jerárquico con puntos se ordena con
`ItemJerarquico.ORDEN`, nunca como texto y nunca con `ORDER BY` de SQL.** Y
cualquier test que quiera demostrarlo necesita **diez o más hermanos**; con
menos, pasa igual con el bug puesto.
