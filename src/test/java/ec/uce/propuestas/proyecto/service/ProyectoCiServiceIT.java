package ec.uce.propuestas.proyecto.service;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProyectoCiServiceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, token_usuario, "
                    + "refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private String crearProyecto(String token) {
        String proyectoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "CI individual",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 6,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.0500",
                        "ciIndividualHabilitado", false,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeHerramientaMenor", "0.0500",
                        "porcentajeIndirecto", "0.0500",
                        "iva", "0.1500",
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200);
        return proyectoId;
    }

    private String presupuestoId(String proyectoPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT p.public_id FROM presupuesto p JOIN proyecto pr ON pr.id=p.proyecto_id "
                                + "WHERE pr.public_id=?::uuid AND p.version=1")) {
            ps.setString(1, proyectoPublicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int countOverrides(String proyectoPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM apu a JOIN presupuesto p ON p.id=a.presupuesto_id "
                                + "JOIN proyecto pr ON pr.id=p.proyecto_id "
                                + "WHERE pr.public_id=?::uuid AND a.porcentaje_indirecto IS NOT NULL")) {
            ps.setString(1, proyectoPublicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String ciRate(String proyectoPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT pp.porcentaje_indirecto::text FROM parametros_proyecto pp "
                                + "JOIN proyecto pr ON pr.id=pp.proyecto_id WHERE pr.public_id=?::uuid")) {
            ps.setString(1, proyectoPublicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void corromperRendimientoParaFalloTardio(long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE apu_detalle d SET rendimiento=NULL FROM apu_seccion s "
                                + "WHERE d.seccion_id=s.id AND s.apu_id=? AND s.tipo='MANO_OBRA'")) {
            ps.setLong(1, apuId);
            if (ps.executeUpdate() != 1) throw new AssertionError("Expected exactly one MO row to corrupt");
        }
    }

    private boolean ciIndividualHabilitado(String proyectoPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT pp.ci_individual_habilitado FROM parametros_proyecto pp "
                                + "JOIN proyecto pr ON pr.id=pp.proyecto_id WHERE pr.public_id=?::uuid")) {
            ps.setString(1, proyectoPublicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private String crearApu(String token, String proyectoId) throws Exception {
        return crearApuEnPresupuesto(token, presupuestoId(proyectoId), "CI-001");
    }

    private String crearApuEnPresupuesto(String token, String presupuestoPublicId, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", "APU CI", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoPublicId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String apuPublicId(long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT public_id::text FROM apu WHERE id=?")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String overrideCi(String apuPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT porcentaje_indirecto FROM apu WHERE public_id=?::uuid")) {
            ps.setString(1, apuPublicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private long internalId(String table, String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM " + table + " WHERE public_id=?::uuid")) {
            ps.setString(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarVersionConApu(
            String proyectoId, int version, boolean vigente, String codigo, boolean conCronograma) throws Exception {
        long proyectoInterno = internalId("proyecto", proyectoId);
        try (Connection con = ds.getConnection()) {
            long presupuestoId;
            if (version == 1) {
                try (PreparedStatement ps =
                        con.prepareStatement("SELECT id FROM presupuesto WHERE proyecto_id = ? AND version = 1")) {
                    ps.setLong(1, proyectoInterno);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        presupuestoId = rs.getLong(1);
                    }
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, ?, ?) RETURNING id")) {
                    ps.setLong(1, proyectoInterno);
                    ps.setInt(2, version);
                    ps.setBoolean(3, vigente);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        presupuestoId = rs.getLong(1);
                    }
                }
            }
            long apuId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad) VALUES (?, ?, ?, 'u') RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setString(2, codigo);
                ps.setString(3, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    apuId = rs.getLong(1);
                }
            }
            long moId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'MANO_OBRA', 0, 2) RETURNING id")) {
                ps.setLong(1, apuId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    moId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'EQUIPO', 0, 1) RETURNING id")) {
                ps.setLong(1, apuId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long equipo = rs.getLong(1);
                    try (PreparedStatement hm = con.prepareStatement(
                            "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, costo_hora, unidad, costo) VALUES (?, 'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
                        hm.setLong(1, equipo);
                        hm.executeUpdate();
                    }
                }
            }
            long insumoId;
            long baseId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO base_insumos (nombre, tipo, proyecto_id, archivada) VALUES ('CI Base', 'PROYECTO', ?, FALSE) RETURNING id")) {
                ps.setLong(1, proyectoInterno);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    baseId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) VALUES (?, ?, 'MANO_OBRA', ?, 'h', 10) RETURNING id")) {
                ps.setLong(1, baseId);
                ps.setString(2, "MO-" + codigo);
                ps.setString(3, "Mano de obra " + codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    insumoId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_detalle (seccion_id, insumo_id, descripcion, orden, cantidad, es_herramienta_menor, rendimiento, unidad, costo) VALUES (?, ?, ?, 1, 1, FALSE, 1, 'h', 0)")) {
                ps.setLong(1, moId);
                ps.setLong(2, insumoId);
                ps.setString(3, "MO " + codigo);
                ps.executeUpdate();
            }
            long capituloId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, item, descripcion, orden, total) VALUES (?, '1', 'Capítulo CI', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    capituloId = rs.getLong(1);
                }
            }
            long rubroId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad, precio_unitario, precio_total) VALUES (?, ?, '1.1', ?, ?, 'u', 2, 0, 0) RETURNING id")) {
                ps.setLong(1, capituloId);
                ps.setLong(2, apuId);
                ps.setString(3, codigo);
                ps.setString(4, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rubroId = rs.getLong(1);
                }
            }
            if (conCronograma) {
                long cronogramaId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos) VALUES (?, 'MES', 1) RETURNING id")) {
                    ps.setLong(1, presupuestoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        cronogramaId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) VALUES (?, ?, 100, '{}'::jsonb)")) {
                    ps.setLong(1, cronogramaId);
                    ps.setLong(2, rubroId);
                    ps.executeUpdate();
                }
            }
            return apuId;
        }
    }

    private void assertDerivedApu(long apuId, String ci, String apuTotal, String rubroTotal) throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(
                "10.500000", value("SELECT costo_directo::text FROM apu WHERE id=" + apuId, null));
        org.junit.jupiter.api.Assertions.assertEquals(
                ci, value("SELECT costo_indirecto::text FROM apu WHERE id=" + apuId, null));
        org.junit.jupiter.api.Assertions.assertEquals(
                apuTotal, value("SELECT costo_total::text FROM apu WHERE id=" + apuId, null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "10.000000",
                value(
                        "SELECT s.subtotal::text FROM apu_seccion s WHERE s.apu_id=" + apuId
                                + " AND s.tipo='MANO_OBRA'",
                        null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "0.500000",
                value(
                        "SELECT s.subtotal::text FROM apu_seccion s WHERE s.apu_id=" + apuId + " AND s.tipo='EQUIPO'",
                        null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "10.000000",
                value(
                        "SELECT d.costo::text FROM apu_detalle d JOIN apu_seccion s ON s.id=d.seccion_id "
                                + "WHERE s.apu_id=" + apuId + " AND s.tipo='MANO_OBRA'",
                        null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "0.500000",
                value(
                        "SELECT d.costo::text FROM apu_detalle d JOIN apu_seccion s ON s.id=d.seccion_id "
                                + "WHERE s.apu_id=" + apuId + " AND s.tipo='EQUIPO'",
                        null));
        org.junit.jupiter.api.Assertions.assertEquals(
                apuTotal, value("SELECT r.precio_unitario::text FROM rubro r WHERE r.apu_id=" + apuId, null));
        org.junit.jupiter.api.Assertions.assertEquals(
                rubroTotal, value("SELECT r.precio_total::text FROM rubro r WHERE r.apu_id=" + apuId, null));
    }

    private String value(String sql, String projectId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            if (sql.contains("?")) ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    void cambio_ci_preservar_y_restablecer_actualiza_costos_todas_las_versiones_y_cronograma() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-costs@ex.com");
        String proyectoId = crearProyecto(token);
        long apuV1 = insertarVersionConApu(proyectoId, 1, true, "CI-V1", true);
        long apuV2 = insertarVersionConApu(proyectoId, 2, false, "CI-V2", true);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.1000",
                        "ciIndividualHabilitado",
                        true,
                        "politicaOverrides",
                        "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);
        String costoInicialV1 = value("SELECT costo_total::text FROM apu WHERE id=" + apuV1, proyectoId);
        String costoInicialV2 = value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId);
        org.junit.jupiter.api.Assertions.assertEquals("11.550000", costoInicialV1);
        org.junit.jupiter.api.Assertions.assertEquals("11.550000", costoInicialV2);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.4000")
                .when()
                .patch("/api/v1/apus/" + apuPublicId(apuV2) + "/porcentaje-indirecto")
                .then()
                .statusCode(200);
        org.junit.jupiter.api.Assertions.assertEquals(
                "14.700000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.2000",
                        "ciIndividualHabilitado",
                        true,
                        "politicaOverrides",
                        "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);
        org.junit.jupiter.api.Assertions.assertEquals(
                "12.600000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV1, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "14.700000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals("0.4000", overrideCi(apuPublicId(apuV2)));
        org.junit.jupiter.api.Assertions.assertEquals(
                "25.200000",
                value(
                        "SELECT r.precio_total::text FROM rubro r JOIN apu a ON a.id=r.apu_id WHERE a.id=" + apuV1,
                        proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "25.200000",
                value(
                        "SELECT c.total::text FROM capitulo c JOIN presupuesto p ON p.id=c.presupuesto_id WHERE p.version=1 AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                        proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "29.400000",
                value(
                        "SELECT total::text FROM presupuesto WHERE version=2 AND proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                        proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "100.0000",
                value(
                        "SELECT a.peso_ponderado::text FROM actividad a JOIN cronograma c ON c.id=a.cronograma_id JOIN presupuesto p ON p.id=c.presupuesto_id WHERE p.version=2 AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                        proyectoId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.3000",
                        "ciIndividualHabilitado",
                        true,
                        "politicaOverrides",
                        "RESTABLECER"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("cantidadOverrides", is(0));
        org.junit.jupiter.api.Assertions.assertEquals(
                "13.650000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV1, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "13.650000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId));
        org.junit.jupiter.api.Assertions.assertNull(overrideCi(apuPublicId(apuV2)));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.4000")
                .when()
                .patch("/api/v1/apus/" + apuPublicId(apuV2) + "/porcentaje-indirecto")
                .then()
                .statusCode(200);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.3500",
                        "ciIndividualHabilitado",
                        false,
                        "politicaOverrides",
                        "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("ciIndividualHabilitado", is(false))
                .body("cantidadOverrides", is(0));
        org.junit.jupiter.api.Assertions.assertNull(overrideCi(apuPublicId(apuV2)));
        org.junit.jupiter.api.Assertions.assertEquals(
                "14.175000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV1, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "14.175000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "28.340000",
                value(
                        "SELECT total::text FROM presupuesto WHERE version=2 AND proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                        proyectoId));
    }

    @Test
    void inherited_apu_created_after_ci_save_uses_new_rate_while_all_versions_recalculate() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-create-after-save@ex.com");
        String proyectoId = crearProyecto(token);
        long apuV1 = insertarVersionConApu(proyectoId, 1, true, "CI-AFTER-SAVE-V1", false);
        long apuV2 = insertarVersionConApu(proyectoId, 2, false, "CI-AFTER-SAVE-V2", false);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.1000",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);

        String inheritedV1 = crearApuEnPresupuesto(token, presupuestoId(proyectoId), "CI-AFTER-SAVE-NEW-V1");
        String presupuestoV2 = value(
                "SELECT public_id::text FROM presupuesto WHERE version=2 AND proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                proyectoId);
        String inheritedV2 = crearApuEnPresupuesto(token, presupuestoV2, "CI-AFTER-SAVE-NEW-V2");

        String tasaEfectivaV1 = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + inheritedV1)
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", org.hamcrest.Matchers.nullValue())
                .extract()
                .path("porcentajeIndirectoEfectivo")
                .toString();
        String tasaEfectivaV2 = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + inheritedV2)
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", org.hamcrest.Matchers.nullValue())
                .extract()
                .path("porcentajeIndirectoEfectivo")
                .toString();
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new java.math.BigDecimal(tasaEfectivaV1).compareTo(new java.math.BigDecimal("0.1000")));
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new java.math.BigDecimal(tasaEfectivaV2).compareTo(new java.math.BigDecimal("0.1000")));
        org.junit.jupiter.api.Assertions.assertEquals(
                "11.550000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV1, proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "11.550000", value("SELECT costo_total::text FROM apu WHERE id=" + apuV2, proyectoId));
    }

    @Test
    void version_copy_denies_legacy_override_when_ci_mode_is_disabled_and_preserves_it_when_enabled() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-copy@ex.com");
        String proyectoId = crearProyecto(token);
        long origenApuId = insertarVersionConApu(proyectoId, 1, true, "CI-COPY-ORIGIN", false);
        String origenApuPublicId = apuPublicId(origenApuId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE apu SET porcentaje_indirecto = 0.4000 WHERE id = ?")) {
            ps.setLong(1, origenApuId);
            ps.executeUpdate();
        }

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", presupuestoId(proyectoId)))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("ci-individual-deshabilitado"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "1",
                value(
                        "SELECT count(*)::text FROM presupuesto WHERE proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                        proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals("0.4000", overrideCi(origenApuPublicId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.1800",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);

        String copiaPresupuestoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", presupuestoId(proyectoId)))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");
        String copiaApuId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id::text FROM apu WHERE presupuesto_id=(SELECT id FROM presupuesto WHERE public_id=?::uuid)")) {
            ps.setString(1, copiaPresupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                copiaApuId = rs.getString(1);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals("0.4000", overrideCi(copiaApuId));
        org.junit.jupiter.api.Assertions.assertEquals("0.4000", overrideCi(origenApuPublicId));
    }

    @Test
    void falla_en_apu_tardio_revierte_ci_overrides_y_derivados_tras_escritura_previa() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-late-rollback@ex.com");
        String proyectoId = crearProyecto(token);
        long apuV1 = insertarVersionConApu(proyectoId, 1, true, "CI-ROLLBACK-V1", false);
        long apuV2 = insertarVersionConApu(proyectoId, 2, false, "CI-ROLLBACK-V2", false);
        long apuV3 = insertarVersionConApu(proyectoId, 3, false, "CI-ROLLBACK-V3", false);
        org.junit.jupiter.api.Assertions.assertTrue(apuV1 < apuV2 && apuV2 < apuV3);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.1000",
                        "ciIndividualHabilitado",
                        true,
                        "politicaOverrides",
                        "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.4000")
                .when()
                .patch("/api/v1/apus/" + apuPublicId(apuV3) + "/porcentaje-indirecto")
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals("0.1000", ciRate(proyectoId));
        org.junit.jupiter.api.Assertions.assertTrue(ciIndividualHabilitado(proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(1, countOverrides(proyectoId));
        assertDerivedApu(apuV1, "1.050000", "11.550000", "23.100000");
        assertDerivedApu(apuV2, "1.050000", "11.550000", "23.100000");
        assertDerivedApu(apuV3, "4.200000", "14.700000", "29.400000");
        for (int version = 1; version <= 3; version++) {
            String total = version == 3 ? "29.400000" : "23.100000";
            org.junit.jupiter.api.Assertions.assertEquals(
                    total,
                    value(
                            "SELECT c.total::text FROM capitulo c JOIN presupuesto p ON p.id=c.presupuesto_id "
                                    + "WHERE p.version=" + version
                                    + " AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                            proyectoId));
            org.junit.jupiter.api.Assertions.assertEquals(
                    total,
                    value(
                            "SELECT p.total::text FROM presupuesto p WHERE p.version=" + version
                                    + " AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                            proyectoId));
        }
        String overrideBefore = overrideCi(apuPublicId(apuV3));
        corromperRendimientoParaFalloTardio(apuV2);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto",
                        "0.2000",
                        "ciIndividualHabilitado",
                        true,
                        "politicaOverrides",
                        "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(500);

        org.junit.jupiter.api.Assertions.assertEquals("0.1000", ciRate(proyectoId));
        org.junit.jupiter.api.Assertions.assertTrue(ciIndividualHabilitado(proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(1, countOverrides(proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(overrideBefore, overrideCi(apuPublicId(apuV3)));
        assertDerivedApu(apuV1, "1.050000", "11.550000", "23.100000");
        assertDerivedApu(apuV2, "1.050000", "11.550000", "23.100000");
        assertDerivedApu(apuV3, "4.200000", "14.700000", "29.400000");
        for (int version = 1; version <= 3; version++) {
            String total = version == 3 ? "29.400000" : "23.100000";
            org.junit.jupiter.api.Assertions.assertEquals(
                    total,
                    value(
                            "SELECT r.precio_total::text FROM rubro r JOIN apu a ON a.id=r.apu_id "
                                    + "JOIN capitulo c ON c.id=r.capitulo_id JOIN presupuesto p ON p.id=c.presupuesto_id "
                                    + "WHERE p.version=" + version
                                    + " AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                            proyectoId));
            org.junit.jupiter.api.Assertions.assertEquals(
                    total,
                    value(
                            "SELECT c.total::text FROM capitulo c JOIN presupuesto p ON p.id=c.presupuesto_id "
                                    + "WHERE p.version=" + version
                                    + " AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                            proyectoId));
            org.junit.jupiter.api.Assertions.assertEquals(
                    total,
                    value(
                            "SELECT p.total::text FROM presupuesto p WHERE p.version=" + version
                                    + " AND p.proyecto_id=(SELECT id FROM proyecto WHERE public_id=?::uuid)",
                            proyectoId));
        }
    }

    @Test
    void guarda_tasa_y_opt_in_de_ci_en_endpoint_dedicado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-save@ex.com");
        String proyectoId = crearProyecto(token);
        org.junit.jupiter.api.Assertions.assertFalse(ciIndividualHabilitado(proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(0, countOverrides(proyectoId));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.0500f))
                .body("ciIndividualHabilitado", is(false))
                .body("cantidadOverrides", is(0));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.1800",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.18f))
                .body("ciIndividualHabilitado", is(true))
                .body("cantidadOverrides", is(0));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.18f))
                .body("ciIndividualHabilitado", is(true))
                .body("cantidadOverrides", is(0));

        Map<String, Object> limpiarCi = new LinkedHashMap<>();
        limpiarCi.put("porcentajeIndirecto", null);
        limpiarCi.put("ciIndividualHabilitado", true);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(limpiarCi)
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", is((Object) null));
    }

    @Test
    void rechaza_porcentaje_indirecto_con_mas_de_cuatro_decimales_sin_redondear() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-scale@ex.com");
        String proyectoId = crearProyecto(token);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.12345",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(400);

        org.junit.jupiter.api.Assertions.assertEquals("0.0500", ciRate(proyectoId));
    }

    @Test
    void requiere_politica_para_overrides_y_preservar_o_restablecer_es_atomico() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-policy@ex.com");
        String proyectoId = crearProyecto(token);
        String apuId = crearApu(token, proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2200")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("ci-individual-deshabilitado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.1800",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2200")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(200);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("porcentajeIndirecto", "0.2500", "ciIndividualHabilitado", true))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.18f))
                .body("cantidadOverrides", is(1));
        org.junit.jupiter.api.Assertions.assertEquals("0.2200", overrideCi(apuId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.2500",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("cantidadOverrides", is(1));
        org.junit.jupiter.api.Assertions.assertEquals("0.2200", overrideCi(apuId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.2500",
                        "ciIndividualHabilitado", true,
                        "politicaOverrides", "RESTABLECER"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("cantidadOverrides", is(0));
        org.junit.jupiter.api.Assertions.assertNull(overrideCi(apuId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2200")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(200);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "porcentajeIndirecto", "0.2500",
                        "ciIndividualHabilitado", false,
                        "politicaOverrides", "PRESERVAR"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200)
                .body("ciIndividualHabilitado", is(false))
                .body("cantidadOverrides", is(0));
        org.junit.jupiter.api.Assertions.assertNull(overrideCi(apuId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2200")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("ci-individual-deshabilitado"));
    }

    @Test
    void legacy_parametros_rechaza_cambios_ci_pero_conserva_actualizaciones_ajenas_y_ci_igual() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "project-ci-legacy@ex.com");
        String proyectoId = crearProyecto(token);

        Map<String, Object> cambioCi = new LinkedHashMap<>();
        cambioCi.put("porcentajeHerramientaMenor", "0.0700");
        cambioCi.put("porcentajeIndirecto", "0.1800");
        cambioCi.put("iva", "0.1600");
        cambioCi.put("moneda", "EUR");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(cambioCi)
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"))
                .body("mensaje", containsString("/proyectos/" + proyectoId + "/ci"));

        Map<String, Object> actualizacionLegada = new LinkedHashMap<>();
        actualizacionLegada.put("porcentajeHerramientaMenor", "0.0700");
        actualizacionLegada.put("porcentajeIndirecto", "0.0500");
        actualizacionLegada.put("iva", "0.1600");
        actualizacionLegada.put("moneda", "EUR");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(actualizacionLegada)
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.0500f))
                .body("porcentajeHerramientaMenor", equalTo(0.07f))
                .body("iva", equalTo(0.16f))
                .body("moneda", equalTo("EUR"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("porcentajeIndirecto", "0.1800", "ciIndividualHabilitado", true))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(200);
        actualizacionLegada.put("porcentajeHerramientaMenor", "0.0800");
        actualizacionLegada.put("porcentajeIndirecto", "0.1800");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(actualizacionLegada)
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(0.18f))
                .body("porcentajeHerramientaMenor", equalTo(0.08f));
    }

    @Test
    void owner_scope_y_rango_ci_se_validan_en_endpoint_dedicado() {
        String tokenOwner = AuthSupport.registrarConToken(mailbox, "project-ci-owner@ex.com");
        String proyectoId = crearProyecto(tokenOwner);
        String tokenIntruso = AuthSupport.registrarConToken(mailbox, "project-ci-intruder@ex.com");

        given().header("Authorization", "Bearer " + tokenIntruso)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenOwner)
                .body(Map.of("porcentajeIndirecto", "1.0100", "ciIndividualHabilitado", true))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().contentType(JSON)
                .body(Map.of("porcentajeIndirecto", "0.1800", "ciIndividualHabilitado", true))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/ci")
                .then()
                .statusCode(401);
    }
}
