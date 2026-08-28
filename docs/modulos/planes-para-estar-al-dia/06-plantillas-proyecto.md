# Plan 06 — Implementar plantillas de proyecto

## Resultado esperado

Un proyecto puede crearse desde una plantilla estructural usando exclusivamente los paquetes existentes. El nuevo agregado no mantiene enlaces con el proyecto original y resuelve sus insumos con el mismo fallback de las plantillas APU.

## Dependencias

Completar primero:

- [Plan 04 — Plantillas de APU](./04-plantillas-apu.md).
- [Plan 05 — Administración de bases](./05-administracion-bases.md).

## Alcance

### Incluye

- `PlantillaProyectoService` y resource dentro de `plantilla`.
- Guardar snapshots estructurales sin precios ni cantidades de obra.
- Recrear proyecto, parámetros, capítulos, rubros/APUs y estructura aprobada.
- Reutilizar parser y fallback de P-26.
- Advertencias por insumos faltantes.

### No incluye

- Crear un módulo `plantilla-proyecto` nuevo.
- Copiar cronograma, historial, firmantes o datos operativos no aprobados.
- Duplicar el proyecto mediante referencias al agregado original.
- Recálculo global.

## Contrato esperado

```text
GET    /plantillas-proyecto
POST   /plantillas-proyecto
DELETE /plantillas-proyecto/{id}
POST   /proyectos/{proyectoId}/desde-plantilla/{plantillaId}
```

Antes de implementar, confirmar en el contrato canónico si la creación parte de un proyecto vacío existente o crea uno nuevo. No improvisar una segunda variante del endpoint.

## Pasos

1. Auditar `PlantillaProyecto`, su repository, el JSONB V004 y P-46 en las fuentes canónicas.
2. Definir el snapshot mínimo:
   - metadatos reutilizables autorizados;
   - parámetros;
   - árbol de capítulos;
   - rubros/APUs y sus filas;
   - sin precios ni cantidades de obra.
3. Implementar propiedad y visibilidad de plantillas sin ampliar roles.
4. Extraer o reutilizar el parser/fallback de P-26; no duplicar reglas de resolución de insumos.
5. Crear el agregado destino en una transacción:
   - proyecto y parámetros;
   - capítulos recursivos;
   - rubros y APUs independientes;
   - filas con insumos materializados en PROYECTO.
6. Generar advertencias para códigos faltantes y usar precio 0 según P-26.
7. Confirmar que no se copian cronograma, historial, firmantes ni relaciones con IDs del origen.
8. Probar rollback ante error intermedio y aislamiento entre propietarios.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew spotlessCheck

git diff --check
```

## Criterios de terminado

- [ ] El snapshot excluye precios, cantidades de obra y datos operativos no autorizados.
- [ ] La creación reconstruye el árbol y sus APUs sin enlazar IDs del origen.
- [ ] Los insumos quedan materializados en una base PROYECTO.
- [ ] Los faltantes producen advertencias y precio 0.
- [ ] Un fallo intermedio no deja un agregado parcial.
- [ ] No se creó ningún módulo nuevo de primer nivel.

## Condiciones de parada

Detener y reportar si:

- las fuentes canónicas no determinan si el endpoint crea o completa el proyecto destino;
- el snapshot requerido incluye cronograma, firmantes o historial;
- la implementación exige duplicar el fallback de P-26 en vez de reutilizarlo;
- aparece una dependencia con un módulo todavía inexistente.
