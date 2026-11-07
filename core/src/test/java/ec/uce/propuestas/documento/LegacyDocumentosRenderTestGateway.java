package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** Test-only substitute for existing public GET fixtures; production never falls back to local writers. */
@Alternative
@IfBuildProperty(name = "documentos.test-local-render", stringValue = "true")
@Priority(1)
@ApplicationScoped
public class LegacyDocumentosRenderTestGateway implements DocumentosRenderGateway {
    @Override
    public byte[] render(SnapshotDocumento snapshot, String documento, OpcionesDocumento opciones) {
        if ("presupuesto".equals(documento)) {
            return "pdf".equals(opciones.formato())
                    ? PresupuestoPdfWriter.renderizar(snapshot, opciones)
                    : PresupuestoXlsxWriter.renderizar(snapshot, opciones);
        }
        if ("apus".equals(documento)) {
            var apus = ApuDocumentoProyeccion.proyectar(snapshot);
            return "pdf".equals(opciones.formato())
                    ? ApuPdfWriter.renderizar(apus, opciones)
                    : ApuXlsxWriter.renderizar(apus, opciones);
        }
        throw new IllegalArgumentException("Unsupported document kind");
    }
}
