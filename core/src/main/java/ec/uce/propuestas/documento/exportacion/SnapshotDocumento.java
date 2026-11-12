package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Captura interna: nunca es la respuesta HTTP ni contiene entidades/FKs internas. */
public record SnapshotDocumento(
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
        List<Apu> apus,
        PreflightDocumento preflight) {
    public SnapshotDocumento {
        firmantes = List.copyOf(firmantes);
        capitulos = List.copyOf(capitulos);
        apus = List.copyOf(apus);
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
            ApuCalculado calculado,
            List<Seccion> secciones) {
        public Apu {
            secciones = List.copyOf(secciones);
            // El resultado canónico copia filas; FilaCalculada solo contiene valores inmutables.
            calculado = new ApuCalculado(
                    calculado.codigo(),
                    calculado.filas(),
                    calculado.subtotalM(),
                    calculado.subtotalN(),
                    calculado.subtotalO(),
                    calculado.subtotalP(),
                    calculado.costoHm(),
                    calculado.costoDirecto(),
                    calculado.costoIndirecto(),
                    calculado.costoTotal());
        }
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
}
