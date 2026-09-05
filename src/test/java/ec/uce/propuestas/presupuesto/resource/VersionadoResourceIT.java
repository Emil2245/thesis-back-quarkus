package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 024 — flujo REST de versionado del presupuesto (P-31): crear version
 * via deep copy, marcar vigente (transaccional), eliminar version
 * (protegiendo la vigente) y comparar dos versiones.
 *
 * <p>Estos tests son <strong>RED</strong>: los endpoints P-31 aun no existen
 * en este modulo (ver {@code docs/modulos/05-presupuesto/06-versionado-comparacion.md}).
 * Las aserciones describen el contrato esperado; la implementacion
 * correspondiente ({@code VersionadoResource}, {@code ComparacionResource},
 * {@code VersionadoService}) se escribira en un plan posterior.
 *
 * <p>Convenciones heredadas de {@code RubroResourceIT} y {@code CapituloResourceIT}:
 * el {@code presupuestoId} y el {@code proyectoId} del path son
 * UUIDv7 del {@code public_id}; el {@code BIGINT} interno nunca aparece en
 * JSON. Cada mutacion se verifica contra BD con helpers nativos (las entidades
 * JPA {@code cronograma}/{@code actividad} no existen aun; la copia
 * estructural se hace con SQL nativo en el modulo {@code presupuesto}, sin
 * introducir entidades ni migraciones nuevas).
 *
 * <p>Stop conditions respetadas: el fixture es compacto (arbol de 3 niveles,
 * un rubro, un APU con sus 4 secciones + 1 detalle compartido, 1 cronograma y
 * 1 actividad); no se usa el seed IESS completo. Las escalas decimales se
 * asertan a 6 cuando el contrato REST las usa (P-30 write-through).</p>
 */
@QuarkusTest
class VersionadoResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    /** Total calibrado del origen en escala 6 (es el valor del write-through). */
    private static final BigDecimal TOTAL_ORIGEN_ESPERADO = new BigDecimal("42.000000");

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            // Reset incluye cronograma + actividad + las dependencias actuales
            // del modulo presupuesto/APU (descuento_global_snapshot,
            // presupuesto_descuento_global, presupuesto_rubro,
            // cronograma_actividad) quedan cubiertas por CASCADE desde
            // presupuesto y cronograma.
            st.execute("TRUNCATE TABLE cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers — proyecto + arbol + APU + secciones + detalle + cronograma
    // ──────────────────────────────────────────────────────────────────────

    /** Crea un proyecto del caller y devuelve el UUID publico del proyecto. */
    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-P24",
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "GAD Municipal"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Long internalProyectoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM proyecto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalPresupuestoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM presupuesto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String vigenteDeProyecto(String proyectoPublicId) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND es_vigente = TRUE")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Inserta una version adicional (no vigente) para tests cross-version. */
    private String insertarVersionNoVigente(String proyectoPublicId, short version) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO presupuesto (proyecto_id, version, es_vigente) "
                                + "VALUES (?, ?, FALSE) RETURNING public_id")) {
            ps.setLong(1, proyectoId);
            ps.setShort(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Inserta un insumo compartido (el proyecto lo usara via apu_detalle). */
    private Long insertarInsumoCompartido(String codigo, BigDecimal precio) throws Exception {
        long baseId;
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("INSERT INTO base_insumos (nombre, tipo, archivada) "
                    + "VALUES ('Base versionado', 'CENTRAL', FALSE)");
            try (ResultSet rs = st.executeQuery("SELECT id FROM base_insumos WHERE nombre = 'Base versionado'")) {
                rs.next();
                baseId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) "
                                + "VALUES (?, ?, 'MANO_OBRA', 'Insumo compartido', 'h', ?) RETURNING id")) {
            ps.setLong(1, baseId);
            ps.setString(2, codigo);
            ps.setBigDecimal(3, precio);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Siembra el arbol compacto usado por TC-P31-01 y TC-P31-04:
     * cap1 ("1") -> cap1.1 ("1.1") -> cap1.1.1 ("1.1.1"), un rubro colgado de
     * cap1.1.1, un APU con las 4 secciones canonicas, un detalle MO
     * referenciando el insumo compartido, un cronograma y una actividad
     * referenciando el rubro.
     */
    private static record ArbolCompacto(
            String rubroPublicId,
            long rubroId,
            String apuPublicId,
            long apuId,
            long insumoId,
            long cronogramaId,
            long actividadId) {}

    private ArbolCompacto sembrarArbolCompacto(String presupuestoPublicId) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);

        Long insumoId = insertarInsumoCompartido("INS-COMP", new BigDecimal("4.000000"));

        long apuId;
        String apuPublicId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                + "especificacion_tecnica, costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, 'APU-24', 'Versionado', 'u', "
                                + "'ET preservada en el deep copy', 0, 0, 0) RETURNING id, public_id")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                apuId = rs.getLong(1);
                apuPublicId = rs.getString(2);
            }
        }

        // 4 secciones (M/N/O/P) en orden canonico
        long equipoSeccionId;
        long moSeccionId;
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) "
                    + "VALUES (?, 'EQUIPO', 0, 1) RETURNING id")) {
                ps.setLong(1, apuId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    equipoSeccionId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) "
                    + "VALUES (?, 'MANO_OBRA', 0, 2) RETURNING id")) {
                ps.setLong(1, apuId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    moSeccionId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) " + "VALUES (?, 'MATERIAL', 0, 3)")) {
                ps.setLong(1, apuId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) " + "VALUES (?, 'TRANSPORTE', 0, 4)")) {
                ps.setLong(1, apuId);
                ps.executeUpdate();
            }
        }

        // HM canonica en bloque EQUIPO (decision #17 #9)
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, "
                                + "costo_hora, unidad, costo) "
                                + "VALUES (?, 'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
            ps.setLong(1, equipoSeccionId);
            ps.executeUpdate();
        }

        // Detalle MO que referencia el insumo compartido:
        // subtotalN = 2 * 1 * 4 = 8 -> HM = 0.4 -> CD = 8.4 -> CT = 8.4
        // PU_2dp_DOWN(8.4) = 8.40 -> PT (cantidad 5) = 42.00
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, insumo_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, tarifa_jornal, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, ?, 'MO ref insumo', 1, 2, FALSE, 4.000000, 0, 1, 'h', 0)")) {
            ps.setLong(1, moSeccionId);
            ps.setLong(2, insumoId);
            ps.executeUpdate();
        }

        // Arbol de capitulos: cap1 ("1") -> cap1.1 ("1.1") -> cap1.1.1 ("1.1.1")
        long cap1Id;
        long cap1_1Id;
        long cap1_1_1Id;
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '1', 'OBRAS PRELIMINARES', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1Id = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1', 'Replanteo', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1_1Id = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1.1', 'Topografia', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1_1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1_1_1Id = rs.getLong(1);
                }
            }
        }

        // Rubro colgado de "1.1.1"
        long rubroId;
        String rubroPublicId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                                + "VALUES (?, ?, '1.1.1.1', 'APU-24', 'Versionado', 'u', 5.000000) "
                                + "RETURNING id, public_id")) {
            ps.setLong(1, cap1_1_1Id);
            ps.setLong(2, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                rubroId = rs.getLong(1);
                rubroPublicId = rs.getString(2);
            }
        }

        // Cronograma (UNIQUE por presupuesto) — 12 periodos SEMANA
        long cronogramaId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos) "
                                + "VALUES (?, 'SEMANA', 12) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                cronogramaId = rs.getLong(1);
            }
        }

        // Actividad referenciando el rubro (UNIQUE por rubro). El mapa JSONB
        // canónico conserva claves 1-based no consecutivas y decimales como strings.
        long actividadId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) "
                                + "VALUES (?, ?, 100.0000, ?::jsonb) RETURNING id")) {
            ps.setLong(1, cronogramaId);
            ps.setLong(2, rubroId);
            ps.setString(3, "{\"1\":\"0.1250\",\"3\":\"0.3750\"}");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                actividadId = rs.getLong(1);
            }
        }

        // Calibrar totales del origen al valor write-through (PU truncado a
        // 2dp, escala 6). Con MO cantidad=2, rendimiento=1, tarifaJornal=4
        // -> subtotalN=8, HM=0.4, CD=8.4, CI=0 -> CT=8.4 -> PU=8.40 ->
        // PT (cantidad 5) = 42.00. La pre-poblacion simula un write-through
        // previo para que el assert bit-a-bit origen<->copia tenga sentido
        // antes de que el recalculo del deep copy se implemente.
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE presupuesto SET total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, TOTAL_ORIGEN_ESPERADO);
                ps.setLong(2, presupuestoId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE apu SET costo_directo = 8.4, costo_indirecto = 0, costo_total = 8.4 WHERE id = ?")) {
                ps.setLong(1, apuId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("UPDATE rubro SET precio_unitario = 8.40, precio_total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, TOTAL_ORIGEN_ESPERADO);
                ps.setLong(2, rubroId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE capitulo SET total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, TOTAL_ORIGEN_ESPERADO);
                ps.setLong(2, cap1_1_1Id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE capitulo SET total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, TOTAL_ORIGEN_ESPERADO);
                ps.setLong(2, cap1_1Id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE capitulo SET total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, TOTAL_ORIGEN_ESPERADO);
                ps.setLong(2, cap1Id);
                ps.executeUpdate();
            }
        }

        return new ArbolCompacto(rubroPublicId, rubroId, apuPublicId, apuId, insumoId, cronogramaId, actividadId);
    }

    private BigDecimal leerPresupuestoTotal(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerRubroCantidad(String rubroPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT cantidad FROM rubro WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(rubroPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerCapituloTotalPorItem(String presupuestoPublicId, String item) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT total FROM capitulo WHERE presupuesto_id = ? AND item = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Cuenta cuantas filas tiene la tabla dada bajo un presupuesto concreto. */
    private long contarFilas(String tabla, String fkPresupuesto, long presupuestoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT COUNT(*) FROM " + tabla + " WHERE " + fkPresupuesto + " = ?")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Cuenta las filas de apu_detalle bajo el APU dado (via join con apu_seccion). */
    private long contarApuDetalles(long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM apu_detalle d "
                        + "JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "WHERE s.apu_id = ?")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Huella observable de cronograma + actividad: configuración, peso y el
     * contenido JSONB canónico completo. El deep copy debe preservar exactamente
     * la semántica del mapa, incluidas claves no consecutivas y valores decimales.
     */
    private List<String> huellaCronogramaActividad(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        List<String> out = new ArrayList<>();
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT 'cronograma' AS t, c.unidad_tiempo || ':' || c.numero_periodos AS shape "
                                + "FROM cronograma c WHERE c.presupuesto_id = ? "
                                + "UNION ALL "
                                + "SELECT 'actividad' AS t, a.peso_ponderado::text || ':' || "
                                + "       a.avance_por_periodo::text AS shape "
                                + "FROM actividad a JOIN cronograma c ON c.id = a.cronograma_id "
                                + "WHERE c.presupuesto_id = ? ORDER BY 1")) {
            ps.setLong(1, pId);
            ps.setLong(2, pId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1) + "|" + rs.getString(2));
                }
            }
        }
        return out;
    }

    private long contarCronogramas(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT COUNT(*) FROM cronograma WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarActividades(String presupuestoPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id "
                        + "JOIN presupuesto p ON p.id = c.presupuesto_id "
                        + "WHERE p.public_id = ?")) {
            ps.setObject(1, UUID.fromString(presupuestoPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Devuelve el insumoId de un apu_detalle concreto (para validar que se comparte). */
    private Long insumoIdDeDetallePorCodigo(String apuCodigo, String detalleDescripcion, String proyectoPublicId)
            throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT d.insumo_id FROM apu_detalle d "
                        + "JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "JOIN apu a ON a.id = s.apu_id "
                        + "JOIN presupuesto p ON p.id = a.presupuesto_id "
                        + "WHERE p.proyecto_id = ? AND a.codigo = ? AND d.descripcion = ?")) {
            ps.setLong(1, proyectoId);
            ps.setString(2, apuCodigo);
            ps.setString(3, detalleDescripcion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────

    // ── POST /proyectos/{proyectoId}/presupuestos ────────────────────────

    /**
     * TC-P31-01 — deep copy completo y bit-a-bit independiente del origen.
     * Cubre cap (3 niveles), rubro, APU, 4 secciones, detalle referenciando
     * insumo compartido, cronograma + actividad (I-08 todavia no introduce
     * entidades JPA: la copia estructural se hace con SQL nativo desde el
     * modulo presupuesto). Las totales deben coincidir bit-a-bit porque la
     * ultima fase del deep copy invoca el recalculo write-through (P-30).
     */
    @Test
    void TC_P31_01_deep_copy_completo_e_independiente_bit_a_bit() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c1@ex.com");
        String proyectoId = crearProyecto(token, "Deep copy");
        String origenId = vigenteDeProyecto(proyectoId);
        ArbolCompacto orig = sembrarArbolCompacto(origenId);

        // Snapshot del origen antes del deep copy
        BigDecimal totalOrigenAntes = leerPresupuestoTotal(origenId);
        BigDecimal cap1_1_1Origen = leerCapituloTotalPorItem(origenId, "1.1.1");
        BigDecimal cantidadOrigen = leerRubroCantidad(orig.rubroPublicId);
        List<String> huellaOrigen = huellaCronogramaActividad(origenId);

        // POST /proyectos/{proyectoId}/presupuestos con origenId + notas
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenId, "notas", "Ajuste 5% indirecto"))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .body("presupuestoId", notNullValue())
                .body("presupuestoId", matchesPattern(UUID_V7))
                .body("presupuestoId", not(equalTo(origenId)))
                .body("version", is(2))
                .body("esVigente", is(false))
                .body("origenId", equalTo(origenId))
                .body("notas", equalTo("Ajuste 5% indirecto"))
                .body("totalGeneral", equalTo(TOTAL_ORIGEN_ESPERADO.toPlainString()));

        // Localizar el nuevo presupuesto por su version+proyecto y comparar bit-a-bit
        Long proyectoInt = internalProyectoId(proyectoId);
        String nuevoPresupuestoId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND version = 2")) {
            ps.setLong(1, proyectoInt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                nuevoPresupuestoId = rs.getString(1);
            }
        }
        assertNotNull(nuevoPresupuestoId);
        long nuevoPresupuestoIdInt = internalPresupuestoId(nuevoPresupuestoId);

        // Totales bit-a-bit identicos al origen
        BigDecimal totalNuevo = leerPresupuestoTotal(nuevoPresupuestoId);
        assertEquals(0, totalOrigenAntes.compareTo(totalNuevo), "Total nuevo identico al origen (bit-a-bit)");

        // Capitulos: misma cantidad (3), mismos items "1", "1.1", "1.1.1"
        assertEquals(3L, contarFilas("capitulo", "presupuesto_id", nuevoPresupuestoIdInt));
        assertEquals(
                0,
                cap1_1_1Origen.compareTo(leerCapituloTotalPorItem(nuevoPresupuestoId, "1.1.1")),
                "total cap 1.1.1 identico al origen");

        // Rubro: nuevo public_id, mismo item "1.1.1.1", misma cantidad, mismo codigo
        String nuevoRubroPublicId = readRubroPublicIdPorItem(nuevoPresupuestoId, "1.1.1.1");
        assertNotNull(nuevoRubroPublicId);
        assertNotEquals(orig.rubroPublicId, nuevoRubroPublicId);
        assertEquals(cantidadOrigen, leerRubroCantidad(nuevoRubroPublicId));

        // APU: nuevo public_id y nuevo id interno (ambos copiados)
        String nuevoApuPublicId = readApuCodigoPublicId(nuevoPresupuestoId, "APU-24");
        assertNotNull(nuevoApuPublicId);
        assertNotEquals(orig.apuPublicId, nuevoApuPublicId);
        long nuevoApuId = internalApuId(nuevoApuPublicId);
        assertNotEquals(orig.apuId, nuevoApuId, "El APU copiado tiene nuevo id interno");

        // Las 4 secciones canonicas estan presentes en el nuevo APU
        assertEquals(4L, contarFilas("apu_seccion", "apu_id", nuevoApuId));

        // especificacionTecnica preservada
        String et = readApuEspecificacionTecnica(nuevoApuPublicId);
        assertEquals("ET preservada en el deep copy", et);

        // porcentaje_descuento es inerte y queda en 0 (Plan 015) — el deep
        // copy no propaga un porcentaje "activo" del origen
        BigDecimal porcentajeDescuentoCopiado = readApuPorcentajeDescuento(nuevoApuPublicId);
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(porcentajeDescuentoCopiado),
                "porcentaje_descuento inactivo — el deep copy no propaga valores activos");

        // El detalle MO referencia el MISMO insumoId compartido (insumo NO se copia)
        Long insumoDetalleCopiado = insumoIdDeDetallePorCodigo("APU-24", "MO ref insumo", proyectoId);
        assertNotNull(insumoDetalleCopiado);
        assertEquals(
                orig.insumoId,
                insumoDetalleCopiado.longValue(),
                "insumo_id del detalle copiado apunta al mismo insumo compartido del origen");

        // Cronograma + actividad: el deep copy los replica estructuralmente
        assertEquals(1L, contarCronogramas(nuevoPresupuestoId), "1 cronograma copiado en el destino");
        assertEquals(1L, contarActividades(nuevoPresupuestoId), "1 actividad copiada en el destino");

        // Semántica preservada: configuración y contenido JSONB exacto, no solo
        // tamaño/forma del valor.
        assertTrue(
                huellaOrigen.contains("actividad|100.0000:{\"1\": \"0.1250\", \"3\": \"0.3750\"}"),
                "El origen conserva el mapa JSONB canónico completo");
        List<String> huellaNuevo = huellaCronogramaActividad(nuevoPresupuestoId);
        assertEquals(huellaOrigen, huellaNuevo, "Cronograma y JSONB de actividad idénticos al origen");

        // La actividad copiada referencia el NUEVO rubro, no el original (FK remapeada)
        Long actividadNuevoRubroFk = readActividadRubroFkPorCronograma(nuevoPresupuestoId);
        Long nuevoRubroIdInterno = internalRubroId(nuevoRubroPublicId);
        assertEquals(
                nuevoRubroIdInterno,
                actividadNuevoRubroFk,
                "actividad copiada referencia el nuevo rubro (FK remapeada)");

        // El origen no se modifica
        BigDecimal totalOrigenDespues = leerPresupuestoTotal(origenId);
        assertEquals(0, totalOrigenAntes.compareTo(totalOrigenDespues), "El total del origen no cambio");
        assertEquals(cantidadOrigen, leerRubroCantidad(orig.rubroPublicId), "cantidad del rubro origen intacta");
        boolean esVigenteOrigen;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT es_vigente FROM presupuesto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(origenId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                esVigenteOrigen = rs.getBoolean(1);
            }
        }
        assertTrue(esVigenteOrigen, "El origen sigue siendo la version vigente");
    }

    /** TC-P31-01 bis: deep copy sin notas (campo opcional). */
    @Test
    void TC_P31_01b_deep_copy_sin_notas_devuelve_201() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c1b@ex.com");
        String proyectoId = crearProyecto(token, "Sin notas");
        String origenId = vigenteDeProyecto(proyectoId);
        sembrarArbolCompacto(origenId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenId))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .body("origenId", equalTo(origenId))
                .body("version", is(2));
    }

    /** path/body/query UUIDv7 malformado o no-v7 → 400 validacion. */
    @Test
    void TC_P31_05_uuid_no_v7_en_path_body_query_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c5@ex.com");
        String proyectoId = crearProyecto(token, "UUIDs");

        // origenId no-v7 en body
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", UUID_NO_V7))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // proyectoId no-v7 en path
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", UUID_INEXISTENTE_V7))
                .when()
                .post("/api/v1/proyectos/" + UUID_NO_V7 + "/presupuestos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // vigente path UUID no-v7
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + UUID_NO_V7 + "/vigente")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // DELETE path UUID no-v7
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // comparar path o query UUID no-v7
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7 + "/comparar?con=" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/comparar?con=" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** origenId de OTRO proyecto del mismo owner → 400 validacion. */
    @Test
    void TC_P31_06_origen_de_otro_proyecto_mismo_owner_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c6@ex.com");
        String proyectoA = crearProyecto(token, "A");
        String proyectoB = crearProyecto(token, "B");
        String origenDeA = vigenteDeProyecto(proyectoA);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenDeA))
                .when()
                .post("/api/v1/proyectos/" + proyectoB + "/presupuestos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** origenId ajeno o inexistente → 404 no-encontrado. */
    @Test
    void TC_P31_07_origen_ajeno_o_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c7-owner@ex.com");
        String proyectoPropio = crearProyecto(token, "Propio");

        String intruso = AuthSupport.registrarConToken(mailbox, "p24-c7-intruso@ex.com");
        String proyectoAjeno = crearProyecto(intruso, "Ajeno");

        String origenAjeno = given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos/" + proyectoAjeno + "/presupuestos")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].presupuestoId");

        // origenId ajeno → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenAjeno))
                .when()
                .post("/api/v1/proyectos/" + proyectoPropio + "/presupuestos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // origenId inexistente → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", UUID_INEXISTENTE_V7))
                .when()
                .post("/api/v1/proyectos/" + proyectoPropio + "/presupuestos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // proyecto del path ajeno → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", UUID_INEXISTENTE_V7))
                .when()
                .post("/api/v1/proyectos/" + proyectoAjeno + "/presupuestos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // ── POST /presupuestos/{id}/vigente ─────────────────────────────────

    /**
     * TC-P31-02 — marcar vigente es transaccional: v2 pasa a true, v1 pasa a
     * false. Reinvocar sobre la ya vigente es idempotente (200 OK).
     */
    @Test
    void TC_P31_02_marcar_vigente_transaccional_y_idempotente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c2@ex.com");
        String proyectoId = crearProyecto(token, "Vigente swap");
        String v1Id = vigenteDeProyecto(proyectoId);
        sembrarArbolCompacto(v1Id);
        String v2Id = insertarVersionNoVigente(proyectoId, (short) 2);

        long v1IdInt = internalPresupuestoId(v1Id);
        long v2IdInt = internalPresupuestoId(v2Id);
        assertTrue(leerEsVigente(v1IdInt));
        assertEquals(false, leerEsVigente(v2IdInt));

        // Marcar v2 como vigente → respuesta 200, v1=false, v2=true
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + v2Id + "/vigente")
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(v2Id))
                .body("version", is(2))
                .body("esVigente", is(true));

        assertEquals(false, leerEsVigente(v1IdInt), "v1 ya no es vigente (transaccional)");
        assertEquals(true, leerEsVigente(v2IdInt), "v2 es la vigente");

        // Reinvocar sigue dando 200 idempotente
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + v2Id + "/vigente")
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(v2Id))
                .body("esVigente", is(true));

        // Volver a marcar v1 como vigente (transaccional)
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + v1Id + "/vigente")
                .then()
                .statusCode(200)
                .body("esVigente", is(true));

        assertEquals(true, leerEsVigente(v1IdInt), "v1 vuelve a ser vigente");
        assertEquals(false, leerEsVigente(v2IdInt), "v2 deja de ser vigente");
    }

    /** Vigente sobre presupuesto ajeno o inexistente → 404. */
    @Test
    void TC_P31_08_vigente_sobre_recurso_ajeno_o_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c8-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String vigenteId = vigenteDeProyecto(proyectoId);

        String intruso = AuthSupport.registrarConToken(mailbox, "p24-c8-intruso@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .post("/api/v1/presupuestos/" + vigenteId + "/vigente")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/vigente")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // ── DELETE /presupuestos/{id} ────────────────────────────────────────

    /**
     * TC-P31-03 — DELETE vigente → 409 version-vigente-protegida; DELETE no
     * vigente → 204 + cascade del arbol copiado solo; el origen sobrevive.
     */
    @Test
    void TC_P31_03_delete_vigente_409_no_vigente_204_y_cascada() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c3@ex.com");
        String proyectoId = crearProyecto(token, "Delete cascade");
        String v1Id = vigenteDeProyecto(proyectoId);
        sembrarArbolCompacto(v1Id);

        // Crear v2 no vigente via deep copy
        String v2Id = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", v1Id))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");

        long v1IdInt = internalPresupuestoId(v1Id);
        long v2IdInt = internalPresupuestoId(v2Id);

        // Antes: v2 tiene cronograma, capitulos, apu y un detalle
        assertTrue(contarFilas("cronograma", "presupuesto_id", v2IdInt) >= 1);
        assertTrue(contarFilas("capitulo", "presupuesto_id", v2IdInt) >= 3);
        assertTrue(contarFilas("apu", "presupuesto_id", v2IdInt) >= 1);
        String nuevoApuId = readApuCodigoPublicId(v2Id, "APU-24");
        assertTrue(contarApuDetalles(internalApuId(nuevoApuId)) >= 1, "al menos un apu_detalle copiado");

        // 1) DELETE sobre la v2 NO vigente → 204 No Content
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + v2Id)
                .then()
                .statusCode(204);

        // 2) Cascada: filas de v2 borradas, v1 sin tocar
        assertEquals(0L, contarFilas("capitulo", "presupuesto_id", v2IdInt));
        assertEquals(0L, contarFilas("apu", "presupuesto_id", v2IdInt));
        assertEquals(0L, contarFilas("cronograma", "presupuesto_id", v2IdInt));
        assertEquals(0L, contarActividades(v2Id));
        // Origen intacto
        assertEquals(1L, contarCronogramas(v1Id));
        assertTrue(leerEsVigente(v1IdInt));

        // 3) DELETE sobre la v1 VIGENTE → 409 version-vigente-protegida
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + v1Id)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("version-vigente-protegida"));

        // La v1 sigue existiendo
        assertTrue(leerEsVigente(v1IdInt));
        assertEquals(1L, contarCronogramas(v1Id));
    }

    /** DELETE sobre presupuesto ajeno o inexistente → 404 (nunca 403). */
    @Test
    void TC_P31_09_delete_sobre_recurso_ajeno_o_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c9-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String vigenteId = vigenteDeProyecto(proyectoId);
        String intruso = AuthSupport.registrarConToken(mailbox, "p24-c9-intruso@ex.com");

        // Ajeno → 404
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .delete("/api/v1/presupuestos/" + vigenteId)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // Inexistente → 404
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // ── PATCH sobre el rubro COPIADO ────────────────────────────────────

    /**
     * TC-P31-04 — mutar el rubro copiado (cantidad 5 → 10) usando el endpoint
     * PATCH existente de RubroResource: el origen permanece bit-a-bit intacto
     * (mismas cantidades y totales). Verifica la independencia estructural
     * entre ambas versiones.
     */
    @Test
    void TC_P31_04_patch_sobre_rubro_copiado_no_altera_el_origen() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c4@ex.com");
        String proyectoId = crearProyecto(token, "Independencia");
        String origenId = vigenteDeProyecto(proyectoId);
        ArbolCompacto orig = sembrarArbolCompacto(origenId);

        // Snapshot completo del origen
        BigDecimal totalOrigen = leerPresupuestoTotal(origenId);
        BigDecimal cantidadOrigen = leerRubroCantidad(orig.rubroPublicId);
        BigDecimal cap1Origen = leerCapituloTotalPorItem(origenId, "1");

        // Crear v2 via deep copy
        String copiaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenId))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");

        // Localizar el rubro copiado y su capitulo por item
        String rubroCopiadoId = readRubroPublicIdPorItem(copiaId, "1.1.1.1");
        String capCopiadoId = readCapituloPublicIdPorItem(copiaId, "1.1.1");

        // PATCH /api/v1/presupuestos/{copiaId}/capitulos/{capCopiadoId}/rubros/{rubroCopiadoId}
        // con cantidad 10.000000 → total nuevo esperado = 10 * 8.40 = 84.00
        BigDecimal cantidadNueva = new BigDecimal("10.000000");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", cantidadNueva.toPlainString()))
                .when()
                .patch("/api/v1/presupuestos/" + copiaId + "/capitulos/" + capCopiadoId + "/rubros/" + rubroCopiadoId)
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(copiaId))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].id", equalTo(rubroCopiadoId))
                .body(
                        "capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].cantidad",
                        equalTo(cantidadNueva.toPlainString()));

        // Persistencia: origen bit-a-bit intacto, copia mutada.
        assertEquals(0, totalOrigen.compareTo(leerPresupuestoTotal(origenId)), "total origen intacto");
        assertEquals(cantidadOrigen, leerRubroCantidad(orig.rubroPublicId), "cantidad origen intacta");
        assertEquals(0, cap1Origen.compareTo(leerCapituloTotalPorItem(origenId, "1")), "cap 1 origen intacto");
    }

    // ── Plan 027 — fingerprint copy + UUID freshness ───────────────────

    /**
     * Plan 027 — el deep copy conserva el {@code presupuesto_fingerprint_revisado}
     * del cronograma origen (la columna se copió explícitamente en V009).
     * El cronograma/actividad copiados reciben {@code public_id}s frescos
     * (el SQL nativo omite {@code public_id} en el INSERT, el DEFAULT los
     * regenera). Las dos copias son bit-a-bit idénticas en su forma pero
     * totalmente independientes en identidad.
     */
    @Test
    void TC_P31_27_deep_copy_conserva_fingerprint_y_renueva_uuids() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p27-c1@ex.com");
        String proyectoId = crearProyecto(token, "Fingerprint copy");
        String origenId = vigenteDeProyecto(proyectoId);
        ArbolCompacto orig = sembrarArbolCompacto(origenId);

        String fingerprintOrigen = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String origenCronogramaPublicId = readCronogramaPublicIdPorPresupuesto(origenId);
        String origenActividadPublicId = readActividadPublicIdPorCronograma(origenId);
        assertNotNull(origenCronogramaPublicId, "Origen debe tener cronograma con public_id");
        assertNotNull(origenActividadPublicId, "Origen debe tener actividad con public_id");

        try (java.sql.Connection con = ds.getConnection();
                java.sql.PreparedStatement ps =
                        con.prepareStatement("UPDATE cronograma SET presupuesto_fingerprint_revisado = ?::char(64) "
                                + "WHERE presupuesto_id = ?")) {
            ps.setString(1, fingerprintOrigen);
            ps.setLong(2, internalPresupuestoId(origenId));
            ps.executeUpdate();
        }

        // POST /proyectos/{proyectoId}/presupuestos con origenId
        String nuevoPresupuestoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenId))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");

        String nuevoCronogramaPublicId = readCronogramaPublicIdPorPresupuesto(nuevoPresupuestoId);
        String nuevoActividadPublicId = readActividadPublicIdPorCronograma(nuevoPresupuestoId);
        String fingerprintCopia = readCronogramaFingerprint(nuevoPresupuestoId);

        assertNotNull(nuevoCronogramaPublicId, "Copia debe tener cronograma con public_id");
        assertNotNull(nuevoActividadPublicId, "Copia debe tener actividad con public_id");
        assertNotEquals(
                origenCronogramaPublicId,
                nuevoCronogramaPublicId,
                "El cronograma copiado debe recibir un public_id fresco");
        assertNotEquals(
                origenActividadPublicId,
                nuevoActividadPublicId,
                "La actividad copiada debe recibir un public_id fresco");
        assertTrue(
                nuevoCronogramaPublicId.matches(UUID_V7),
                "public_id del cronograma copiado debe ser UUIDv7: " + nuevoCronogramaPublicId);
        assertTrue(
                nuevoActividadPublicId.matches(UUID_V7),
                "public_id de la actividad copiada debe ser UUIDv7: " + nuevoActividadPublicId);
        assertEquals(
                fingerprintOrigen,
                fingerprintCopia,
                "El fingerprint del cronograma copiado debe coincidir bit-a-bit con el origen");

        // El origen mantiene su propio publicId y fingerprint intacto.
        assertEquals(origenCronogramaPublicId, readCronogramaPublicIdPorPresupuesto(origenId));
        assertEquals(fingerprintOrigen, readCronogramaFingerprint(origenId));
    }

    // ── GET /presupuestos/{id}/comparar?con={id2} ───────────────────────

    /**
     * Comparar devuelve dos versiones lado a lado con totales y
     * {@code porCapituloRaiz[{item, descripcion, total}]}.
     */
    @Test
    void TC_P31_10_comparar_devuelve_dos_versiones_con_por_capitulo_raiz() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c10@ex.com");
        String proyectoId = crearProyecto(token, "Comparar");
        String v1Id = vigenteDeProyecto(proyectoId);
        sembrarArbolCompacto(v1Id);

        // Crear v2 (no vigente) via deep copy
        String v2Id = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", v1Id))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + v1Id + "/comparar?con=" + v2Id)
                .then()
                .statusCode(200)
                .body("versiones", hasSize(2))
                // El orden de las versiones es estable: presupuesto del path primero, luego el con=
                .body("versiones[0].presupuestoId", equalTo(v1Id))
                .body("versiones[0].version", is(1))
                .body("versiones[0].totalGeneral", equalTo(TOTAL_ORIGEN_ESPERADO.toPlainString()))
                .body("versiones[0].porCapituloRaiz", hasSize(greaterThanOrEqualTo(1)))
                .body("versiones[0].porCapituloRaiz[0].item", equalTo("1"))
                .body("versiones[0].porCapituloRaiz[0].descripcion", equalTo("OBRAS PRELIMINARES"))
                .body("versiones[0].porCapituloRaiz[0].total", equalTo(TOTAL_ORIGEN_ESPERADO.toPlainString()))
                .body("versiones[1].presupuestoId", equalTo(v2Id))
                .body("versiones[1].version", is(2))
                .body("versiones[1].totalGeneral", equalTo(TOTAL_ORIGEN_ESPERADO.toPlainString()));
    }

    /** Comparar con si mismo → 400 validacion. */
    @Test
    void TC_P31_11_comparar_con_self_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c11@ex.com");
        String proyectoId = crearProyecto(token, "Self");
        String v1Id = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + v1Id + "/comparar?con=" + v1Id)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Comparar contra un id de OTRO proyecto → 400 validacion. */
    @Test
    void TC_P31_12_comparar_con_presupuesto_de_otro_proyecto_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c12@ex.com");
        String proyectoA = crearProyecto(token, "A");
        String proyectoB = crearProyecto(token, "B");
        String vA = vigenteDeProyecto(proyectoA);
        String vB = vigenteDeProyecto(proyectoB);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + vA + "/comparar?con=" + vB)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Comparar contra ajeno o inexistente → 404. */
    @Test
    void TC_P31_13_comparar_con_ajeno_o_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p24-c13-owner@ex.com");
        String proyectoId = crearProyecto(token, "Comp 404");
        String v1Id = vigenteDeProyecto(proyectoId);

        String intruso = AuthSupport.registrarConToken(mailbox, "p24-c13-intruso@ex.com");
        String proyectoAjeno = crearProyecto(intruso, "Ajeno");
        String vAjena = given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos/" + proyectoAjeno + "/presupuestos")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].presupuestoId");

        // vAjena pertenece al intruso → el caller legitimo obtiene 404 (no-encontrado)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + v1Id + "/comparar?con=" + vAjena)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + v1Id + "/comparar?con=" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // path ajeno (path presupuestoId del intruso)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + vAjena + "/comparar?con=" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // ── Helpers privados finales ─────────────────────────────────────────

    private boolean leerEsVigente(long presupuestoIdInt) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT es_vigente FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, presupuestoIdInt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private String readRubroPublicIdPorItem(String presupuestoPublicId, String item) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT r.public_id FROM rubro r JOIN capitulo c ON c.id = r.capitulo_id "
                                + "WHERE c.presupuesto_id = ? AND r.item = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readCapituloPublicIdPorItem(String presupuestoPublicId, String item) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT public_id FROM capitulo WHERE presupuesto_id = ? AND item = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private long internalRubroId(String rubroPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM rubro WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(rubroPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long internalApuId(String apuPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(apuPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String readApuCodigoPublicId(String presupuestoPublicId, String codigo) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT public_id FROM apu WHERE presupuesto_id = ? AND codigo = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readApuEspecificacionTecnica(String apuPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT especificacion_tecnica FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(apuPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private BigDecimal readApuPorcentajeDescuento(String apuPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT porcentaje_descuento FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(apuPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal val = rs.getBigDecimal(1);
                return val == null ? BigDecimal.ZERO : val;
            }
        }
    }

    private String readCronogramaPublicIdPorPresupuesto(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (java.sql.Connection con = ds.getConnection();
                java.sql.PreparedStatement ps =
                        con.prepareStatement("SELECT public_id::text FROM cronograma WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readActividadPublicIdPorCronograma(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (java.sql.Connection con = ds.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("SELECT a.public_id::text FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id "
                        + "WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String readCronogramaFingerprint(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (java.sql.Connection con = ds.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement(
                        "SELECT btrim(presupuesto_fingerprint_revisado) FROM cronograma WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    return value == null ? null : value.trim();
                }
                return null;
            }
        }
    }

    private Long readActividadRubroFkPorCronograma(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT a.rubro_id FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id "
                        + "WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
