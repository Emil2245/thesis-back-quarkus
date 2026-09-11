# Plan backend 004 — Crear y vincular un APU manual completo

**Estado:** DONE (2026-09-10) · **Prioridad:** P0 · **Depende de:** backend 003

## 01. Resultado observable

Una petición crea cabecera, secciones y filas editables de un APU, lo vincula al capítulo explícito o a la última hoja con cantidad `1.000000`, recalcula una vez y devuelve el presupuesto final. Un error en cualquier fila revierte todo.

## 02. Contrato

```http
POST /api/v1/presupuestos/{presupuestoId}/apus/completo
```

Body propuesto:

```json
{
  "capituloId": null,
  "codigo": null,
  "descripcion": "Hormigón simple",
  "unidad": "m3",
  "porcentajeIndirecto": null,
  "detalles": [
    {
      "seccionTipo": "MATERIAL",
      "insumoId": "<UUIDv7>",
      "cantidad": "1.000000",
      "rendimiento": null
    }
  ]
}
```

- `codigo` nulo/blanco = autogenerar; no duplicado cuando viene.
- `porcentajeIndirecto` nulo/omitido = heredar; validar el rango canónico cuando viene.
- `detalles`: al menos uno, máximo 200; reutilizar reglas de `ApuDetalleCrearRequest`. `cantidad` y `rendimiento` conservan la representación Decimal aceptada por el contrato vigente; los campos editables pueden entrar como string decimal y nunca se convierten en totales del cliente.
- No aceptar HM manual, totales, costos, precios derivados, orden arbitrario ni `plantillaId`.
- El orden de la lista define orden contiguo dentro de cada sección; secciones canónicas se crean siempre.
- `rendimiento` solo donde la regla vigente lo permite.
- Response 201: `{apu: ApuResponse, presupuesto: PresupuestoResponse}`.

## 03. Reuso del plan 003

- Reusar resolución de owner, destino implícito a última hoja, creación append-only de rubro, lock y consolidación final.
- Extraer una primitiva interna para crear cabecera/secciones sin disparar recálculo prematuro.
- Reusar la resolución/copia al usar de insumos del flujo P-21; todo detalle debe terminar apuntando a base PROYECTO.
- Crear HM exactamente con la regla canónica existente, no desde el request.

## 04. RED crítico

- Código automático y manual.
- Filas de las cuatro secciones, orden estable y HM protegida.
- Rendimiento permitido/rechazado por tipo.
- Insumo CENTRAL/PERSONAL se copia o reutiliza en PROYECTO según N04 §A9.
- Insumo ajeno, fila inválida o código duplicado revierte APU, filas, rubro y logs.
- Destino omitido usa última hoja profunda.
- Body con `costoDirecto`, subtotal o precio derivado → 400 por campo desconocido.
- Una sola consolidación/sincronización final.

## 05. Alcance

- DTO agregado nuevo y schema Jackson estricto según convenciones del proyecto.
- Resource/application service bajo APU o presupuesto, sin duplicar la orquestación del plan 003.
- Refactor mínimo de `ApuCrudService`/detalle para permitir una transacción agregada.
- Tests IT focalizados.

Fuera de alcance: editar un APU existente, guardar automáticamente como plantilla PERSONAL, importar CSV, modificar motor o aceptar fórmulas del cliente.

## 06. Relación con Plan frontend 074

Este endpoint habilita la creación manual completa desde workspace. No resuelve la edición posterior de secciones vacías en `EditorApuPage`; ese residual debe reevaluarse, no darse por cerrado automáticamente.

## 07. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.*Completo*'
./gradlew test --tests 'ec.uce.propuestas.apu.*' --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.recalculo.*'
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

## 08. STOP y rollback

STOP si se requiere aceptar cálculos del cliente, crear HM manual, comprometer parcialmente o recalcular por fila. Rollback: retirar endpoint/DTO/tests y las primitivas exclusivas del flujo manual; preservar el lote del plan 003.

## 09. Cierre de ejecución

Implementación completada sin cambios en frontend, migraciones, `motor/` ni `recalculo/`:

- `POST /presupuestos/{presupuestoId}/apus/completo` crea el agregado completo dentro de una transacción owner-scoped, con cuatro secciones canónicas, HM server-authored, insumos materializados en PROYECTO, rubro append-only y una consolidación final `Alcance.Version`.
- La entrada usa un deserializador estricto acotado al endpoint; rechaza campos desconocidos tanto en la cabecera como en las filas, sin cambiar la compatibilidad de DTOs existentes.
- `ApuManualCompletoResourceIT`: 10/10 verde. Regresión `apu.*` + `presupuesto.*` + `recalculo.*`: 172/172 verde.
- Suite completa: 755 tests = 752 verdes + 2 residuales aceptados (GM-19/GM-20) + 1 omitido (GM-24), 0 errores. `spotlessCheck`, `build -x test` y `git diff --check`: PASS.
- `graphify update .`: 4.799 nodos, 15.089 aristas y 201 comunidades.
- No se realizó commit ni push.
