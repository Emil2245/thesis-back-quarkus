# Plan 07 — Uniformar UUIDv7 en las APIs actuales

## Resultado esperado

Todas las fronteras REST de los módulos actuales reciben y devuelven UUIDv7 públicos. Los BIGINT permanecen como detalle interno de persistencia y nunca aparecen en paths o JSON.

## Dependencias

Completar primero los planes [04](./04-plantillas-apu.md), [05](./05-administracion-bases.md) y [06](./06-plantillas-proyecto.md), para no migrar dos veces contratos recién creados.

## Alcance

### Incluye

- Auditar `proyecto`, firmantes, `insumo`, bases, `presupuesto`, `plantilla`, `documento` y referencias anidadas.
- Migrar paths y DTOs actuales que todavía usan `Long` público.
- Resolver UUID + propietario a BIGINT en repositories.
- Respuestas 400/404 coherentes.
- Pruebas de contrato por módulo.

### No incluye

- Cambiar PK/FK internas de BIGINT a UUID.
- Modificar tablas que ya tienen `public_id` correcto.
- Crear endpoints nuevos ajenos a la migración.
- Cambiar los IDs internos usados por el motor puro.

## Reglas

1. Los paths públicos reciben UUIDv7 como `String` y validan con `UuidV7`.
2. Los repositories resuelven UUID + owner a BIGINT una sola vez por frontera de servicio.
3. JSON usa el nombre semántico `id`, no `publicId` ni `public_id`.
4. Recurso ajeno o inexistente devuelve 404.
5. UUID malformado o que no es v7 devuelve 400 con tipo `validacion`.
6. Joins y claves foráneas permanecen BIGINT.

## Pasos

1. Crear una matriz de endpoints actuales por módulo con tipo de ID de entrada y salida.
2. Marcar los endpoints ya alineados para evitar cambios innecesarios, especialmente APU/detalle.
3. Migrar un módulo a la vez en este orden:
   1. proyecto y firmantes;
   2. insumo y bases;
   3. presupuesto;
   4. plantilla;
   5. documento.
4. Para cada módulo:
   - ajustar DTOs y mappers;
   - añadir lookup por `publicId` + owner en repository;
   - mantener BIGINT después de resolver la frontera;
   - adaptar tests antes de pasar al siguiente módulo.
5. Revisar referencias anidadas y Location headers para que tampoco filtren BIGINT.
6. Buscar usos públicos residuales de `Long` y nombres `publicId/public_id`.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.documento.*'
./gradlew test --tests 'ec.uce.propuestas.identifier.*'
./gradlew spotlessCheck
./gradlew build -x test

git diff --check
```

Complementar con búsquedas acotadas en `resource/` y DTOs públicos; no asumir que cualquier `Long` interno es un defecto.

## Criterios de terminado

- [ ] La matriz de endpoints no contiene IDs públicos BIGINT.
- [ ] Los responses usan `id` con UUIDv7.
- [ ] UUID inválido/no-v7 produce 400 `validacion`.
- [ ] Recurso ajeno produce 404.
- [ ] PK, FK y joins internos siguen usando BIGINT.
- [ ] Las suites específicas de todos los módulos migrados pasan.

## Condiciones de parada

Detener y reportar si:

- una entidad pública no tiene `public_id` y la solución exige una migración no autorizada;
- el contrato canónico exige mantener un ID numérico público;
- la migración cambia semántica de propiedad o autorización más allá de owner-to-404.
