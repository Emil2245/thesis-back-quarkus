package ec.uce.propuestas.documentos.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Versioned, immutable render input. All IDs are public UUIDs; no entities or database keys cross the wire. */
public record DocumentProjectionV1(
        int schemaVersion, String documento, String formato, Map<String, String> opciones, Snapshot snapshot) {
    public static final int VERSION = 1;

    public DocumentProjectionV1 {
        opciones = Map.copyOf(Objects.requireNonNull(opciones, "opciones"));
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public record Snapshot(
            UUID presupuestoId,
            short version,
            boolean vigente,
            String notas,
            BigDecimal total,
            Proyecto proyecto,
            Parametros parametros,
            Display display,
            List<Firmante> firmantes,
            List<Capitulo> capitulos,
            List<Apu> apus) {
        public Snapshot {
            firmantes = List.copyOf(firmantes);
            capitulos = List.copyOf(capitulos);
            apus = List.copyOf(apus);
        }
    }

    public record Proyecto(
            UUID id,
            String nombre,
            String codigo,
            String descripcion,
            short anio,
            LocalDate fechaInicio,
            Short plazoEjecucion,
            String plazoUnidad,
            String estado,
            String direccionInstitucional,
            String subdireccionInstitucional) {}

    public record Parametros(
            BigDecimal porcentajeHerramientaMenor,
            BigDecimal porcentajeIndirecto,
            boolean ciIndividualHabilitado,
            BigDecimal iva,
            String moneda,
            boolean mostrarSeccionesVacias,
            boolean sufijosSeccionActivos,
            boolean mostrarSubtotalesSeccion,
            boolean mostrarSubtotalesPie,
            boolean mostrarNombreProyectoHeader,
            boolean enumerarApus,
            String mensajeFooter,
            String modoCodigoRubro) {}

    public record Display(int precision, int precisionPorcentaje) {}

    public record Firmante(UUID id, String nombre, String cargo, String rol, short orden) {}

    public record Capitulo(
            UUID id,
            String item,
            String descripcion,
            short orden,
            BigDecimal total,
            List<Capitulo> hijos,
            List<Rubro> rubros) {
        public Capitulo {
            hijos = List.copyOf(hijos);
            rubros = List.copyOf(rubros);
        }
    }

    public record Rubro(
            UUID id,
            UUID apuId,
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal precioTotal) {}

    public record Apu(
            UUID id,
            String codigo,
            String descripcion,
            String unidad,
            String especificacionTecnica,
            BigDecimal porcentajeIndirecto,
            BigDecimal costoDirecto,
            BigDecimal costoIndirecto,
            BigDecimal costoTotal,
            CalculatedApu calculado,
            List<Seccion> secciones) {
        public Apu {
            secciones = List.copyOf(secciones);
            Objects.requireNonNull(calculado, "calculado");
        }
    }

    public enum SeccionTipo {
        EQUIPO,
        MANO_OBRA,
        MATERIAL,
        TRANSPORTE
    }

    public record Seccion(SeccionTipo tipo, short orden, BigDecimal subtotal, List<Detalle> detalles) {
        public Seccion {
            detalles = List.copyOf(detalles);
        }
    }

    public record Detalle(
            UUID id,
            short orden,
            boolean herramientaMenor,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal rendimiento,
            BigDecimal tarifaJornal,
            BigDecimal precioUnitarioTarifa,
            BigDecimal precioEfectivo,
            BigDecimal costoHora,
            BigDecimal costo,
            Insumo insumo) {}

    public record Insumo(
            UUID id, String codigo, String tipo, String descripcion, String unidad, BigDecimal precioUnitario) {}

    public record CalculatedApu(
            String codigo,
            List<CalculatedRow> filas,
            BigDecimal subtotalM,
            BigDecimal subtotalN,
            BigDecimal subtotalO,
            BigDecimal subtotalP,
            BigDecimal costoHm,
            BigDecimal costoDirecto,
            BigDecimal costoIndirecto,
            BigDecimal costoTotal) {
        public CalculatedApu {
            filas = List.copyOf(filas);
        }
    }

    public record CalculatedRow(
            SeccionTipo seccion,
            boolean esHerramientaMenor,
            BigDecimal cantidad,
            BigDecimal rendimiento,
            BigDecimal precioUnitarioEfectivo,
            BigDecimal costoHora,
            BigDecimal costoFila) {}
}
