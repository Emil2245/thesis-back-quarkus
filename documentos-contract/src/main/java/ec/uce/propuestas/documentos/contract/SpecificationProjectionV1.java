package ec.uce.propuestas.documentos.contract;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, technical-only DOCX input captured by Core; no internal database IDs or prices. */
public record SpecificationProjectionV1(
        int schemaVersion,
        UUID presupuestoId,
        String nombreProyecto,
        short anio,
        String titulo1,
        String titulo2,
        String fechaExportacion,
        List<Specification> especificaciones) {
    public static final int VERSION = 1;

    public SpecificationProjectionV1 {
        Objects.requireNonNull(presupuestoId, "presupuestoId");
        Objects.requireNonNull(nombreProyecto, "nombreProyecto");
        Objects.requireNonNull(titulo1, "titulo1");
        Objects.requireNonNull(titulo2, "titulo2");
        Objects.requireNonNull(fechaExportacion, "fechaExportacion");
        especificaciones = List.copyOf(especificaciones);
        if (especificaciones.isEmpty()) throw new IllegalArgumentException("Empty technical specifications");
    }

    public record Specification(String codigo, String descripcion, String unidad, String especificacionTecnica) {
        public Specification {
            Objects.requireNonNull(codigo, "codigo");
            Objects.requireNonNull(descripcion, "descripcion");
            Objects.requireNonNull(unidad, "unidad");
            if (especificacionTecnica == null || especificacionTecnica.isBlank())
                throw new IllegalArgumentException("Empty technical specification");
        }
    }
}
