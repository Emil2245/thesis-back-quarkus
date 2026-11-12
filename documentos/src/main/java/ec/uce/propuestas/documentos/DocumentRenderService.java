package ec.uce.propuestas.documentos;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.render.ApuPdfRenderer;
import ec.uce.propuestas.documentos.render.ApuXlsxRenderer;
import ec.uce.propuestas.documentos.render.CronogramaMspdiRenderer;
import ec.uce.propuestas.documentos.render.CronogramaPdfRenderer;
import ec.uce.propuestas.documentos.render.CronogramaXlsxRenderer;
import ec.uce.propuestas.documentos.render.PresupuestoPdfRenderer;
import ec.uce.propuestas.documentos.render.PresupuestoXlsxRenderer;
import jakarta.ws.rs.BadRequestException;

/** Shared render validation for the existing byte GET adapter and private-object upload. */
final class DocumentRenderService {
    private DocumentRenderService() {}

    static Rendered render(DocumentProjectionV1 request) {
        if (request == null || request.schemaVersion() != DocumentProjectionV1.VERSION) {
            throw new BadRequestException("Unsupported document contract version");
        }
        boolean validFormat = ("presupuesto".equals(request.documento()) || "apus".equals(request.documento()))
                && ("xlsx".equals(request.formato()) || "pdf".equals(request.formato()));
        if (!validFormat
                || request.snapshot() == null
                || request.snapshot().presupuestoId() == null
                || request.snapshot().proyecto() == null
                || request.snapshot().proyecto().id() == null
                || request.snapshot().parametros() == null
                || request.snapshot().display() == null
                || request.snapshot().total() == null) {
            throw new BadRequestException("Invalid document projection");
        }
        if ("apus".equals(request.documento()) && request.snapshot().apuRender().isEmpty()) {
            throw new BadRequestException("Empty APU projection");
        }
        try {
            byte[] bytes = switch (request.documento() + ":" + request.formato()) {
                case "presupuesto:xlsx" -> PresupuestoXlsxRenderer.renderizar(request);
                case "presupuesto:pdf" -> PresupuestoPdfRenderer.renderizar(request);
                case "apus:xlsx" -> ApuXlsxRenderer.renderizar(request);
                case "apus:pdf" -> ApuPdfRenderer.renderizar(request);
                default -> throw new BadRequestException("Unsupported document format");
            };
            String mimeType = "pdf".equals(request.formato())
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            return new Rendered(bytes, mimeType);
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Invalid document projection");
        }
    }

    static Rendered render(CronogramaProjectionV1 request) {
        if (request == null || request.schemaVersion() != CronogramaProjectionV1.VERSION) {
            throw new BadRequestException("Unsupported cronograma contract version");
        }
        try {
            return switch (request.format()) {
                case "xlsx" ->
                    new Rendered(
                            CronogramaXlsxRenderer.renderizar(request),
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                case "pdf" -> new Rendered(CronogramaPdfRenderer.renderizar(request), "application/pdf");
                case "mspdi" -> new Rendered(CronogramaMspdiRenderer.renderizar(request), "application/xml");
                default -> throw new BadRequestException("Unsupported cronograma export format");
            };
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Invalid cronograma projection");
        }
    }

    record Rendered(byte[] bytes, String mimeType) {}
}
