package ec.uce.propuestas.documento;

import static ec.uce.propuestas.documento.PresupuestoDocumentoFixture.*;

import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.List;

final class ApuDocumentoFixture {
    static final String TEXTO = "=<script> ñ 😃 []:*?/\\ @SUM(A1)\n";

    static SnapshotDocumento snapshot(boolean activos, boolean individual, BigDecimal override) {
        var s = PresupuestoDocumentoFixture.snapshot(6, 0);
        var p = s.parametros();
        var params = new SnapshotDocumento.Parametros(
                decimal("0.07300"),
                decimal("0.1800"),
                individual,
                p.iva(),
                p.moneda(),
                activos,
                activos,
                activos,
                activos,
                activos,
                activos,
                TEXTO,
                "ITEM");
        var a = apu(20, override);
        var b = apu(21, null);
        var hijo = new SnapshotDocumento.Capitulo(
                id(12),
                "1.2",
                TEXTO,
                (short) 1,
                s.total(),
                List.of(),
                List.of(rubro(2, 20, "1.2.1"), rubro(3, 21, "1.2.2")));
        var cap = new SnapshotDocumento.Capitulo(
                id(11),
                "1",
                TEXTO,
                (short) 1,
                s.total(),
                List.of(hijo),
                List.of(rubro(1, 21, "1.1"), rubro(4, 20, "1.3")));
        return new SnapshotDocumento(
                s.presupuestoId(),
                s.version(),
                false,
                s.notas(),
                s.total(),
                s.proyecto(),
                params,
                s.display(),
                s.firmantes(),
                List.of(cap),
                List.of(a, b, a, apu(99, null)),
                s.preflight());
    }

    static SnapshotDocumento.Apu apu(int n, BigDecimal override) {
        var diagnostic =
                PresupuestoDocumentoFixture.snapshot(2, 0).apus().getFirst().calculado();
        var m = new SnapshotDocumento.Seccion(
                SeccionTipo.EQUIPO,
                (short) 99,
                decimal("11.123456789"),
                List.of(detalle(33, 2, true), detalle(32, 1, false), detalle(31, 1, false)));
        var o = new SnapshotDocumento.Seccion(
                SeccionTipo.MATERIAL, (short) 0, decimal("22.000000"), List.of(detalle(34, 1, false)));
        return new SnapshotDocumento.Apu(
                id(n),
                TEXTO,
                TEXTO,
                "m²",
                TEXTO,
                override,
                decimal("33.123456789"),
                decimal("4.987654321"),
                decimal(EXACTO),
                diagnostic,
                List.of(o, m));
    }

    private static SnapshotDocumento.Detalle detalle(int n, int orden, boolean hm) {
        return new SnapshotDocumento.Detalle(
                id(n),
                (short) orden,
                hm,
                TEXTO,
                "u",
                decimal("2.000001"),
                decimal("0.000123456"),
                decimal("7.00"),
                decimal("8.00"),
                decimal("9.000000001"),
                decimal("10.000000002"),
                decimal("12.000000003"),
                null);
    }

    private static SnapshotDocumento.Rubro rubro(int n, int apu, String item) {
        return new SnapshotDocumento.Rubro(
                id(n), id(apu), item, "NOT THE APU CODE", TEXTO, "u", BigDecimal.ONE, decimal("1.23"), decimal("1.23"));
    }

    private ApuDocumentoFixture() {}
}
