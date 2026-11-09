package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.util.Map;
import java.util.Objects;

/** Stable domain revision: render-time date is deliberately not part of the captured content identity. */
public final class ExportRevision {
    private ExportRevision() {}

    public static String hash(ObjectMapper json, SpecificationProjectionV1 specification) {
        Objects.requireNonNull(specification, "specification");
        return ExportFingerprint.hash(
                json,
                Map.of(
                        "schemaVersion", specification.schemaVersion(),
                        "presupuestoId", specification.presupuestoId(),
                        "nombreProyecto", specification.nombreProyecto(),
                        "anio", specification.anio(),
                        "titulo1", specification.titulo1(),
                        "titulo2", specification.titulo2(),
                        "especificaciones", specification.especificaciones()));
    }
}
