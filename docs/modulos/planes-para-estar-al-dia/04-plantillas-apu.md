# Plan 04 — Implementar plantillas de APU

## Resultado esperado

Un usuario puede guardar un APU como plantilla PERSONAL, administrar sus plantillas y crear otro APU desde una plantilla SISTEMA o PERSONAL. Los insumos se resuelven hacia una base PROYECTO y los faltantes generan advertencias, no enlaces ocultos.

## Dependencia

Completar primero el [Plan 03](./03-contrato-apu-actual.md).

## Alcance

### Incluye

- DTOs, resource y servicio de plantillas APU dentro del paquete `plantilla` existente.
- Listar SISTEMA y PERSONALES propias.
- Obtener, renombrar y eliminar una plantilla PERSONAL propia.
- Guardar snapshots JSONB sin precios.
- Crear un APU desde plantilla con fallback de insumos.
- Tolerar campos extra del seed V004.

### No incluye

- Nueva migración V005 para reseed.
- Compartir plantillas PERSONALES entre usuarios.
- Plantillas de proyecto.
- Crear un módulo nuevo.

## Contrato esperado

```text
GET    /plantillas-apu
GET    /plantillas-apu/{id}
PUT    /plantillas-apu/{id}
DELETE /plantillas-apu/{id}
POST   /apus/{id}/guardar-plantilla
POST   /presupuestos/{id}/apus  { plantillaId }
```

Los IDs públicos deben seguir la convención UUIDv7 ya vigente en APU. La uniformidad global se cierra en el Plan 07.

## Pasos

1. Auditar `PlantillaApu`, su repository, el formato JSONB de V004 y los contratos canónicos P-26.
2. Definir DTOs mínimos para listado, detalle, renombrado, guardado, creación y advertencias.
3. Implementar autorización:
   - SISTEMA: visible y no editable por usuarios;
   - PERSONAL: solo visible/editable/eliminable por su propietario;
   - recurso ajeno: 404.
4. Guardar un snapshot sin precios que preserve:
   - códigos de insumo;
   - cantidades y rendimientos;
   - sección y orden;
   - fila HM;
   - overrides explícitos permitidos.
5. Integrar `plantillaId` en la creación de APU sin duplicar la lógica normal de creación.
6. Resolver cada código mediante `ResolverInsumoProyectoService`:
   - ya existe en PROYECTO: reutilizar;
   - existe en CENTRAL/PERSONAL visible: copiar a PROYECTO;
   - no existe: crear fila con precio 0 y agregar `advertencias[]`.
7. Rechazar o ignorar de forma documentada campos JSONB desconocidos; no crear V005 solo por compatibilidad.
8. Probar propiedad, fallback, faltantes y atomicidad de la creación.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew spotlessCheck

git diff --check
```

## Criterios de terminado

- [ ] SISTEMA y PERSONALES propias aparecen en el listado correcto.
- [ ] Una plantilla PERSONAL ajena devuelve 404.
- [ ] El snapshot no guarda precios ni relaciones con el APU original.
- [ ] Crear desde plantilla materializa insumos en PROYECTO.
- [ ] Los códigos faltantes producen precio 0 y advertencias explícitas.
- [ ] V004 continúa legible sin crear V005.

## Condiciones de parada

Detener y reportar si:

- el snapshot canónico exige copiar precios;
- el fallback requiere enlazar el nuevo APU con insumos CENTRAL/PERSONAL;
- se necesita una migración para corregir un fixture upstream sin autorización;
- la implementación requiere un nuevo módulo de primer nivel.
