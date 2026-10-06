package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ItemJerarquico;
import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Pure presentation boundary; never captures or calculates domain data. */
public final class ApuDocumentoProyeccion {
    private ApuDocumentoProyeccion() {}

    public record Documento(
            UUID presupuestoId,
            short version,
            SnapshotDocumento.Proyecto proyecto,
            SnapshotDocumento.Parametros parametros,
            SnapshotDocumento.Display display,
            List<SnapshotDocumento.Firmante> firmantes,
            List<Analisis> apus) {
        public Documento {
            firmantes = List.copyOf(firmantes);
            apus = List.copyOf(apus);
        }
    }

    public record Analisis(
            UUID id,
            String codigo,
            String etiquetaCodigo,
            String descripcion,
            String unidad,
            String especificacionTecnica,
            String nombreProyectoHeader,
            List<Bloque> bloques,
            Pie pie,
            ApuCalculado diagnostico) {
        public Analisis {
            bloques = List.copyOf(bloques);
        }
    }

    public record Bloque(
            SeccionTipo tipo,
            String etiqueta,
            Short ordenCapturado,
            List<Fila> filas,
            BigDecimal subtotal,
            boolean mostrarSubtotal) {
        public Bloque {
            filas = List.copyOf(filas);
        }
    }

    public record Fila(SnapshotDocumento.Detalle detalle, String descripcion, BigDecimal porcentajeHerramientaMenor) {}

    public record Subtotal(SeccionTipo tipo, BigDecimal valor) {}

    public record Pie(
            List<Subtotal> subtotales,
            BigDecimal porcentajeIndirecto,
            BigDecimal costoDirecto,
            BigDecimal costoIndirecto,
            BigDecimal costoTotal,
            BigDecimal valorOfertado,
            String mensaje) {
        public Pie {
            subtotales = List.copyOf(subtotales);
        }
    }

    public static Documento proyectar(SnapshotDocumento snapshot) {
        var p = snapshot.parametros();
        var parId = new HashMap<UUID, SnapshotDocumento.Apu>();
        for (var a : snapshot.apus()) {
            var anterior = parId.putIfAbsent(a.id(), a);
            if (anterior != null && !anterior.equals(a)) {
                throw new IllegalArgumentException("Identidad APU ambigua");
            }
        }
        var rubros = new ArrayList<SnapshotDocumento.Rubro>();
        recorrer(snapshot.capitulos(), rubros);
        rubros.sort(Comparator.comparing(SnapshotDocumento.Rubro::item, ItemJerarquico.ORDEN)
                .thenComparing(r -> r.id().toString()));
        var seleccion = new LinkedHashSet<UUID>();
        for (var r : rubros) seleccion.add(r.apuId());
        var analyses = new ArrayList<Analisis>();
        for (var id : seleccion) {
            var a = parId.get(id);
            if (a == null) throw new IllegalArgumentException("Referencia APU ilegible");
            var bloques = new ArrayList<Bloque>();
            var subtotales = new ArrayList<Subtotal>();
            // Fixed documentary M/N/O/P order, irrespective of captured section orden.
            for (var tipo :
                    List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE)) {
                var ss = a.secciones().stream().filter(s -> s.tipo() == tipo).toList();
                if (ss.size() > 1) throw new IllegalArgumentException("Seccion APU ambigua");
                var s = ss.isEmpty() ? null : ss.getFirst();
                var detalles = s == null ? List.<SnapshotDocumento.Detalle>of() : s.detalles();
                var subtotal = s == null ? BigDecimal.ZERO : s.subtotal();
                subtotales.add(new Subtotal(tipo, subtotal));
                if (detalles.isEmpty() && !p.mostrarSeccionesVacias()) continue;
                var filas = detalles.stream()
                        .sorted(Comparator.comparingInt(SnapshotDocumento.Detalle::orden)
                                .thenComparing(d -> d.id().toString()))
                        .map(d -> new Fila(
                                d,
                                d.herramientaMenor()
                                        // Decimal-point shift only for the percent label; no financial computation.
                                        ? "Herramienta Menor "
                                                + p.porcentajeHerramientaMenor()
                                                        .movePointRight(2)
                                                        .toPlainString() + "%MO"
                                        : d.descripcion(),
                                d.herramientaMenor() ? p.porcentajeHerramientaMenor() : null))
                        .toList();
                bloques.add(new Bloque(
                        tipo,
                        etiqueta(tipo, p.sufijosSeccionActivos()),
                        s == null ? null : s.orden(),
                        filas,
                        subtotal,
                        p.mostrarSubtotalesSeccion()));
            }
            // ProyectoCiService clears overrides when disabled; CRUD rejects new ones.
            // Honor the captured policy explicitly, including inconsistent historical overrides.
            var ci = p.ciIndividualHabilitado() && a.porcentajeIndirecto() != null
                    ? a.porcentajeIndirecto()
                    : p.porcentajeIndirecto();
            var pie = new Pie(
                    p.mostrarSubtotalesPie() ? subtotales : List.of(),
                    ci,
                    a.costoDirecto(),
                    a.costoIndirecto(),
                    a.costoTotal(),
                    a.costoTotal(),
                    p.mensajeFooter());
            analyses.add(new Analisis(
                    a.id(),
                    a.codigo(),
                    p.enumerarApus() ? (analyses.size() + 1) + " " + a.codigo() : a.codigo(),
                    a.descripcion(),
                    a.unidad(),
                    a.especificacionTecnica(),
                    p.mostrarNombreProyectoHeader() ? snapshot.proyecto().nombre() : null,
                    bloques,
                    pie,
                    a.calculado()));
        }
        return new Documento(
                snapshot.presupuestoId(),
                snapshot.version(),
                snapshot.proyecto(),
                p,
                snapshot.display(),
                snapshot.firmantes(),
                analyses);
    }

    private static void recorrer(List<SnapshotDocumento.Capitulo> capitulos, List<SnapshotDocumento.Rubro> rubros) {
        for (var c : capitulos) {
            rubros.addAll(c.rubros());
            recorrer(c.hijos(), rubros);
        }
    }

    private static String etiqueta(SeccionTipo tipo, boolean sufijos) {
        String nombre =
                switch (tipo) {
                    case EQUIPO -> "Equipos y herramientas";
                    case MANO_OBRA -> "Mano de obra";
                    case MATERIAL -> "Materiales";
                    case TRANSPORTE -> "Transporte";
                };
        String sufijo =
                switch (tipo) {
                    case EQUIPO -> "M";
                    case MANO_OBRA -> "N";
                    case MATERIAL -> "O";
                    case TRANSPORTE -> "P";
                };
        return sufijos ? nombre + " (" + sufijo + ")" : nombre;
    }
}
