# Plan 05 — Completar administración de bases

## Resultado esperado

Los usuarios pueden eliminar sus bases PERSONALES con aislamiento por propietario, y SUPER_ADMIN puede administrar el ciclo completo de bases CENTRALES, incluido archivar y borrar sin afectar las copias de proyecto.

## Dependencia

Completar primero el [Plan 01](./01-linea-base-y-decisiones.md). Puede ejecutarse en paralelo conceptual con los planes 02–04, pero los cambios deben aplicarse en una sola línea de trabajo.

## Alcance

### Incluye

- `DELETE /bases-personales/{id}`.
- Administración bajo `/admin/bases-centrales`.
- CRUD de bases e insumos centrales.
- Importación CSV central.
- Archivar y borrar según D-12.
- Pruebas de roles, propiedad y preservación de copias PROYECTO.

### No incluye

- Nuevos roles.
- Borrar insumos ya materializados en bases PROYECTO.
- Recálculo de APUs tras cambiar precios.
- Cambiar el parser CSV salvo que una prueba del contrato vigente lo requiera.

## Contrato mínimo

```text
DELETE /bases-personales/{id}

GET    /admin/bases-centrales
POST   /admin/bases-centrales
PUT    /admin/bases-centrales/{id}
DELETE /admin/bases-centrales/{id}
PUT    /admin/bases-centrales/{id}/archivar

# CRUD/importación de insumos bajo la base central correspondiente
```

Los paths exactos de insumos deben seguir `07-api-contract.md` y las convenciones del resource actual; no crear variantes duplicadas.

## Pasos

1. Auditar entities, repositories, services y resources de `insumo`, además del contrato REST canónico.
2. Implementar borrado PERSONAL con owner-to-404 y una política explícita para contenido asociado.
3. Crear o ampliar el resource administrativo con `@RolesAllowed("SUPER_ADMIN")`.
4. Implementar listado, creación y renombrado de bases CENTRALES.
5. Reutilizar servicios existentes para CRUD/importación de insumos centrales, evitando duplicar parser o reglas.
6. Implementar archivado:
   - `archivada=true`;
   - ocultar la base del catálogo normal;
   - conservarla para administración.
7. Implementar borrado solo después de archivar. No bloquear por referencias históricas, porque los proyectos usan copias PROYECTO.
8. Verificar que solo existen los roles `USUARIO` y `SUPER_ADMIN`.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew spotlessCheck
./gradlew build -x test

git diff --check
```

## Criterios de terminado

- [ ] Una base PERSONAL propia puede borrarse y una ajena responde 404.
- [ ] USUARIO no accede a endpoints administrativos.
- [ ] SUPER_ADMIN puede crear, renombrar, importar, archivar y borrar una central.
- [ ] Una central archivada desaparece del catálogo normal.
- [ ] Solo puede borrarse después de archivar.
- [ ] Borrar una central no modifica copias PROYECTO existentes.

## Condiciones de parada

Detener y reportar si:

- el schema actual impide el borrado D-12 y exige una migración no contemplada;
- se descubre que algún proyecto referencia directamente insumos CENTRALES/PERSONALES;
- el contrato propone un tercer rol o middleware roles;
- completar el flujo exige implementar recálculo global.
