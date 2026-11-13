# Separación Core + Documentos

**Estado: etapas 01–05 cerradas en local (FLoCI), sin prueba de producción.** Los planes por etapa se retiraron del repositorio una vez ejecutados; el historial git conserva su detalle. El contrato vigente vive en `thesis-docs/plan/architecture/07-api-contract.md` y el diseño de módulos en `08-codebase-design.md`.

Son **dos servicios Quarkus en un build Gradle multiproyecto** (`core`, `documentos`, más `documentos-contract`), no dos repositorios. Core conserva PostgreSQL, Flyway, las reglas de negocio y la autorización; Documentos recibe proyecciones inmutables, renderiza y guarda el archivo en un bucket privado S3, sin acceso a la base.

## Decisiones tomadas

- Dos servicios y una sola base PostgreSQL; Core es el único dueño de tablas, migraciones y autorización del usuario.
- Descarga: `POST /documentos/{documento}/{id}/exportaciones` (idempotente con `Idempotency-Key`) captura datos en Core, Documentos renderiza y sube a S3, y Core entrega un ticket de un solo uso de **5 minutos** que transmite el objeto privado por proxy. El objeto se retiene **24 horas**.
- Los GET de bytes y los *writers* de Core se retiraron en la etapa 05; ya no existen.
- Traefik enruta; Kubernetes ofrece descubrimiento y réplicas. No se agrega Consul.
- El bucket de respaldo existente no se reutiliza para archivos de usuarios.
- Si el contrato vigente contradice una propuesta de estos planes, detenerse y acordar la actualización en `thesis-docs`.

## Sigue pendiente

1. Textos de la tesis que aún citan los GET retirados (se tratan como registro de lo evaluado).
2. Verificaciones no ejecutadas: k6 (el despliegue ya usa POST + `Idempotency-Key` + ticket, pero k6 no está instalado) y ejecución en vivo de Bruno y Playwright.
3. Especificaciones técnicas DOCX: solo cubiertas por tests automáticos; falta una prueba con un APU que tenga ET (en la prueba local respondió 400 de negocio).
4. GM-19 y GM-20: residuales aceptados (Plan 014), sin diagnóstico de causa.
5. Borrado físico de objetos S3 a las 24 horas, pruebas con varias réplicas y fallos, IAM/TLS/Ingress reales y despliegue en AWS/producción (requiere autorización explícita).
