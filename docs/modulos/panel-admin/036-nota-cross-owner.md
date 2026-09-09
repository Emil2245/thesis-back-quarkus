# Plan 036 — alcance cross-owner de `desdeApuId`

## Decisión aplicada

`POST /admin/plantillas-apu` permite que un `SUPER_ADMIN` cree una plantilla
`SISTEMA` desde cualquier APU existente, independientemente del propietario del
proyecto origen.

La autorización ocurre primero en `PlantillaApuAdminResource` mediante
`@RolesAllowed("SUPER_ADMIN")`. Después, `PlantillaApuAdminService` usa el lookup
administrativo estrecho `ApuRepository.findByPublicId(UUID)`, sin owner-scope.
Los recursos de usuario continúan usando `findByPublicIdAndOwnerScope(...)`; no
se amplió su visibilidad.

## Snapshot compartido

`PlantillaApuService.serializarSnapshotDesdeApu(Apu)` expone únicamente la
serialización estructural que ya usaba el flujo PERSONAL. Ambos flujos delegan
al mismo `SnapshotApuMapper` price-free. El mapper permanece intacto y el
snapshot no contiene precios efectivos, IDs internos ni enlaces al APU origen.

## Límites

- Solo el recurso `SUPER_ADMIN` usa el lookup sin owner.
- La plantilla resultante siempre fija `tipo=SISTEMA` y `usuario_id=NULL`.
- `descripcion_rubro` es `TEXT`; no se impone un límite artificial.
- Campos server-authored o no declarados en POST/PUT se rechazan con 400.
- No se modificaron migraciones, `motor/` ni `recalculo/`.
