package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuBlock;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuFooter;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuRow;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuView;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Detalle;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Display;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Firmante;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Proyecto;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.SeccionTipo;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Snapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 equivalents of the Core-side helpers the retired APU writer tests used to derive variants from a captured
 * document ({@code ApuDocumentoProyeccion.Documento}/{@code Analisis}/{@code Bloque}/{@code Fila}/{@code Pie}).
 * A Core {@code Fila(detalle, descripcion, pctHm)} printed the detail's own costo/costoHora; {@link #row} keeps that.
 */
final class ApuFixtures {
    private ApuFixtures() {}

    static DocumentProjectionV1 as(DocumentProjectionV1 source, String formato, Map<String, String> opciones) {
        return new DocumentProjectionV1(DocumentProjectionV1.VERSION, "apus", formato, opciones, source.snapshot());
    }

    static DocumentProjectionV1 xlsx(DocumentProjectionV1 source, String layout) {
        return as(source, "xlsx", Map.of("layout", layout));
    }

    static DocumentProjectionV1 pdf(DocumentProjectionV1 source) {
        return as(source, "pdf", Map.of());
    }

    static DocumentProjectionV1 with(
            DocumentProjectionV1 source,
            Proyecto proyecto,
            Display display,
            List<Firmante> firmantes,
            List<ApuView> apus) {
        Snapshot s = source.snapshot();
        var snapshot = new Snapshot(
                s.presupuestoId(),
                s.version(),
                s.vigente(),
                s.notas(),
                s.total(),
                proyecto == null ? s.proyecto() : proyecto,
                s.parametros(),
                display == null ? s.display() : display,
                firmantes == null ? s.firmantes() : firmantes,
                s.capitulos(),
                s.apus(),
                apus == null ? s.apuRender() : apus);
        return new DocumentProjectionV1(
                source.schemaVersion(), source.documento(), source.formato(), source.opciones(), snapshot);
    }

    static DocumentProjectionV1 display(DocumentProjectionV1 source, int money, int percent) {
        return with(source, null, new Display(money, percent), null, null);
    }

    static DocumentProjectionV1 apus(DocumentProjectionV1 source, List<ApuView> apus) {
        return with(source, null, null, null, apus);
    }

    static ApuView copy(
            ApuView a, String codigo, String etiqueta, String descripcion, List<ApuBlock> bloques, ApuFooter pie) {
        return new ApuView(a.id(), codigo, etiqueta, descripcion, a.unidad(), a.nombreProyectoHeader(), bloques, pie);
    }

    static ApuView copy(ApuView a, String codigo, String descripcion, List<ApuBlock> bloques, ApuFooter pie) {
        return copy(a, codigo, codigo, descripcion, bloques, pie);
    }

    static ApuRow row(Detalle detalle, String descripcion) {
        return new ApuRow(detalle, descripcion, null, detalle.costo(), detalle.costoHora());
    }

    static Detalle detail(
            Detalle v,
            UUID id,
            short orden,
            String descripcion,
            String unidad,
            BigDecimal precioEfectivo,
            BigDecimal costoHora,
            BigDecimal costo) {
        return new Detalle(
                id,
                orden,
                false,
                descripcion,
                unidad,
                v.cantidad(),
                v.rendimiento(),
                v.tarifaJornal(),
                v.precioUnitarioTarifa(),
                precioEfectivo,
                costoHora,
                costo,
                v.insumo());
    }

    static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Port of the retired {@code ApuDocumentoEvidenceIT.extremes}: 3 APUs × 125 signed-negative rows, long Unicode
     * text in every institutional field, display precision {@code precision}. Document stress, not financial parity.
     */
    static DocumentProjectionV1 extremes(int precision) {
        var base = Projections.load("apus-ci018-base");
        String longText = "Descripción institucional ñ á Ω ".repeat(150) + " LONGENDING";
        var analyses = new ArrayList<ApuView>();
        for (int n = 0; n < 3; n++) {
            var a = base.snapshot().apuRender().getFirst();
            var source = a.bloques().stream()
                    .filter(b -> !b.filas().isEmpty())
                    .findFirst()
                    .orElseThrow();
            var original = source.filas().getFirst().detalle();
            var rows = new ArrayList<ApuRow>();
            for (int i = 0; i < 125; i++) {
                var d = new Detalle(
                        uuid("extreme-" + n + "-" + i),
                        (short) i,
                        false,
                        "Fila ñ " + i + (i == 124 ? " ROWENDING" : ""),
                        "m²",
                        original.cantidad(),
                        original.rendimiento(),
                        original.tarifaJornal(),
                        original.precioUnitarioTarifa(),
                        new BigDecimal("-9.000001"),
                        new BigDecimal("-10.000002"),
                        new BigDecimal("-12.000003"),
                        null);
                rows.add(row(d, d.descripcion()));
            }
            var block = new ApuBlock(
                    source.tipo(),
                    source.etiqueta(),
                    source.ordenCapturado(),
                    rows,
                    new BigDecimal("-1500.000375"),
                    true);
            var pie = new ApuFooter(
                    List.of(),
                    new BigDecimal("0.18"),
                    new BigDecimal("-1500.000375"),
                    new BigDecimal("-270.000068"),
                    new BigDecimal("-1770.000443"),
                    new BigDecimal("-1770.000443"),
                    longText + " FOOTERENDING");
            analyses.add(new ApuView(
                    uuid("extreme-apu-" + n),
                    longText + n + " CODEENDING",
                    longText + n + " CODEENDING",
                    longText,
                    longText + " UNITENDING",
                    longText,
                    List.of(block),
                    pie));
        }
        var p = base.snapshot().proyecto();
        var project = new Proyecto(
                p.id(),
                longText,
                longText,
                longText,
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                longText + " INSTITUTIONENDING",
                longText);
        var signers = List.of(new Firmante(uuid("signer"), longText + " SIGNERENDING", longText, "TECNICO", (short) 1));
        return with(base, project, new Display(precision, 4), signers, analyses);
    }

    /** True for the hourly sections whose rows print quantity, rate, hourly cost and yield. */
    static boolean hourly(SeccionTipo tipo) {
        return tipo == SeccionTipo.EQUIPO || tipo == SeccionTipo.MANO_OBRA;
    }
}
