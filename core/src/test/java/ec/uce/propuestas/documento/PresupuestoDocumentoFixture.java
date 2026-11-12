package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.PreflightDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.motor.ApuCalculado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PresupuestoDocumentoFixture {
    static final String EXACTO = "123.123456789012345678901234567890";
    static final String HOSTIL = "=SUM(A1:A2) +@ ñ m² m³";

    static UUID id(int n) {
        return UUID.fromString("01900000-0000-7000-8000-%012d".formatted(n));
    }

    static BigDecimal decimal(String s) {
        return new BigDecimal(s);
    }

    static OpcionesDocumento opciones(String formato, boolean horizontal) {
        return new OpcionesDocumento(
                formato,
                formato.equals("pdf") ? Map.of("orientacion", horizontal ? "horizontal" : "vertical") : Map.of());
    }

    static SnapshotDocumento snapshot(int precision, int extras) {
        var rubros = new ArrayList<SnapshotDocumento.Rubro>();
        rubros.add(rubro(3, "1.10", "DÉCIMO", "Descripción décima"));
        rubros.add(rubro(2, "1.2", "+SEGUNDO", HOSTIL));
        rubros.add(rubro(1, "1.2", "@PRIMERO", "Primero ñ m² m³"));
        for (int i = 0; i < extras; i++) {
            rubros.add(rubro(
                    100 + i,
                    "1." + (20 + i),
                    "LARGO" + i,
                    "Continuación ñ m² m³ " + "descripción extensa segura ".repeat(12) + "FIN" + i));
        }
        var hijo = new SnapshotDocumento.Capitulo(
                id(12),
                "1.1",
                "Subcapítulo",
                (short) 1,
                decimal("777.777777"),
                List.of(),
                List.of(rubro(4, "1.1.1", "-HIJO", "Hijo")));
        var capitulo = new SnapshotDocumento.Capitulo(
                id(11), "1", "Capítulo raíz", (short) 1, decimal("888.888888"), List.of(hijo), rubros);
        var calculado = new ApuCalculado(
                "APU",
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                decimal("999999.999999"));
        var apu = new SnapshotDocumento.Apu(
                id(20),
                "APU",
                "No usar CT",
                "m²",
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                decimal("888888.888888"),
                calculado,
                List.of());
        return new SnapshotDocumento(
                id(30),
                (short) 7,
                false,
                HOSTIL,
                decimal("4321.987654"),
                new SnapshotDocumento.Proyecto(
                        id(31),
                        "Proyecto histórico ñ",
                        "PROY",
                        "",
                        (short) 2025,
                        null,
                        null,
                        null,
                        "",
                        "Dirección institucional",
                        "Subdirección"),
                new SnapshotDocumento.Parametros(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        BigDecimal.ZERO,
                        "USD",
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        HOSTIL,
                        ""),
                new SnapshotDocumento.Display(precision, 4),
                List.of(
                        new SnapshotDocumento.Firmante(id(41), "Técnico ñ", "Ingeniero", "TECNICO", (short) 2),
                        new SnapshotDocumento.Firmante(id(40), "Legal ñ", "Director", "LEGAL", (short) 1)),
                List.of(capitulo),
                List.of(apu),
                new PreflightDocumento(id(30), (short) 7, "presupuesto", "xlsx", Map.of(), true, List.of(), List.of()));
    }

    static SnapshotDocumento sinInstitucion() {
        var s = snapshot(2, 0);
        var p = s.proyecto();
        var proyecto = new SnapshotDocumento.Proyecto(
                p.id(),
                p.nombre(),
                p.codigo(),
                p.descripcion(),
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                null,
                "");
        return new SnapshotDocumento(
                s.presupuestoId(),
                s.version(),
                s.vigente(),
                s.notas(),
                s.total(),
                proyecto,
                s.parametros(),
                s.display(),
                s.firmantes(),
                s.capitulos(),
                s.apus(),
                s.preflight());
    }

    private static SnapshotDocumento.Rubro rubro(int n, String item, String codigo, String descripcion) {
        return new SnapshotDocumento.Rubro(
                id(n),
                id(20),
                item,
                codigo,
                descripcion,
                "m²",
                decimal(EXACTO),
                decimal("12.345678"),
                decimal("98.765432"));
    }

    static SnapshotDocumento extremo(int precision) {
        var s = snapshot(precision, 0);
        var rows = List.of(
                new SnapshotDocumento.Rubro(
                        id(501),
                        id(20),
                        "1.1",
                        "0001234567890123456789012345678901234567890",
                        "INICIOGIGANTE " + "descripción ñ m² m³ extensa ".repeat(650) + " FINGIGANTE",
                        "m²",
                        decimal("-1.5"),
                        decimal("2.345"),
                        decimal("-2.345")),
                new SnapshotDocumento.Rubro(
                        id(502),
                        id(20),
                        "1.2",
                        "=+@-0001",
                        "Control\u0001 tab\t salto\n ñ m² m³",
                        "u",
                        decimal("1.5"),
                        decimal("2.355"),
                        decimal("-2.355")));
        var c = new SnapshotDocumento.Capitulo(id(11), "1", "Extremo", (short) 1, s.total(), List.of(), rows);
        return new SnapshotDocumento(
                s.presupuestoId(),
                s.version(),
                s.vigente(),
                s.notas(),
                s.total(),
                s.proyecto(),
                s.parametros(),
                s.display(),
                s.firmantes(),
                List.of(c),
                s.apus(),
                s.preflight());
    }

    private PresupuestoDocumentoFixture() {}
}
