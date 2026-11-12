# 02 — Build Gradle y generación documental separada

> **Retirado en etapa 05 (2026-11):** los GET de bytes `GET /documentos/{presupuesto|apus|cronograma|especificaciones-tecnicas}/{id}`, sus servicios de descarga y los *writers* XLSX/PDF/MSPDI de Core ya no existen; la descarga es `POST …/exportaciones` + ticket de Core. Este documento conserva el estado histórico; ver [etapa 05](05-retiro-descargas-legadas.md).

**Objetivo:** obtener dos aplicaciones Quarkus construibles de forma independiente dentro de `thesis-back-quarkus/`, conservando la funcionalidad documental que puede migrarse sin redefinir todavía la garantía temporal del cronograma.

**Entrada:** contrato y resultado de factibilidad del [plan 01](01-factibilidad-y-contratos.md), reconciliados con `thesis-docs`.

## Acciones de esta etapa

1. Convertir el build único en multiproyecto: `settings.gradle.kts` incluye `:core` y `:documentos` (nombres sujetos a convención); cada aplicación tiene `build.gradle.kts`, `application.yml`, tests, puerto/imagen y dependencias propias. Conservar wrapper y tareas raíz de build/verificación. Un módulo compartido de **contratos mínimos**, si hiciera falta, no es un tercer microservicio; evitar dependencia de Documentos hacia código JPA de Core.
2. Mover dominio, seguridad, motor, repositorios, PostgreSQL y **única autoridad Flyway** a Core. Documentos no lleva datasource, Panache, Flyway ni entidades/repositories de negocio. Verificar que ambos proyectos arrancan y se prueban por separado y que CI construye dos artefactos; preservar cobertura y comportamiento de Core.
3. Crear la frontera: Core resuelve JWT/usuario, propiedad, preflight y captura; Documentos recibe **proyecciones serializables, inmutables y listas para render**, no entidades ORM, IDs internos ni consultas diferidas. El endpoint interno exige JWT de servicio Core→Documentos (`iss=core`, `aud=documentos`, rol dedicado, `exp` ≤60 s), con clave distinta del JWT de usuario; prueba rechazos de JWT de usuario/servicio inválido, formato/versión no admitidos y límites medidos de tamaño/tiempo. TLS interno y NetworkPolicy se prueban en despliegue del plan 03. Mantener cálculos canónicos y parámetros efectivos en Core; los writers de Documentos solo presentan los valores proyectados.
4. Migrar primero presupuesto y APU (XLSX/PDF), cuyos snapshots ya se capturan en transacción `REQUIRES_NEW` de solo lectura y cuyos writers ya operan fuera de ella. Comprobar que no se agrega auditoría de exportación por accidente. Migrar después especificaciones DOCX: actualmente lectura, generación y auditoría comparten transacción; separar captura/render cambia esa frontera y exige comprobar éxito/fallo y registrar el evento solo después del resultado acordado.
5. Separar generación de bytes de transporte/almacenamiento mediante una costura justificada por los **dos adaptadores reales** de la transición (prueba y S3 previsto); no duplicar reglas de archivo, nombres, formato o errores. Verificar con pruebas de contrato y comparaciones de contenido/estructura por formato, no solo por coincidencia binaria.

## Entregables / puerta para el plan 03

- `:core` y `:documentos` compilan, arrancan y pasan pruebas focales por separado; migraciones solo en Core y Documentos probado sin PostgreSQL.
- Presupuesto/APU/especificaciones se producen desde DTO contractuales, sin lecturas de base ni recálculos de negocio en Documentos; preflight y rechazo impiden invocarlo.
- Los cambios de archivos, tests y documentación canónica van junto al comportamiento al implementar esta etapa. No declarar terminada una exportación solo por devolver bytes si el contrato ya exige almacenamiento confirmado.

## Fuera de esta etapa

No desplegar URLs S3 ni cambiar aún el frontend; no retirar cronograma de Core en esta etapa. Durante la transición, la ruta pública vigente debe seguir funcionando o contar con la migración compatible prevista en el plan 01.

## Evidencia local de cierre

- `./gradlew build -PexcludeKnownResiduals` terminó correctamente: Core 973 pruebas
  (0 fallos, 1 omitida), Documentos 24 (0 fallos); incluye Spotless y builds Quarkus.
- La comprobación opt-in de `RealDocumentosGetIT` pasó (1 prueba): Core bajo
  `@QuarkusTest` con PostgreSQL aislado descargó presupuesto y APU en PDF/XLSX,
  y especificaciones en DOCX desde la **aplicación Documentos empaquetada**,
  autenticada con JWT de servicio efímero. Comprueba además el evento DOCX tras
  recibir bytes. No son dos servicios desplegados, TLS, Pod Identity ni navegador.
- `scripts/check-document-contract.sh` agotó 30 segundos en su intento anterior;
  su resultado de ejecución local no está acreditado y no se reintentó. La
  verificación independiente del slice de compatibilidad de DTO no estuvo
  disponible por cuota. CI en vivo, build nativo y despliegue no se ejecutaron.
- El PDF **del manual** permanece diferido a petición del usuario. No confundirlo
  con los PDF de presupuesto/APU, cubiertos por la comprobación anterior.

**Referencias:** [índice](README.md), [`settings.gradle.kts`](../../settings.gradle.kts), [`build.gradle.kts`](../../build.gradle.kts), [`ProyeccionDocumentoService`](../../core/src/main/java/ec/uce/propuestas/documento/exportacion/ProyeccionDocumentoService.java), [diseño canónico](../../../thesis-docs/plan/architecture/08-codebase-design.md).
