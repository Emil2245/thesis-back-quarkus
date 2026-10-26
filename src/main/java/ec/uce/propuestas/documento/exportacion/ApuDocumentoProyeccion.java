package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ItemJerarquico;
import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.FilaCalculada;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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

    /**
     * Fila impresa. {@code costo} y {@code costoHora} son los importes que se muestran: salen del
     * resultado del motor cuando el APU está conciliado (ver {@link #proyectar}); si no, los persistidos.
     */
    public record Fila(
            SnapshotDocumento.Detalle detalle,
            String descripcion,
            BigDecimal porcentajeHerramientaMenor,
            BigDecimal costo,
            BigDecimal costoHora) {
        public Fila(SnapshotDocumento.Detalle detalle, String descripcion, BigDecimal porcentajeHerramientaMenor) {
            this(detalle, descripcion, porcentajeHerramientaMenor, detalle.costo(), detalle.costoHora());
        }
    }

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
            var motor = conciliar(a);
            boolean hayDetalles =
                    a.secciones().stream().anyMatch(sec -> !sec.detalles().isEmpty());
            // Fixed documentary M/N/O/P order, irrespective of captured section orden.
            for (var tipo :
                    List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE)) {
                var ss = a.secciones().stream().filter(s -> s.tipo() == tipo).toList();
                if (ss.size() > 1) throw new IllegalArgumentException("Seccion APU ambigua");
                var s = ss.isEmpty() ? null : ss.getFirst();
                var detalles = s == null ? List.<SnapshotDocumento.Detalle>of() : s.detalles();
                // El subtotal impreso sigue siendo el persistido (contrato APU05): con la derivación fresca
                // (versión recién calculada) coincide con la suma de las filas del motor.
                var subtotal = s == null ? BigDecimal.ZERO : s.subtotal();
                subtotales.add(new Subtotal(tipo, subtotal));
                if (detalles.isEmpty() && !p.mostrarSeccionesVacias()) continue;
                var filas = new ArrayList<Fila>(detalles.stream()
                        .sorted(Comparator.comparingInt(SnapshotDocumento.Detalle::orden)
                                .thenComparing(d -> d.id().toString()))
                        .map(d -> fila(d, p, motor == null ? null : motor.get(d.id())))
                        .toList());
                // CHK-05: todo APU imprime exactamente una fila Herramienta Menor en el bloque M. Si no tiene
                // fila persistida se sintetiza en la posición 0 con el valor del motor (sin HM fila el motor
                // no calcula HM, por lo que su costo es 0 y los subtotales no cambian).
                if (tipo == SeccionTipo.EQUIPO
                        && motor != null
                        && hayDetalles
                        && filas.stream().noneMatch(f -> f.detalle().herramientaMenor())) {
                    filas.addFirst(herramientaMenorSintetica(a, p, a.calculado().costoHm()));
                }
                bloques.add(new Bloque(
                        tipo,
                        ec.uce.propuestas.documento.ApuTextoPresentacion.tituloBloque(tipo, p.sufijosSeccionActivos()),
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

    private static final int ESCALA_PERSISTENCIA = 6;

    private static BigDecimal escala6(BigDecimal v) {
        return v.setScale(ESCALA_PERSISTENCIA, RoundingMode.HALF_UP);
    }

    private static String etiquetaHerramientaMenor(SnapshotDocumento.Parametros p) {
        // Decimal-point shift only for the percent label; no financial computation.
        return "Herramienta Menor "
                + p.porcentajeHerramientaMenor().movePointRight(2).toPlainString() + "%MO";
    }

    private static Fila fila(SnapshotDocumento.Detalle d, SnapshotDocumento.Parametros p, FilaCalculada motor) {
        BigDecimal costo = d.costo();
        BigDecimal costoHora = d.costoHora();
        if (motor != null) {
            costo = escala6(motor.costoFila());
            if (!d.herramientaMenor() && motor.costoHora() != null) costoHora = escala6(motor.costoHora());
        }
        return new Fila(
                d,
                d.herramientaMenor() ? etiquetaHerramientaMenor(p) : d.descripcion(),
                d.herramientaMenor() ? p.porcentajeHerramientaMenor() : null,
                costo,
                costoHora);
    }

    private static Fila herramientaMenorSintetica(
            SnapshotDocumento.Apu a, SnapshotDocumento.Parametros p, BigDecimal costoHm) {
        String etiqueta = etiquetaHerramientaMenor(p);
        var detalle = new SnapshotDocumento.Detalle(
                UUID.nameUUIDFromBytes(("herramienta-menor:" + a.id()).getBytes(StandardCharsets.UTF_8)),
                (short) 0,
                true,
                etiqueta,
                null,
                p.porcentajeHerramientaMenor().movePointRight(2),
                null,
                null,
                null,
                null,
                BigDecimal.ZERO.setScale(ESCALA_PERSISTENCIA),
                escala6(costoHm),
                null);
        return new Fila(detalle, etiqueta, p.porcentajeHerramientaMenor(), detalle.costo(), detalle.costoHora());
    }

    /**
     * Casa cada detalle capturado con su fila del motor (el motor ordena M con HM primero y luego N, O, P;
     * dentro de cada bloque conserva el orden de captura de las filas no HM). Devuelve {@code null} si las
     * cantidades de filas por bloque no coinciden: la captura de producción siempre las calcula juntas, así
     * que {@code null} solo ocurre con instantáneas armadas a mano; entonces se imprimen los valores
     * persistidos tal cual.
     */
    private static java.util.Map<UUID, FilaCalculada> conciliar(SnapshotDocumento.Apu a) {
        var porTipo = new java.util.EnumMap<SeccionTipo, List<FilaCalculada>>(SeccionTipo.class);
        for (var tipo : SeccionTipo.values()) porTipo.put(tipo, new ArrayList<>());
        FilaCalculada hm = null;
        int hmMotor = 0;
        for (var fc : a.calculado().filas()) {
            if (fc.esHerramientaMenor()) {
                hm = fc;
                hmMotor++;
            } else {
                porTipo.get(fc.seccion()).add(fc);
            }
        }
        var out = new HashMap<UUID, FilaCalculada>();
        int hmDetalles = 0;
        int casadas = 0;
        var vistos = new java.util.EnumMap<SeccionTipo, Boolean>(SeccionTipo.class);
        for (var s : a.secciones()) {
            if (vistos.put(s.tipo(), true) != null) return null;
            int indice = 0;
            for (var d : s.detalles()) {
                if (d.herramientaMenor()) {
                    if (s.tipo() != SeccionTipo.EQUIPO || hm == null) return null;
                    hmDetalles++;
                    out.put(d.id(), hm);
                } else {
                    var filas = porTipo.get(s.tipo());
                    if (indice >= filas.size()) return null;
                    out.put(d.id(), filas.get(indice++));
                    casadas++;
                }
            }
        }
        int esperadas = porTipo.values().stream().mapToInt(List::size).sum();
        if (casadas != esperadas || hmDetalles != hmMotor || hmDetalles > 1) return null;
        return out;
    }

    private static void recorrer(List<SnapshotDocumento.Capitulo> capitulos, List<SnapshotDocumento.Rubro> rubros) {
        for (var c : capitulos) {
            rubros.addAll(c.rubros());
            recorrer(c.hijos(), rubros);
        }
    }
}
