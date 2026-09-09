package ec.uce.propuestas.proyecto.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

public final class Plan037TestSupport {

    private Plan037TestSupport() {}

    public static void reset(DataSource ds, RecordingEnviadorCorreo mailbox) throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
            st.execute("DELETE FROM valor_referencia");
            st.execute("INSERT INTO valor_referencia (clave, valor, descripcion, fuente) VALUES "
                    + "('SBU','450.00','Salario Básico Unificado USD/mes','Ministerio del Trabajo 2023'),"
                    + "('APORTE_PATRONAL','12.15','Aporte patronal IESS %','IESS 2023'),"
                    + "('FAS','1.538','Factor de ajuste salarial (360/234)','v1.1 Anexo A'),"
                    + "('HORAS_OPERACION_ANUAL','1800','Referencia h/año equipos','v1.1 Anexo A')");
            st.execute("UPDATE parametros_sistema SET porcentaje_herramienta_menor=0.0500, "
                    + "porcentaje_indirecto=NULL, iva=0.1500, rango_hm_min=0.0000, rango_hm_max=0.2000, "
                    + "rango_ci_min=0.0000, rango_ci_max=1.0000, rango_descuento_min=0.0000, "
                    + "rango_descuento_max=0.5000, rango_iva_min=0.0000, rango_iva_max=0.3000, "
                    + "moneda='USD', updated_at=now() WHERE id=1");
        }
    }

    public static String registrarSuperAdmin(DataSource ds, RecordingEnviadorCorreo mailbox, String email)
            throws Exception {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol='SUPER_ADMIN' WHERE email=?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
        return login(email);
    }

    public static String registrarUsuario(RecordingEnviadorCorreo mailbox, String email) {
        return AuthSupport.registrarConToken(mailbox, email);
    }

    public static String login(String email) {
        return given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    public static String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 4,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    public static Map<String, Object> parametrosSistema(String hm, String ci, String iva) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("porcentajeHerramientaMenor", hm);
        if (ci != null) body.put("porcentajeIndirecto", ci);
        body.put("iva", iva);
        body.put("rangoHmMin", "0.0000");
        body.put("rangoHmMax", "0.2000");
        body.put("rangoCiMin", "0.0000");
        body.put("rangoCiMax", "1.0000");
        body.put("rangoDescuentoMin", "0.0000");
        body.put("rangoDescuentoMax", "0.5000");
        body.put("rangoIvaMin", "0.0000");
        body.put("rangoIvaMax", "0.3000");
        body.put("moneda", "USD");
        return body;
    }
}
