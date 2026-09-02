package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 025 — flujo REST de validación de integridad (P-32).
 *
 * <p>Cubre el contrato del endpoint
 * {@code GET /api/v1/presupuestos/{presupuestoId}/validacion}, que devuelve tres
 * listas planas de referencias a rubros con defectos y un flag
 * {@code exportable} derivado del tamaño agregado de esas listas. La forma
 * esperada es:
 * <ul>
 *   <li>{@code itemsPuCero}: rubros con {@code rubro.precio_unitario = 0}.</li>
 *   <li>{@code itemsCantidadCero}: rubros con {@code rubro.cantidad = 0}.</li>
 *   <li>{@code itemsSinActividad}: rubros sin actividad en el cronograma del
 *       presupuesto del path.</li>
 *   <li>{@code exportable}: {@code true} cuando las tres listas están vacías;
 *       {@code false} en caso contrario.</li>
 * </ul>
 *
 * <p>Cada elemento de las tres listas es un {@code RubroRefResponse} reducido,
 * exactamente {@code {id UUIDv7, item, codigo, descripcion}}. No se exponen los
 * campos del {@code RubroResponse} completo (cantidad, precios, unidad, apuId)
 * ni el {@code BIGINT} interno del rubro.</p>
 *
 * <p>Estos tests fueron redactados como contrato antes de la implementación de
 * Plan 025 y ahora verifican el endpoint implementado
 * {@code /api/v1/presupuestos/{presupuestoId}/validacion}.</p>
 *
 * <p>Convenciones heredadas de los planes 021–024 (AuthSupport, DataSource,
 * REST-assured):</p>
 * <ul>
 *   <li>El {@code presupuestoId} del path es UUIDv7 del {@code public_id} de la
 *       fila {@code presupuesto} (Plan 07 / WU-03); el {@code BIGINT} interno
 *       nunca aparece en JSON.</li>
 *   <li>Los fixtures se siembran con SQL nativo para respetar el stop condition
 *       (B): "No crear entidades JPA {@code Actividad}/{@code Cronograma}"; la
 *       implementación del recurso usará el mismo enfoque (mirroring Plan 024).</li>
 *   <li>APU/rubro con valores directos: {@code cantidad=0} y
 *       {@code precio_unitario=0} son estados legales a nivel BD (V007 / V001)
 *       aunque REST P-29 prohíba el primero en creación; se siembran por SQL
 *       directamente. La consulta de validación P-32 debe reflejarlos como
 *       alerta.</li>
 *   <li>Reset: TRUNCATE ... CASCADE que aísla explícitamente
 *       {@code cronograma} y {@code actividad} (no son JPA en este módulo) y
 *       todas las dependencias actuales de presupuesto/APU.</li>
 * </ul>
 */
@QuarkusTest
class PresupuestoValidacionResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            // Reset aísla cronograma + actividad (no JPA) y todas las dependencias
            // actuales del módulo presupuesto/APU. CASCADE borra hijos
            // (capitulos hijos, rubros, apu_seccion, apu_detalle,
            // presupuesto_rubro, cronograma_actividad) sin orden manual.
            st.execute("TRUNCATE TABLE cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers — proyecto + árbol + APU + rubro + cronograma + actividad
    // ──────────────────────────────────────────────────────────────────────

    /** Crea un proyecto del caller y devuelve el UUID público del proyecto. */
    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-P25",
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

    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalRubroId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM rubro WHERE public_id = ?")) {
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

    /** Inserta una versión adicional (no vigente) del mismo proyecto. */
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

    /**
     * Inserta un APU con costo_directo y costo_total positivos por defecto
     * (costo_indirecto queda en 0). El default sirve para tests "camino
     * positivo" donde la consulta P-32 debe tratar el APU como válido (p.ej.
     * TC_P32_03 / TC_P32_04, que siembran el rubro vía {@link #insertarRubro}
     * con {@code precio_unitario = 10} copiando el costo_total del APU). El
     * camino REST P-29 ya no se usa en esos tests porque dispara el motor de
     * recálculo sobre APUs sin secciones y reescribe el costo_total a 0, lo
     * que sesgaría el snapshot válido bajo prueba. Tests que necesitan forzar
     * {@code rubro.precio_unitario=0} lo sobrescriben explícitamente vía
     * {@link #insertarRubro} y no dependen del default del APU.
     */
    private String insertarApu(String presupuestoPublicId, String codigo, String descripcion) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                + "costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, ?, ?, 'u', 10.000000, 0, 10.000000) RETURNING public_id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, descripcion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Capitulo raíz vía REST (P-22). Devuelve UUIDv7 del capítulo. */
    private String crearCapituloRaiz(String token, String presupuestoId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");
    }

    /**
     * Inserta un rubro por SQL con los valores crudos deseados. Cantidad=0 y
     * precio_unitario=0 son estados permitidos a nivel BD (V007 / V001) aunque
     * la API REST P-29 los prohíba en creación — la consulta de validación
     * P-32 debe reflejarlos como alerta, así que sembramos directamente.
     * Asume que el presupuesto tiene al menos un capítulo creado.
     */
    private String insertarRubro(
            String presupuestoPublicId,
            String apuPublicId,
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario)
            throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        Long apuId = internalApuId(apuPublicId);
        long capId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT id FROM capitulo WHERE presupuesto_id = ? ORDER BY id LIMIT 1")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                capId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                                + "cantidad, precio_unitario, precio_total) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0) RETURNING public_id")) {
            ps.setLong(1, capId);
            ps.setLong(2, apuId);
            ps.setString(3, item);
            ps.setString(4, codigo);
            ps.setString(5, descripcion);
            ps.setString(6, unidad);
            ps.setBigDecimal(7, cantidad);
            ps.setBigDecimal(8, precioUnitario);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Inserta un cronograma de 12 periodos SEMANA para el presupuesto. */
    private long insertarCronograma(String presupuestoPublicId) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos) "
                                + "VALUES (?, 'SEMANA', 12) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Inserta una actividad vinculando un rubro al cronograma. La columna
     * {@code avance_por_periodo} es JSONB; sembramos 12 entradas vacías para
     * preservar la forma estructural del contrato.
     */
    private long insertarActividad(long cronogramaId, long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) "
                                + "VALUES (?, ?, 0.5000, ?::jsonb) RETURNING id")) {
            ps.setLong(1, cronogramaId);
            ps.setLong(2, rubroId);
            ps.setString(3, "[\"P1\",\"P2\",\"P3\",\"P4\",\"P5\",\"P6\",\"P7\",\"P8\",\"P9\",\"P10\",\"P11\",\"P12\"]");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarRubros(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarApus(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM apu WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarActividadesPorRubro(long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM actividad WHERE rubro_id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC-P32-01 (canónico): presupuesto con tres defectos independientes
     * repartidos en tres rubros distintos — uno con PU=0, uno con cantidad=0,
     * uno sin actividad. Cada lista se puebla con la referencia al rubro
     * defectuoso y {@code exportable=false}. Los defectos son ortogonales: un
     * rubro puede estar en múltiples listas si acumula varios defectos.
     */
    @Test
    void TC_P32_01_canonico_tres_defectos_listas_pobladas_y_no_exportable() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c1@ex.com");
        String proyectoId = crearProyecto(token, "TC-P32-01 canonico");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuPuCero = insertarApu(presupuestoId, "APU-PU0", "APU con PU=0");
        String apuCantCero = insertarApu(presupuestoId, "APU-C0", "APU con cantidad 0");
        String apuSinAct = insertarApu(presupuestoId, "APU-SA", "APU sin actividad");

        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo canonico");

        // rubro PU=0 (cantidad 5, con actividad)
        String rubroPuCeroId = insertarRubro(
                presupuestoId,
                apuPuCero,
                "1.1",
                "APU-PU0",
                "APU con PU=0",
                "u",
                new BigDecimal("5.000000"),
                BigDecimal.ZERO);

        // rubro cantidad=0 (PU 10, con actividad). V007 lo permite a nivel BD;
        // REST P-29 lo prohíbe en creación, por eso se inserta por SQL directo.
        String rubroCantCeroId = insertarRubro(
                presupuestoId,
                apuCantCero,
                "1.2",
                "APU-C0",
                "APU con cantidad 0",
                "u",
                BigDecimal.ZERO,
                new BigDecimal("10.000000"));

        // rubro PU>0 + cantidad>0 SIN actividad (no se le crea actividad luego)
        String rubroSinActId = insertarRubro(
                presupuestoId,
                apuSinAct,
                "1.3",
                "APU-SA",
                "APU sin actividad",
                "u",
                new BigDecimal("3.000000"),
                new BigDecimal("7.500000"));

        // Cronograma + actividades sólo para los dos primeros rubros;
        // rubroSinAct queda deliberadamente sin actividad → itemsSinActividad
        long cronogramaId = insertarCronograma(presupuestoId);
        insertarActividad(cronogramaId, internalRubroId(rubroPuCeroId));
        insertarActividad(cronogramaId, internalRubroId(rubroCantCeroId));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("itemsPuCero", hasSize(1))
                .body("itemsPuCero[0].id", equalTo(rubroPuCeroId))
                .body("itemsPuCero[0].item", equalTo("1.1"))
                .body("itemsPuCero[0].codigo", equalTo("APU-PU0"))
                .body("itemsPuCero[0].descripcion", equalTo("APU con PU=0"))
                .body("itemsCantidadCero", hasSize(1))
                .body("itemsCantidadCero[0].id", equalTo(rubroCantCeroId))
                .body("itemsCantidadCero[0].item", equalTo("1.2"))
                .body("itemsCantidadCero[0].codigo", equalTo("APU-C0"))
                .body("itemsCantidadCero[0].descripcion", equalTo("APU con cantidad 0"))
                .body("itemsSinActividad", hasSize(1))
                .body("itemsSinActividad[0].id", equalTo(rubroSinActId))
                .body("itemsSinActividad[0].item", equalTo("1.3"))
                .body("itemsSinActividad[0].codigo", equalTo("APU-SA"))
                .body("itemsSinActividad[0].descripcion", equalTo("APU sin actividad"));
    }

    /**
     * Presupuesto recién creado sin rubros ni cronograma: las tres listas
     * vacías y {@code exportable=true}.
     */
    @Test
    void TC_P32_02_presupuesto_vacio_listas_vacias_y_exportable_true() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c2@ex.com");
        String proyectoId = crearProyecto(token, "Vacio");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true))
                .body("itemsPuCero", hasSize(0))
                .body("itemsCantidadCero", hasSize(0))
                .body("itemsSinActividad", hasSize(0));
    }

    /**
     * Versión válida completa: PU>0, cantidad>0, cronograma+actividad. Las
     * tres listas vacías y {@code exportable=true}.
     */
    @Test
    void TC_P32_03_version_valida_listas_vacias_y_exportable_true() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c3@ex.com");
        String proyectoId = crearProyecto(token, "Valido");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-OK", "APU valido");
        // Capítulo requerido para que insertarRubro localice el FK por SQL directo.
        crearCapituloRaiz(token, presupuestoId, "Capitulo valido");
        // insertarRubro siembra por SQL directo el estado persistido PU>0 /
        // cantidad>0 sin pasar por REST P-29: el endpoint de creación dispara el
        // motor de recálculo sobre APUs sin secciones y reescribe
        // apu.costo_total = 0 → rubro.precio_unitario = 0, lo que ensuciaría el
        // snapshot válido que este test quiere representar. Es fixture puro: lo
        // que se valida aquí es la lectura P-32, no la creación P-29.
        String rubroId = insertarRubro(
                presupuestoId,
                apuId,
                "1.1",
                "APU-OK",
                "APU valido",
                "u",
                new BigDecimal("5.000000"),
                new BigDecimal("10.000000"));

        long cronogramaId = insertarCronograma(presupuestoId);
        insertarActividad(cronogramaId, internalRubroId(rubroId));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true))
                .body("itemsPuCero", hasSize(0))
                .body("itemsCantidadCero", hasSize(0))
                .body("itemsSinActividad", hasSize(0));
    }

    /**
     * Presupuesto con rubros pero sin cronograma: cada rubro queda forzado a
     * aparecer en {@code itemsSinActividad} (no existe ninguna actividad
     * posible sin cronograma). {@code exportable=false}.
     */
    @Test
    void TC_P32_04_sin_cronograma_todos_los_rubros_en_sin_actividad() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c4@ex.com");
        String proyectoId = crearProyecto(token, "Sin cronograma");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuA = insertarApu(presupuestoId, "APU-A", "A");
        String apuB = insertarApu(presupuestoId, "APU-B", "B");
        String apuC = insertarApu(presupuestoId, "APU-C", "C");
        // Capítulo requerido para que insertarRubro localice el FK por SQL directo.
        crearCapituloRaiz(token, presupuestoId, "Sin crono");

        // 3 rubros válidos (cantidad>0, PU>0) sembrados por SQL directo con
        // items 1.1/1.2/1.3 fijando el orden determinista que asserta
        // itemsSinActividad. REST P-29 se evita aquí porque dispara el motor
        // de recálculo sobre APUs sin secciones y reescribe apu.costo_total =
        // 0 → rubro.precio_unitario = 0, lo que forzaría PU=0 en un snapshot
        // que debe reportarse como válido. Es fixture puro: lo que se valida
        // es la lectura P-32, no la creación P-29.
        String rubroA = insertarRubro(
                presupuestoId, apuA, "1.1", "APU-A", "A", "u", new BigDecimal("2.000000"), new BigDecimal("10.000000"));
        String rubroB = insertarRubro(
                presupuestoId, apuB, "1.2", "APU-B", "B", "u", new BigDecimal("3.000000"), new BigDecimal("10.000000"));
        String rubroC = insertarRubro(
                presupuestoId, apuC, "1.3", "APU-C", "C", "u", new BigDecimal("4.000000"), new BigDecimal("10.000000"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("itemsPuCero", hasSize(0))
                .body("itemsCantidadCero", hasSize(0))
                .body("itemsSinActividad", hasSize(3))
                .body("itemsSinActividad.id", equalTo(List.of(rubroA, rubroB, rubroC)));
    }

    /**
     * Defectos independientes: un único rubro con PU=0 AND cantidad=0 AND sin
     * actividad debe aparecer en las tres listas a la vez. Ninguno de los
     * tres defectos se deduce de los otros dos.
     */
    @Test
    void TC_P32_05_un_rubro_con_multiples_defectos_en_varias_listas() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c5@ex.com");
        String proyectoId = crearProyecto(token, "Multi-defecto");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-MULTI", "Multi defecto");
        String capId = crearCapituloRaiz(token, presupuestoId, "Multi defecto");
        String rubroId = insertarRubro(
                presupuestoId, apuId, "1.1", "APU-MULTI", "Multi defecto", "u", BigDecimal.ZERO, BigDecimal.ZERO);
        // Sin actividad: no se crea cronograma

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("itemsPuCero", hasSize(1))
                .body("itemsPuCero[0].id", equalTo(rubroId))
                .body("itemsCantidadCero", hasSize(1))
                .body("itemsCantidadCero[0].id", equalTo(rubroId))
                .body("itemsSinActividad", hasSize(1))
                .body("itemsSinActividad[0].id", equalTo(rubroId));
    }

    /**
     * Orden determinista: las tres listas devuelven los rubros en orden
     * ascendente por {@code item}. Sembramos 3 rubros con PU=0 con items no
     * contiguos en orden de inserción distinto al de salida, para descartar
     * accidentalmente devolver el orden de inserción.
     */
    @Test
    void TC_P32_06_orden_determinista_por_item_en_listas() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c6@ex.com");
        String proyectoId = crearProyecto(token, "Orden");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // D-09 UNIQUE(rubro.apu_id): un mismo APU no puede referenciarse
        // desde tres rubros distintos, así que creamos un APU por rubro.
        // Los códigos del rubro siguen siendo "APU-ORD" para no contaminar
        // el contrato del test (la aserción sólo verifica el orden por item).
        String apuC = insertarApu(presupuestoId, "APU-ORD-C", "Orden C");
        String apuA = insertarApu(presupuestoId, "APU-ORD-A", "Orden A");
        String apuB = insertarApu(presupuestoId, "APU-ORD-B", "Orden B");
        String capId = crearCapituloRaiz(token, presupuestoId, "Orden");

        // Items deliberadamente desordenados respecto a la salida esperada
        String rC = insertarRubro(
                presupuestoId, apuC, "1.3", "APU-ORD", "Orden C", "u", new BigDecimal("5.000000"), BigDecimal.ZERO);
        String rA = insertarRubro(
                presupuestoId, apuA, "1.1", "APU-ORD", "Orden A", "u", new BigDecimal("5.000000"), BigDecimal.ZERO);
        String rB = insertarRubro(
                presupuestoId, apuB, "1.2", "APU-ORD", "Orden B", "u", new BigDecimal("5.000000"), BigDecimal.ZERO);

        // Assert: orden ascendente por item en itemsPuCero
        List<LinkedHashMap<String, String>> lista = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("itemsPuCero");

        assertEquals(3, lista.size());
        assertEquals("1.1", lista.get(0).get("item"));
        assertEquals(rA, lista.get(0).get("id"));
        assertEquals("1.2", lista.get(1).get("item"));
        assertEquals(rB, lista.get(1).get("id"));
        assertEquals("1.3", lista.get(2).get("item"));
        assertEquals(rC, lista.get(2).get("id"));
    }

    /**
     * Forma del {@code RubroRefResponse}: exactamente cuatro campos
     * {@code id, item, codigo, descripcion}. No debe aparecer el {@code BIGINT}
     * interno ni campos del {@code RubroResponse} existente (cantidad, precios,
     * unidad, apuId). El {@code id} debe ser UUIDv7.
     *
     * <p>Esto cubre simultáneamente la decisión de no introducir un campo
     * {@code alertas} per-rubro en el {@code RubroResponse} existente — la
     * validación vive en listas top-level, no en el read-model del rubro.</p>
     */
    @Test
    void TC_P32_07_forma_rubroref_solo_cuatro_campos_sin_bigint() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c7@ex.com");
        String proyectoId = crearProyecto(token, "Forma");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-FORMA", "Forma");
        String capId = crearCapituloRaiz(token, presupuestoId, "Forma");
        String rubroId =
                insertarRubro(presupuestoId, apuId, "1.1", "APU-FORMA", "Forma", "u", BigDecimal.ZERO, BigDecimal.ZERO);

        List<LinkedHashMap<String, Object>> lista = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("itemsPuCero");

        assertEquals(1, lista.size());
        Map<String, Object> elem = lista.get(0);
        Set<String> keys = elem.keySet();
        assertEquals(
                Set.of("id", "item", "codigo", "descripcion"),
                keys,
                "RubroRefResponse debe tener exactamente 4 campos");
        assertEquals(rubroId, elem.get("id"));
        assertNotNull(elem.get("id"));
        assertTrue(((String) elem.get("id")).matches(UUID_V7), "id debe ser UUIDv7");
        // No debe colarse el BIGINT interno ni campos del RubroResponse existente
        assertFalse(keys.contains("apuId"), "No debe exponer apuId");
        assertFalse(keys.contains("cantidad"), "No debe exponer cantidad");
        assertFalse(keys.contains("precioUnitario"), "No debe exponer precioUnitario");
        assertFalse(keys.contains("precioTotal"), "No debe exponer precioTotal");
        assertFalse(keys.contains("unidad"), "No debe exponer unidad");
        assertFalse(keys.contains("alertas"), "No debe exponer campo alertas por rubro");
    }

    /**
     * UUID malformado en el path (texto cualquiera no-UUID) → 400
     * {@code validacion} (RNF-05), sin tocar la BD.
     */
    @Test
    void TC_P32_08_path_uuid_malformado_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c8@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/no-es-uuid/validacion")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /**
     * UUIDv4 bien formado (no v7) → 400 {@code validacion} (RNF-05).
     */
    @Test
    void TC_P32_09_path_uuid_v4_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c9@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7 + "/validacion")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** UUIDv7 bien formado pero inexistente → 404 (RNF-05: nunca 403). */
    @Test
    void TC_P32_10_path_uuid_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c10@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/validacion")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /** Caller ajeno al presupuesto → 404 (RNF-05: owner-to-404, no 403). */
    @Test
    void TC_P32_11_caller_ajeno_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c11-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String intruso = AuthSupport.registrarConToken(mailbox, "p25-c11-intruso@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /**
     * Aislamiento cross-presupuesto: las listas del path se calculan sólo con
     * las tablas del MISMO presupuesto. La actividad de la versión vigente NO
     * debe "cubrir" los rubros de la versión no-vigente del mismo proyecto.
     *
     * <p>Diseño:
     * <ul>
     *   <li>Versión vigente V: 1 rubro V_r con cronograma + actividad que lo
     *       cubre.</li>
     *   <li>Versión no-vigente N (mismo proyecto): 1 rubro N_r sin cronograma
     *       (sin actividad).</li>
     * </ul>
     * Una consulta que NO filtre por {@code presupuesto_id} al evaluar
     * cobertura podría confundir la cobertura o devolver N_r como cubierto.
     * Esta consulta también verifica que N_r NO contiene a V_r ni al revés.
     * </p>
     */
    @Test
    void TC_P32_12_cross_presupuesto_actividad_otro_presupuesto_no_cubre() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c12@ex.com");
        String proyectoId = crearProyecto(token, "Cross presupuesto");
        String vigenteId = vigenteDeProyecto(proyectoId);
        String noVigenteId = insertarVersionNoVigente(proyectoId, (short) 2);

        // Versión vigente: 1 rubro cubierto por actividad
        String apuV = insertarApu(vigenteId, "APU-V", "Vigente");
        String capV = crearCapituloRaiz(token, vigenteId, "V");
        String rubroVigente = insertarRubro(
                vigenteId,
                apuV,
                "1.1",
                "APU-V",
                "Vigente",
                "u",
                new BigDecimal("2.000000"),
                new BigDecimal("8.000000"));
        long cronogramaV = insertarCronograma(vigenteId);
        insertarActividad(cronogramaV, internalRubroId(rubroVigente));

        // Versión no vigente: 1 rubro sin cronograma → itemsSinActividad
        String apuN = insertarApu(noVigenteId, "APU-N", "No vigente");
        String capN = crearCapituloRaiz(token, noVigenteId, "N");
        String rubroNoVigente = insertarRubro(
                noVigenteId,
                apuN,
                "1.1",
                "APU-N",
                "No vigente",
                "u",
                new BigDecimal("3.000000"),
                new BigDecimal("12.000000"));

        // Vigente: cubierto por su propia actividad → todas las listas vacías,
        // exportable true.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + vigenteId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true))
                .body("itemsSinActividad", hasSize(0))
                .body("itemsPuCero", hasSize(0))
                .body("itemsCantidadCero", hasSize(0));

        // No vigente: su rubro en itemsSinActividad; el rubro de la versión
        // vigente NUNCA debe filtrarse aquí.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + noVigenteId + "/validacion")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("itemsSinActividad", hasSize(1))
                .body("itemsSinActividad[0].id", equalTo(rubroNoVigente))
                .body("itemsSinActividad[0].codigo", equalTo("APU-N"))
                .body("itemsSinActividad[0].codigo", not(equalTo("APU-V")));
    }

    /**
     * Read-only: N invocaciones consecutivas de GET no mutan BD. El reset
     * aísla cronograma/actividad (sin JPA) y todas las dependencias actuales
     * de presupuesto/APU (ver {@link #reset()}).
     */
    @Test
    void TC_P32_13_get_validacion_no_escribe_en_bd() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p25-c13@ex.com");
        String proyectoId = crearProyecto(token, "Read only");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // 1 rubro PU=0 para que itemsPuCero no esté vacío
        String apuId = insertarApu(presupuestoId, "APU-RO", "Read only");
        String capId = crearCapituloRaiz(token, presupuestoId, "Read only");
        String rubroId = insertarRubro(
                presupuestoId, apuId, "1.1", "APU-RO", "Read only", "u", new BigDecimal("5.000000"), BigDecimal.ZERO);
        long rubroInterno = internalRubroId(rubroId);

        long rubrosAntes = contarRubros(presupuestoId);
        long apusAntes = contarApus(presupuestoId);
        long cronosAntes = contarCronogramas(presupuestoId);
        long actsAntes = contarActividades(presupuestoId);
        long rubroPuCeroAntes = contarActividadesPorRubro(rubroInterno);

        for (int i = 0; i < 5; i++) {
            given().header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/v1/presupuestos/" + presupuestoId + "/validacion")
                    .then()
                    .statusCode(200)
                    .body("exportable", equalTo(false))
                    .body("itemsPuCero", hasSize(1));
        }

        assertEquals(rubrosAntes, contarRubros(presupuestoId), "GET /validacion no crea rubros");
        assertEquals(apusAntes, contarApus(presupuestoId), "GET /validacion no crea APUs");
        assertEquals(cronosAntes, contarCronogramas(presupuestoId), "GET /validacion no crea cronogramas");
        assertEquals(actsAntes, contarActividades(presupuestoId), "GET /validacion no crea actividades");
        assertEquals(
                rubroPuCeroAntes,
                contarActividadesPorRubro(rubroInterno),
                "GET /validacion no crea actividad para un rubro PU=0");
    }
}
