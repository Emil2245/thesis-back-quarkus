# 05 — Retiro de las descargas legadas por GET de bytes

**Estado:** implementado en local (2026-11) en `feat/microservices-stage05`. Registro de tareas, comandos y evidencia: [`odd/tasks/microservices-stage05.md`](../../odd/tasks/microservices-stage05.md).

**Entrada:** etapas [03](03-s3-despliegue-y-cliente.md) y [04](04-cronograma-y-cierre.md) verificadas en local con Core, Documentos, frontend y FLoCI. El frontend ya usaba solo `POST …/exportaciones` y el ticket de Core para los cuatro documentos.

## Qué se retiró

- Los cuatro GET públicos de bytes: `GET /documentos/{presupuesto|apus|cronograma|especificaciones-tecnicas}/{presupuestoId}`. Ahora responden 404 (`LegacyByteDownloadRetiredIT`).
- En Core: `DocumentoResource`, los servicios `PresupuestoDescargaService`, `ApuDescargaService`, `CronogramaDescargaService`, `EspecificacionesTecnicasService` y `EspecificacionesAuditService`, los *gateways* de render remoto, `DocumentosHttpClient`, `ArchivoGenerado`, los siete *writers* XLSX/PDF/MSPDI, la fuente LiberationSans y las dependencias `poi-ooxml`, `openpdf` y `pdfbox` (test) de `core/build.gradle.kts`.
- En Documentos: las rutas internas de render síncrono que solo servían a esos GET (tarea T3 del registro).
- En el frontend: las funciones de descarga por `Blob` y su manejo de 409 por GET.
- En Bruno: `TC-31-05/06/07` y `TC-09-07c`. `TC-12-P42-01` produce `documento.exportado` con el POST.

## Qué se conserva

- Los tres preflight `GET /documentos/{presupuesto|apus|cronograma}/{id}/preflight`.
- `POST /documentos/{documento}/{id}/exportaciones` y `GET /documentos/descargas/{ticket}`.
- La auditoría D-13 `documento.exportado`, que ahora emite solo `ExportReservationService.finalizeAudit` (DOCX) y `finalizeCronogramaAudit` (XLSX/PDF/MSPDI). Presupuesto y APUs no emiten, igual que antes.

## Cambios visibles

- Desaparecen los nombres de archivo saneados del GET y la cabecera `X-Cronograma-Desactualizado`; el aviso de cronograma desactualizado viaja en `warnings` de la respuesta JSON del POST.
- La garantía TOCTOU del cronograma es la **captura consistente** de la etapa 04, no el bloqueo hasta generar los bytes.
- Sin Documentos o S3 no hay descarga: Core ya no tiene un camino de respaldo.

## Sigue pendiente (no forma parte de esta etapa)

Borrado físico a las 24 horas, réplicas y fallos, IAM/TLS/Ingress desplegados, suite Playwright y los residuales aceptados GM-19/GM-20. Los capítulos de la tesis que citan los GET retirados no se reescribieron; ver el registro.
