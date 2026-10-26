package ec.uce.propuestas.documento;

import static ec.uce.propuestas.documento.PresupuestoDocumentoFixture.decimal;
import static ec.uce.propuestas.documento.PresupuestoDocumentoFixture.id;

import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.motor.ApuSnapshot;
import ec.uce.propuestas.motor.FilaCalculada;
import ec.uce.propuestas.motor.FilaSnapshot;
import ec.uce.propuestas.motor.Motor;
import ec.uce.propuestas.motor.ParametrosCalculo;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot de un APU cuyos importes persistidos coinciden con el motor (escala 6), como tras
 * {@code ApuCalculoService.recalcular}. Los importes se calculan con el motor (solo lectura); nada
 * aquí reimplementa aritmética del dominio.
 */
final class ApuConciliadoFixture {
    static final BigDecimal HM = decimal("0.0500");
    static final BigDecimal CI = decimal("0.1800");

    /** Fila de entrada: tipo, es HM, cantidad, rendimiento, precio. */
    record Entrada(SeccionTipo tipo, boolean hm, String cantidad, String rendimiento, String precio, String texto) {}

    static List<Entrada> entradasConHm() {
        return List.of(
                new Entrada(SeccionTipo.EQUIPO, true, null, null, null, "hm"),
                new Entrada(SeccionTipo.EQUIPO, false, "1", "0.33", "15.00", "Equipo certificador"),
                new Entrada(SeccionTipo.MANO_OBRA, false, "1", "0.33", "4.75", "Maestro eléctrico"),
                new Entrada(SeccionTipo.MANO_OBRA, false, "2", "0.5", "4.23", "Ayudante"),
                new Entrada(SeccionTipo.MATERIAL, false, "3.5", null, "1.2345", "Cable"),
                new Entrada(SeccionTipo.TRANSPORTE, false, "0.5", null, "2.00", "Flete"));
    }

    static List<Entrada> entradasSinHm() {
        return entradasConHm().stream().filter(e -> !e.hm()).toList();
    }

    /** persistidoVencido: costo y costo hora persistidos distintos (obsoletos) del motor. */
    static SnapshotDocumento.Apu apu(int n, List<Entrada> entradas, boolean persistidoVencido) {
        var filas = new ArrayList<FilaSnapshot>();
        for (var e : entradas) {
            filas.add(
                    e.hm()
                            ? new FilaSnapshot(SeccionTipo.EQUIPO, true, HM.movePointRight(2), null, null, null)
                            : new FilaSnapshot(
                                    e.tipo(),
                                    false,
                                    decimal(e.cantidad()),
                                    e.rendimiento() == null ? null : decimal(e.rendimiento()),
                                    decimal(e.precio()),
                                    null));
        }
        var calc = Motor.calcularApu(new ApuSnapshot("APU" + n, null, filas), new ParametrosCalculo(HM, CI));
        var secciones = new ArrayList<SnapshotDocumento.Seccion>();
        for (var tipo : SeccionTipo.values()) {
            var detalles = new ArrayList<SnapshotDocumento.Detalle>();
            int orden = 0;
            // El motor ordena M con HM primero; los detalles persistidos conservan su orden de captura.
            var calcTipo =
                    calc.filas().stream().filter(f -> f.seccion() == tipo).toList();
            int i = 0;
            int nonHm = 0;
            for (var e : entradas) {
                if (e.tipo() != tipo) continue;
                FilaCalculada fc = e.hm()
                        ? calcTipo.stream()
                                .filter(FilaCalculada::esHerramientaMenor)
                                .findFirst()
                                .orElseThrow()
                        : calcTipo.stream()
                                .filter(f -> !f.esHerramientaMenor())
                                .skip(nonHm++)
                                .findFirst()
                                .orElseThrow();
                BigDecimal costo = fc.costoFila().setScale(6, RoundingMode.HALF_UP);
                BigDecimal costoHora = fc.costoHora();
                if (persistidoVencido) {
                    costo = costo.add(decimal("1.500000"));
                    costoHora = costoHora == null ? null : costoHora.add(decimal("2.250000"));
                }
                detalles.add(new SnapshotDocumento.Detalle(
                        id(1000 + n * 100 + (i++)),
                        (short) (++orden),
                        e.hm(),
                        e.hm() ? "Herramienta Menor 5.00%MO" : e.texto(),
                        e.hm() ? null : (tipo == SeccionTipo.MATERIAL || tipo == SeccionTipo.TRANSPORTE ? "u" : "h"),
                        e.hm() ? HM.movePointRight(2) : decimal(e.cantidad()),
                        e.rendimiento() == null ? null : decimal(e.rendimiento()),
                        null,
                        null,
                        e.hm() ? null : decimal(e.precio()),
                        e.hm() ? BigDecimal.ZERO.setScale(6) : costoHora,
                        costo,
                        null));
            }
            BigDecimal subtotal = switch (tipo) {
                case EQUIPO -> calc.subtotalM();
                case MANO_OBRA -> calc.subtotalN();
                case MATERIAL -> calc.subtotalO();
                case TRANSPORTE -> calc.subtotalP();
            };
            secciones.add(new SnapshotDocumento.Seccion(
                    tipo, (short) tipo.ordinal(), subtotal.setScale(6, RoundingMode.HALF_UP), detalles));
        }
        return new SnapshotDocumento.Apu(
                id(n),
                "APU" + n,
                "Descripción del APU " + n,
                "m2",
                "",
                null,
                calc.costoDirecto().setScale(6, RoundingMode.HALF_UP),
                calc.costoIndirecto().setScale(6, RoundingMode.HALF_UP),
                calc.costoTotal().setScale(6, RoundingMode.HALF_UP),
                calc,
                secciones);
    }

    /** Documento con un capítulo y un rubro por APU (ids 20, 21...). */
    static SnapshotDocumento snapshot(List<SnapshotDocumento.Apu> apus, boolean activos) {
        var base = PresupuestoDocumentoFixture.snapshot(2, 0);
        var p = base.parametros();
        var params = new SnapshotDocumento.Parametros(
                HM,
                CI,
                false,
                p.iva(),
                p.moneda(),
                true,
                activos,
                true,
                true,
                false,
                false,
                "Este precio no incluye IVA",
                "ITEM");
        var rubros = new ArrayList<SnapshotDocumento.Rubro>();
        int k = 1;
        for (var a : apus) {
            rubros.add(new SnapshotDocumento.Rubro(
                    id(300 + k),
                    a.id(),
                    "1." + k,
                    a.codigo(),
                    a.descripcion(),
                    "m2",
                    decimal("2.5"),
                    a.costoTotal().setScale(2, RoundingMode.DOWN),
                    decimal("2.5").multiply(a.costoTotal().setScale(2, RoundingMode.DOWN))));
            k++;
        }
        var cap = new SnapshotDocumento.Capitulo(
                id(11), "1", "Capítulo", (short) 1, decimal("100.000000"), List.of(), rubros);
        return new SnapshotDocumento(
                base.presupuestoId(),
                base.version(),
                false,
                "",
                decimal("100.000000"),
                base.proyecto(),
                params,
                base.display(),
                base.firmantes(),
                List.of(cap),
                apus,
                base.preflight());
    }

    private ApuConciliadoFixture() {}
}
