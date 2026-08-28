package ec.uce.propuestas.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TDD T1 — Frontera APU→Rubro: regla <b>workbook-consistent</b>
 * (corrección 2026-08-28, plan 014).
 *
 * <p>Accede únicamente a la API pública {@link Motor#consolidar(VersionSnapshot)}.
 * Nunca invoca {@code motor.internal.Consolidador} directamente.
 *
 * <p>Cada test construye un {@link VersionSnapshot} mínimo (1 capítulo, 1 rubro) con
 * un {@link ApuSnapshot} stub y un único {@link FilaSnapshot} MATERIAL con precio
 * = {@code costoTotal} del caso (cantidad=1, esAuxiliar=false, %CI=0 → CD_ajustado = CD).
 *
 * <p>Política verificada (workbook-consistent 2026-08-28):
 * <pre>
 *   precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)
 *   precioTotal    = cantidad.multiply(precioUnitario, MC).setScale(6, RoundingMode.HALF_UP)
 *   totalGeneral   = sum(precioTotal scale 6); display/assertion canonical = scale 2 HALF_UP
 * </pre>
 * Y los totales de capítulo / totalGeneral agregan los mismos {@code precioTotal}
 * ya retenidos a escala 6, sin recomputar desde el {@code costoTotal} sin redondear.
 *
 * <p>Las aserciones usan {@code compareTo} para la comparación numérica y añaden
 * una aserción explícita de {@code scale()} para capturar la diferencia entre
 * la regla retirada (PT scale 2) y la regla workbook-consistent (PT scale 6).
 */
class ConsolidadorFronteraTest {

    private static final BigDecimal HM = new BigDecimal("0.0500");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Construye un {@link VersionSnapshot} con un solo capítulo y un solo rubro.
     * El APU es un único MATERIAL con precioUnitario = {@code apuPrecioUnitario}
     * (cantidad de fila=1, rendimiento null) → el motor calcula CD = 1 × pu,
     * %CI=0 → CI=0 → CT = CD.
     */
    private static VersionSnapshot versionUnRubro(BigDecimal apuPrecioUnitario, BigDecimal cantidad) {
        FilaSnapshot fila = new FilaSnapshot(
                SeccionTipo.MATERIAL,
                false,
                BigDecimal.ONE,
                null,
                apuPrecioUnitario,
                null,
                null);
        ApuSnapshot apu = new ApuSnapshot("T1", false, List.of(fila));
        RubroSnapshot rubro = new RubroSnapshot("R-T1", cantidad, apu);
        CapituloSnapshot cap = new CapituloSnapshot("1", "Capitulo frontera", 1, List.of(), List.of(rubro));
        ParametrosCalculo params = new ParametrosCalculo(HM, ZERO, null, ZERO);
        return new VersionSnapshot(params, List.of(cap), null);
    }

    @Test
    void frontera_costoTotal_5_5352325_cantidad_10_aplica_DOWN_a_2dp_y_pt_a_6dp() {
        BigDecimal costoTotalStub = new BigDecimal("5.5352325");
        BigDecimal cantidad = new BigDecimal("10");
        VersionSnapshot v = versionUnRubro(costoTotalStub, cantidad);

        VersionCalculada result = Motor.consolidar(v);

        assertEquals(1, result.rubros().size(), "un solo rubro");
        RubroConPrecio r = result.rubros().get(0);

        // precioUnitario DOWN 2dp: 5.5352325 → 5.53
        assertEquals(
                0,
                new BigDecimal("5.53").compareTo(r.precioUnitario()),
                "precioUnitario DOWN 2dp: 5.5352325 → 5.53, got " + r.precioUnitario());
        assertEquals(2, r.precioUnitario().scale(), "precioUnitario scale == 2 (DOWN), got " + r.precioUnitario().scale());

        // precioTotal = 10 × 5.53 = 55.30 → workbook-consistent: scale 6 HALF_UP = 55.300000
        assertEquals(
                0,
                new BigDecimal("55.300000").compareTo(r.precioTotal()),
                "precioTotal scale 6 HALF_UP: 10 × 5.53 → 55.300000, got " + r.precioTotal());
        assertEquals(
                6,
                r.precioTotal().scale(),
                "precioTotal scale == 6 (workbook-consistent), got " + r.precioTotal().scale());
    }

    @Test
    void frontera_costoTotal_0_001_cantidad_1000_aplica_DOWN_a_2dp_y_pt_a_6dp() {
        BigDecimal costoTotalStub = new BigDecimal("0.001");
        BigDecimal cantidad = new BigDecimal("1000");
        VersionSnapshot v = versionUnRubro(costoTotalStub, cantidad);

        VersionCalculada result = Motor.consolidar(v);

        RubroConPrecio r = result.rubros().get(0);

        // precioUnitario DOWN 2dp: 0.001 → 0.00
        assertEquals(
                0,
                new BigDecimal("0.00").compareTo(r.precioUnitario()),
                "precioUnitario DOWN 2dp: 0.001 → 0.00, got " + r.precioUnitario());
        assertEquals(2, r.precioUnitario().scale(), "precioUnitario scale == 2 (DOWN), got " + r.precioUnitario().scale());

        // precioTotal = 1000 × 0.00 = 0.00 → scale 6 HALF_UP = 0.000000
        assertEquals(
                0,
                new BigDecimal("0.000000").compareTo(r.precioTotal()),
                "precioTotal scale 6 HALF_UP: 1000 × 0.00 → 0.000000, got " + r.precioTotal());
        assertEquals(
                6,
                r.precioTotal().scale(),
                "precioTotal scale == 6 (workbook-consistent), got " + r.precioTotal().scale());
    }

    @Test
    void frontera_costoTotal_1_999_cantidad_1_aplica_DOWN_sin_redondear_a_2_00() {
        BigDecimal costoTotalStub = new BigDecimal("1.999");
        BigDecimal cantidad = BigDecimal.ONE;
        VersionSnapshot v = versionUnRubro(costoTotalStub, cantidad);

        VersionCalculada result = Motor.consolidar(v);

        RubroConPrecio r = result.rubros().get(0);

        // precioUnitario DOWN 2dp: 1.999 → 1.99 (no 2.00)
        assertEquals(
                0,
                new BigDecimal("1.99").compareTo(r.precioUnitario()),
                "precioUnitario DOWN 2dp: 1.999 → 1.99 (no 2.00), got " + r.precioUnitario());
        assertEquals(2, r.precioUnitario().scale(), "precioUnitario scale == 2 (DOWN), got " + r.precioUnitario().scale());

        // precioTotal = 1 × 1.99 = 1.99 → scale 6 HALF_UP = 1.990000
        assertEquals(
                0,
                new BigDecimal("1.990000").compareTo(r.precioTotal()),
                "precioTotal scale 6 HALF_UP: 1 × 1.99 → 1.990000, got " + r.precioTotal());
        assertEquals(
                6,
                r.precioTotal().scale(),
                "precioTotal scale == 6 (workbook-consistent), got " + r.precioTotal().scale());
    }

    @Test
    void frontera_costoTotal_5_5352325_cantidad_1_aplica_DOWN_a_2dp_y_pt_a_6dp() {
        BigDecimal costoTotalStub = new BigDecimal("5.5352325");
        BigDecimal cantidad = BigDecimal.ONE;
        VersionSnapshot v = versionUnRubro(costoTotalStub, cantidad);

        VersionCalculada result = Motor.consolidar(v);

        RubroConPrecio r = result.rubros().get(0);

        // precioUnitario DOWN 2dp: 5.5352325 → 5.53
        assertEquals(
                0,
                new BigDecimal("5.53").compareTo(r.precioUnitario()),
                "precioUnitario DOWN 2dp: 5.5352325 → 5.53, got " + r.precioUnitario());
        assertEquals(2, r.precioUnitario().scale(), "precioUnitario scale == 2 (DOWN), got " + r.precioUnitario().scale());

        // precioTotal = 1 × 5.53 = 5.53 → scale 6 HALF_UP = 5.530000
        assertEquals(
                0,
                new BigDecimal("5.530000").compareTo(r.precioTotal()),
                "precioTotal scale 6 HALF_UP: 1 × 5.53 → 5.530000, got " + r.precioTotal());
        assertEquals(
                6,
                r.precioTotal().scale(),
                "precioTotal scale == 6 (workbook-consistent), got " + r.precioTotal().scale());
    }

    @Test
    void agregacion_capitulo_y_totalGeneral_suman_pt_scale6_consistentemente() {
        // Construye 3 rubros con CT stubs que producen precios unitarios con residuo.
        // Cada PU: DOWN 2dp. Cada PT: scale 6 HALF_UP. Capítulo y totalGeneral
        // agregan los mismos PT a scale 6 (sin recomputar).
        BigDecimal puA = new BigDecimal("5.5352325"); // → 5.53
        BigDecimal puB = new BigDecimal("1.999"); // → 1.99
        BigDecimal puC = new BigDecimal("0.001"); // → 0.00
        BigDecimal cantA = new BigDecimal("10"); // 10 × 5.53 = 55.30 → 55.300000
        BigDecimal cantB = BigDecimal.ONE; // 1 × 1.99 = 1.99 → 1.990000
        BigDecimal cantC = new BigDecimal("1000"); // 1000 × 0.00 = 0.00 → 0.000000

        FilaSnapshot fA = new FilaSnapshot(SeccionTipo.MATERIAL, false, BigDecimal.ONE, null, puA, null, null);
        FilaSnapshot fB = new FilaSnapshot(SeccionTipo.MATERIAL, false, BigDecimal.ONE, null, puB, null, null);
        FilaSnapshot fC = new FilaSnapshot(SeccionTipo.MATERIAL, false, BigDecimal.ONE, null, puC, null, null);

        ApuSnapshot apuA = new ApuSnapshot("A", false, List.of(fA));
        ApuSnapshot apuB = new ApuSnapshot("B", false, List.of(fB));
        ApuSnapshot apuC = new ApuSnapshot("C", false, List.of(fC));

        RubroSnapshot rA = new RubroSnapshot("R-A", cantA, apuA);
        RubroSnapshot rB = new RubroSnapshot("R-B", cantB, apuB);
        RubroSnapshot rC = new RubroSnapshot("R-C", cantC, apuC);

        CapituloSnapshot cap =
                new CapituloSnapshot("1", "Cap agreg", 1, List.of(), new ArrayList<>(List.of(rA, rB, rC)));
        ParametrosCalculo params = new ParametrosCalculo(HM, ZERO, null, ZERO);
        VersionSnapshot v = new VersionSnapshot(params, List.of(cap), null);

        VersionCalculada result = Motor.consolidar(v);

        // capítulo total = Σ PT scale 6 = 55.300000 + 1.990000 + 0.000000 = 57.290000
        BigDecimal esperadoCap = new BigDecimal("57.290000");
        assertEquals(
                0,
                esperadoCap.compareTo(result.capitulos().get(0).total()),
                "capituloTotal (scale 6) = Σ PT scale 6, expected=" + esperadoCap
                        + " got=" + result.capitulos().get(0).total());
        assertEquals(
                6,
                result.capitulos().get(0).total().scale(),
                "capituloTotal scale == 6 (workbook-consistent), got "
                        + result.capitulos().get(0).total().scale());

        // totalGeneral — display/assertion canónico a 2 dp HALF_UP = 57.29
        // El agregado interno se conserva a scale 6; la presentación canónica se
        // verifica vía setScale(2, HALF_UP) sin truncar el agregado.
        BigDecimal esperado2dp = new BigDecimal("57.29");
        assertEquals(
                0,
                esperado2dp.compareTo(result.totalGeneral().setScale(2, java.math.RoundingMode.HALF_UP)),
                "totalGeneral canonical 2dp HALF_UP = 57.29, got " + result.totalGeneral());
    }
}