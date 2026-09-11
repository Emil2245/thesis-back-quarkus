# Plan backend 003 — Aplicar plantillas y vincular rubros en un lote atómico

**Estado:** TODO · **Prioridad:** P0 · **Puede ejecutarse en paralelo con:** 001 y 002

## 01. Resultado observable

Una petición crea entre 1 y 20 APUs desde plantillas visibles, los vincula al mismo capítulo con cantidad `1.000000`, sincroniza/recalcula una vez y devuelve el presupuesto final. Cualquier fallo revierte el lote completo.

## 02. Contrato

```http
POST /api/v1/presupuestos/{presupuestoId}/rubros/desde-plantillas
Content-Type: application/json

{
  "capituloId": "<UUIDv7 opcional>",
  "plantillaIds": ["<UUIDv7>", "<UUIDv7>"]
}
```

- `plantillaIds`: 1..20, sin duplicados, orden significativo.
- `capituloId` omitido/null: última hoja del árbol.
- Cada plantilla produce un APU con código autogenerado, descripción/unidad de la plantilla y un rubro de cantidad `1.000000`.
- Response 201:

```json
{
  "presupuesto": {},
  "resultados": [
    {
      "plantillaId": "...",
      "plantillaNombre": "...",
      "apuId": "...",
      "codigo": "APU-001",
      "advertencias": []
    }
  ]
}
```

Advertencias por insumo faltante no rompen el lote; mantienen la semántica actual P-26 y quedan asociadas a su plantilla.

## 03. Semántica de última hoja

Resolver dentro del presupuesto del caller:

1. tomar el capítulo raíz de mayor `orden`;
2. si tiene hijos, tomar su hijo de mayor `orden`;
3. repetir hasta una hoja.

Sin capítulos → 409 `presupuesto-sin-capitulos`. Un `capituloId` explícito puede ser raíz o subcapítulo válido; no se sustituye silenciosamente por una hoja.

## 04. Atomicidad y rendimiento

Crear un application service nuevo bajo `presupuesto` con `@Transactional`:

1. resolver presupuesto/owner y destino;
2. precargar **todas** las plantillas con owner-scope antes de persistir;
3. validar duplicados y campos requeridos;
4. crear APUs y aplicar snapshots en orden;
5. crear rubros append-only con cantidad uno;
6. emitir `apu.creado` por cada APU dentro de la misma transacción;
7. ejecutar una sola consolidación `Alcance.Version` y una sola sincronización de cronograma al final;
8. mapear la respuesta final.

No llamar N veces a `ApuCrudService.crear` ni `RubroService.crear`, porque disparan recálculos. Extraer primitivas internas explícitas —por ejemplo `crearApuSinRecalculo(...)` y `crearRubroAppendOnlySinRecalculo(...)`— que preserven validaciones, HM, logs e items; al final ejecutar un único `recalcular(new Alcance.Version(presupuestoId))`. No crear `Alcance.Lote` ni tocar el motor.

Antes de calcular códigos/items, bloquear la fila `presupuesto` con `SELECT ... FOR UPDATE` mediante un helper de repository, siguiendo el lock pesimista existente del módulo. La prueba concurrente debe demostrar que dos lotes no generan códigos o items duplicados.

## 05. Error identificable

Extender `ErrorPayload` de forma retrocompatible con un campo opcional `detalles`, `@JsonInclude(NON_NULL)` y constructor explícito de dos argumentos. Una IT debe confirmar que errores existentes conservan exactamente `{codigo,mensaje}` sin `detalles:null`. Para fallos del lote incluir:

```json
{
  "codigo": "plantilla-lote-fallida",
  "mensaje": "No se pudo agregar la plantilla seleccionada en la posición 2.",
  "detalles": {"indice": 1, "plantillaId": "<id visible>"}
}
```

- Índice de detalles: 0-based para consumo técnico; mensaje humano: posición 1-based.
- Si la plantilla ya fue visible y precargada, puede incluirse `plantillaNombre`.
- Si es ajena/inexistente, responder 404 sin filtrar nombre; `indice` permite al cliente identificar su selección.
- Preservar status/códigos específicos cuando sean más útiles, pero siempre adjuntar índice de lote.

## 06. RED crítico

- Lote SISTEMA+PERSONAL propia conserva orden y crea N APUs/rubros.
- Una plantilla ajena/inexistente revierte todo y señala índice sin revelar datos.
- IDs duplicados, lista vacía y 21 elementos → 400.
- Advertencias no causan rollback y quedan agrupadas.
- Destino omitido usa última hoja profunda, no último raíz.
- Presupuesto sin capítulos → 409.
- Fallo inducido en el elemento 2 deja conteos de APU/rubro/log sin cambios.
- Consolidación y sincronización se invocan una vez, no N veces.
- Dos requests concurrentes preservan códigos e items únicos; usar el lock ya establecido para la versión/proyecto o documentar el lock mínimo.

## 07. Archivos candidatos

- DTOs nuevos en `presupuesto/dto/`.
- Resource nuevo o método cohesivo bajo `presupuesto/resource/`.
- Application service nuevo bajo `presupuesto/service/`.
- `CapituloRepository.ultimoHijo(presupuestoId, parentId)` con `ORDER BY orden DESC` y límite 1; iterarlo hasta hoja.
- Helpers acotados `crearApuSinRecalculo` y `crearRubroAppendOnlySinRecalculo` en `ApuCrudService`/`RubroService`, más lock de presupuesto en repository.
- `common/ErrorPayload.java` y `ProblemaException.java` solo para detalles retrocompatibles.
- Tests IT bajo `presupuesto/`.

No crear migraciones, no tocar V001–V010, FTS, seeds, motor ni catálogo D-13.

## 08. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*Plantilla*Lote*'
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.apu.*' --tests 'ec.uce.propuestas.plantilla.*' --tests 'ec.uce.propuestas.recalculo.*'
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

## 09. STOP y rollback

STOP si la única implementación viable recalcula N veces, permite parciales, amplía D-13 o filtra ownership. Rollback: retirar endpoint/DTO/service/helpers y extensión opcional de error; endpoints unitarios previos deben seguir verdes.
