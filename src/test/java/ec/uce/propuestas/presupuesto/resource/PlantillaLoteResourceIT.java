package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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

/** Focused REST coverage for Plan 003/005 atomic template batches. */
@QuarkusTest
class PlantillaLoteResourceIT {

    private static final String SNAPSHOT_HM = "{\"secciones\":["
            + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
            + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[]},"
            + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
            + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, actividad, cronograma, apu_detalle, apu_seccion, apu, rubro, "
                    + "capitulo, presupuesto, insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "plantilla_apu, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void TC_P05_01_lote_mixto_preserva_orden_usa_ultima_hoja_y_cantidad_uno() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p05-mixto@ex.com");
        long usuarioId = usuarioIdPorEmail("p05-mixto@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Raíz 1");
        String raizDos = crearCapitulo(token, presupuestoId, "Raíz 2");
        crearCapituloHijo(token, presupuestoId, raizDos, "Hoja final");
        UUID sistema = sembrarPlantillaSistema("Sistema lote");
        UUID personal = sembrarPlantillaPersonal(usuarioId, "Personal lote", SNAPSHOT_HM);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", List.of(sistema, personal)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(201)
                .body("resultados.size()", is(2))
                .body("resultados[0].plantillaId", equalTo(sistema.toString()))
                .body("resultados[1].plantillaId", equalTo(personal.toString()))
                .body("resultados[0].codigo", equalTo("APU-001"))
                .body("resultados[1].codigo", equalTo("APU-002"))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros.size()", is(2))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros[0].item", equalTo("2.1.1"))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros[1].item", equalTo("2.1.2"))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros[0].cantidad", equalTo("1.000000"))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros[1].cantidad", equalTo("1.000000"));

        assertEquals(2L, count("select count(*) from apu"));
        assertEquals(2L, count("select count(*) from rubro"));
        assertEquals(2L, count("select count(*) from log_actividad where evento = 'apu.creado'"));
    }

    @Test
    void TC_P05_02_advertencia_de_insumo_faltante_no_revierte_el_lote() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p05-warning@ex.com");
        long usuarioId = usuarioIdPorEmail("p05-warning@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        UUID plantilla = sembrarPlantillaPersonal(
                usuarioId,
                "Plantilla con pendiente",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"P05-NO-EXISTE\",\"cantidad\":1,\"rendimiento\":1}]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
                        + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", List.of(plantilla)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(201)
                .body("resultados.size()", is(1))
                .body("resultados[0].advertencias.size()", greaterThanOrEqualTo(1))
                .body("resultados[0].advertencias[0].insumoCodigo", equalTo("P05-NO-EXISTE"))
                .body("presupuesto.capitulos[0].rubros[0].cantidad", equalTo("1.000000"));

        assertEquals(1L, count("select count(*) from apu"));
        assertEquals(1L, count("select count(*) from rubro"));
    }

    @Test
    void TC_P05_03_fallo_en_segundo_elemento_revierte_apus_rubros_y_logs_con_indice() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "p05-rollback@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "p05-rollback-other@ex.com");
        long bobId = usuarioIdPorEmail("p05-rollback-other@ex.com");
        String proyectoId = crearProyecto(tokenAlice);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(tokenAlice, presupuestoId, "Obras");
        UUID sistema = sembrarPlantillaSistema("Visible primero");
        UUID ajena = sembrarPlantillaPersonal(bobId, "Privada de Bob", SNAPSHOT_HM);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("plantillaIds", List.of(sistema, ajena)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"))
                .body("mensaje", containsString("posición 2"))
                .body("detalles.indice", equalTo(1))
                .body("detalles.plantillaId", equalTo(ajena.toString()));

        assertEquals(0L, count("select count(*) from apu"));
        assertEquals(0L, count("select count(*) from rubro"));
        assertEquals(0L, count("select count(*) from log_actividad where evento = 'apu.creado'"));
    }

    @Test
    void TC_P05_04_valida_limites_duplicados_y_uuid_invalido_sin_mutar() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p05-validation@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        UUID id = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000002001");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", List.of(id, id)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", List.of()))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        List<UUID> demasiadas = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            demasiadas.add(UUID.randomUUID());
        }
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", demasiadas))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals(0L, count("select count(*) from apu"));
        assertEquals(0L, count("select count(*) from rubro"));
    }

    @Test
    void TC_P05_05_uuid_invalido_en_elemento_identifica_su_posicion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p05-invalid-item@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("plantillaIds", List.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"))
                .body("detalles.indice", equalTo(0));

        assertEquals(0L, count("select count(*) from apu"));
        assertEquals(0L, count("select count(*) from rubro"));
    }

    @Test
    void TC_P05_06_owner_scope_y_presupuesto_sin_capitulos_son_errores_atomicamente() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "p05-owner@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "p05-owner-other@ex.com");
        String proyectoAlice = crearProyecto(tokenAlice);
        String presupuestoAlice = presupuestoDeProyecto(proyectoAlice);
        UUID sistema = sembrarPlantillaSistema("Sistema visible");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("plantillaIds", List.of(sistema)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoAlice + "/rubros/desde-plantillas")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("plantillaIds", List.of(sistema)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoAlice + "/rubros/desde-plantillas")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("presupuesto-sin-capitulos"));

        assertEquals(0L, count("select count(*) from apu"));
        assertEquals(0L, count("select count(*) from rubro"));
    }

    @Test
    void TC_P05_07_capitulo_explicito_no_se_sustituye_por_la_ultima_hoja() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p05-explicit@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        String primerCapitulo = crearCapitulo(token, presupuestoId, "Primer capítulo");
        String segundoCapitulo = crearCapitulo(token, presupuestoId, "Último capítulo");
        crearCapituloHijo(token, presupuestoId, segundoCapitulo, "Hoja profunda");
        UUID sistema = sembrarPlantillaSistema("Destino explícito");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("capituloId", primerCapitulo, "plantillaIds", List.of(sistema)))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/rubros/desde-plantillas")
                .then()
                .statusCode(201)
                .body("presupuesto.capitulos[0].rubros[0].item", equalTo("1.1"))
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros.size()", is(0));
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Proyecto Plan 005",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "Universidad Central del Ecuador"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String presupuestoDeProyecto(String proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select p.public_id from presupuesto p join proyecto pr on pr.id = p.proyecto_id "
                                + "where pr.public_id = ? and p.version = 1")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String crearCapitulo(String token, String presupuestoId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[-1].id");
    }

    private String crearCapituloHijo(String token, String presupuestoId, String parentId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion, "parentId", parentId))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[1].subcapitulos[0].id");
    }

    private UUID sembrarPlantillaSistema(String nombre) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO plantilla_apu "
                        + "(nombre, tipo, descripcion_rubro, unidad, snapshot_secciones) "
                        + "VALUES (?, 'SISTEMA', ?, 'u', ?::jsonb) RETURNING public_id")) {
            ps.setString(1, nombre);
            ps.setString(2, nombre + " descripción");
            ps.setString(3, SNAPSHOT_HM);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private UUID sembrarPlantillaPersonal(Long usuarioId, String nombre, String snapshot) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO plantilla_apu "
                        + "(nombre, tipo, usuario_id, descripcion_rubro, unidad, snapshot_secciones) "
                        + "VALUES (?, 'PERSONAL', ?, ?, 'u', ?::jsonb) RETURNING public_id")) {
            ps.setString(1, nombre);
            ps.setLong(2, usuarioId);
            ps.setString(3, nombre + " descripción");
            ps.setString(4, snapshot);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private long usuarioIdPorEmail(String email) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("select id from usuario where email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long count(String sql) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
