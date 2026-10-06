# 04 — Integrar selección y descarga solo después del gate backend completo

**Estado: PROPUESTO, BLOQUEADO por gates 01–03.** No implementar este plan hasta
que estén terminadas todas las capacidades, pruebas y compatibilidad visual del
[gate backend integral](03-backend-apus.md). El frontend no renderiza documentos
ni agrega fórmulas o aprobaciones de presupuesto.

## Evidencia actual y dependencia

[useExportar.ts](../../../../thesis-front-react/src/features/exportar/hooks/useExportar.ts)
solo descarga especificaciones técnicas y cronograma; su comentario explícitamente
indica que presupuesto/APUs aún no existen. Reutiliza `descargar` y guarda Blob
con nombre recibido, revocando object URL; dispone de validación P-32 y preflight
cronograma. Estos hechos no acreditan nuestras rutas propuestas como implementadas.

Entrada: contrato final y evidencia real de backend 03, no solo propuesta de 01;
selección de versión inequívoca en
[ExportPage](../../../../thesis-front-react/src/features/exportar/pages/ExportPage.tsx).
Releer lógica de selección real antes de editar, sin sustituir silenciosamente por
presupuesto vigente. Si falta versión, mostrar selección necesaria sin solicitud.

## UX y combinaciones admitidas

| Documento | Controles opcionales | Solicitud |
|---|---|---|
| Presupuesto | XLSX/PDF; en PDF A4 vertical default u horizontal | UUID presupuesto/version seleccionado |
| APUs referenciados | XLSX pestañas default o apilado; PDF A4 vertical | mismo UUID, sin selector de catálogo |
| Cronograma | formatos existentes; solo PDF papel A4 default/A3 horizontal | contrato compatible backend 01 |

No ofrecer MSPDI en presupuesto/APUs, pestañas en PDF, A3 para APUs/presupuesto,
archivos por APU, logo, nueva carátula, firmas digitales ni aprobación. Al cambiar
formato eliminar parámetros incompatibles, sin enviarlos vacíos o escondidos.
Puede mantener defaults sin obligar a abrir un selector; opciones adicionales
solo muestran capacidades efectivamente verificadas por backend.

## Tareas acotadas, pruebas antes de integración

- [ ] RED futuro hook: URL y query exactos con versión seleccionada (incluida no
      vigente), formatos/layouts válidos y descarga mediante helper existente.
- [ ] RED futuro pantalla: matriz completa, sin llamada si no hay UUID válido,
      cambio de versión/options invalida preflight y no reutiliza resultado viejo.
- [ ] GREEN futuro: extender hook existente con presupuesto/APUs y sus preflights;
      tipos/schemas/query keys específicos, no enum MSPDI compartido indiscriminado.
- [ ] Preflight presupuestario/APU con key UUID+documento+formato+opciones;
      validar respuesta en frontera con schemas como cronograma. P-32 mostrado
      como integridad; no confundirlo con borrador/desviación de cronograma.
- [ ] Mostrar bloqueos sin descargar; vacío/cero APUs tiene mensaje del contrato,
      no botón que exporta catálogo. Stale es advertencia no bloqueante y ofrece
      continuar sin marcar revisado ni inventar aprobación.
- [ ] Backend sigue siendo autoridad: si cambia entre preflight y descarga,
      409 muestra arrays/mensaje e invalida preflight de esa misma versión;
      400/404 y fallo red sin filtrar datos ni guardar JSON error como archivo.
- [ ] TRIANGULAR Blob/filename/mimetype: nombre backend y extensión coherente,
      fallback seguro por documento/formato si falta header; revocar object URL,
      estado cargando, evitar doble click, liberar estado tras error.
- [ ] Pruebas negativas: PDF+layout no generado, cambio XLSX→PDF limpia layout,
      cambio de versión durante solicitud no atribuye resultado a otra versión;
      descargar siempre corresponde al contexto capturado al hacer click.
- [ ] Mantener ET DOCX y cronograma XLSX/PDF/MSPDI actuales; cronograma A3 no
      debilita sus blockers. Navegación, accesibilidad teclado/labels y mensajes
      españoles se comprueban con mocks de contrato y sesión real autorizada.

## Superficie futura delimitada

En repositorio hermano `thesis-front-react`, exclusivamente:

- `src/features/exportar/hooks/useExportar.ts` y `pages/ExportPage.tsx`.
- `src/api/contract.ts`, `schemas.ts`, `queryKeys.ts` para contrato nuevo acotado.
- `src/features/cronograma/components/DescargaCronograma.tsx` si se expone A3 ahí.
- Pruebas correspondientes en `src/test/features/exportar/`,
  `src/test/features/cronograma/` y `src/test/features/presupuesto/` solo para guards
  de UUID/version afectados.

[Helper de request](../../../../thesis-front-react/src/api/request.ts) se reutiliza;
solo tocarlo si una prueba de descarga demuestra necesidad dentro de contrato
existente y se aprueba ese ajuste. No generadores API, paquetes, lockfile,
renderers client-side, fixtures backend ni escritura de muestras.

## Verificación futura y aceptación

Desde frontend; scripts confirmados en [package.json](../../../../thesis-front-react/package.json).
No ejecutados en esta redacción:

```bash
pnpm exec vitest run src/test/features/exportar
pnpm exec vitest run src/test/features/cronograma src/test/features/presupuesto
pnpm run typecheck
pnpm run lint
pnpm run build
git diff --check
```

- [ ] Test-first futuro observado y resultados registrados, sin fabricar RED/GREEN.
- [ ] Cinco combinaciones nuevas descargan versión correcta y cronograma A3/A4
      conserva contratos; screenshots/revisión manual solo en ejecución autorizada.
- [ ] Bloqueos/warnings/errores consistentes con backend, sin cálculos del cliente.
- [ ] Regresión ET y cronograma existentes pasa; no cambios de dominio/dependencias.

Rollback: ocultar/revertir solo nuevos controles y hooks de esta unidad con revisión
del controlador; mantener ET/cronograma original y backend ya cerrado. Un fallo
frontend no autoriza alterar importes, relajar preflight ni modificar fuentes.
