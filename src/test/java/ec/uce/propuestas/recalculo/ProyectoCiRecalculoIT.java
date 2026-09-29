package ec.uce.propuestas.recalculo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real-PostgreSQL coverage for project CI recalculation and persisted money rollups. */
@QuarkusTest
class ProyectoCiRecalculoIT {

    @Inject
    DataSource ds;

    @Inject
    RecalculoService recalculoService;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void recalcula_apu_vinculado_y_persiste_rubro_capitulo_y_presupuesto() throws Exception {
        long projectId = createProjectAndFixture();
        recalculoService.recalcularCiProyecto(projectId);

        assertEquals("12.600000", scalar("SELECT costo_total::text FROM apu"));
        assertEquals("25.200000", scalar("SELECT precio_total::text FROM rubro"));
        assertEquals("25.200000", scalar("SELECT total::text FROM capitulo"));
        assertEquals("25.200000", scalar("SELECT total::text FROM presupuesto"));
    }

    private long createProjectAndFixture() throws Exception {
        try (Connection con = ds.getConnection()) {
            long userId = insertId(
                    con,
                    "INSERT INTO usuario (nombre, email, password_hash, email_verificado, activo) "
                            + "VALUES ('u','ci-recalc@e','x',TRUE,TRUE)");
            long projectId = insertId(
                    con,
                    "INSERT INTO proyecto (usuario_id, nombre_proyecto, anio, plazo_ejecucion, "
                            + "plazo_unidad, estado, direccion_institucional) VALUES (" + userId
                            + ", 'CI', 2026, 4, 'MES', 'BORRADOR', 'UCE')");
            long budgetId = insertId(
                    con,
                    "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (" + projectId + ", 1, TRUE)");
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO parametros_proyecto "
                    + "(proyecto_id, porcentaje_herramienta_menor, porcentaje_indirecto, iva, moneda, ci_individual_habilitado) "
                    + "VALUES (?, 0.05, 0.20, 0.15, 'USD', TRUE)")) {
                ps.setLong(1, projectId);
                ps.executeUpdate();
            }
            long baseId = insertId(
                    con,
                    "INSERT INTO base_insumos (nombre, tipo, proyecto_id, archivada) "
                            + "VALUES ('Base CI', 'PROYECTO', " + projectId + ", FALSE)");
            long insumoId = insertId(
                    con,
                    "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) " + "VALUES ("
                            + baseId + ", 'MO-CI', 'MANO_OBRA', 'MO CI', 'h', 10)");
            long apuId = insertId(
                    con,
                    "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad) " + "VALUES (" + budgetId
                            + ", 'CI-RECALC', 'CI recalculo', 'u')");
            long equipoId = insertId(
                    con,
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) " + "VALUES (" + apuId
                            + ", 'EQUIPO', 0, 1)");
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO apu_detalle "
                    + "(seccion_id, descripcion, orden, es_herramienta_menor, costo_hora, unidad, costo) "
                    + "VALUES (?, 'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
                ps.setLong(1, equipoId);
                ps.executeUpdate();
            }
            long moId = insertId(
                    con,
                    "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) " + "VALUES (" + apuId
                            + ", 'MANO_OBRA', 0, 2)");
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO apu_detalle "
                    + "(seccion_id, insumo_id, descripcion, orden, cantidad, es_herramienta_menor, rendimiento, unidad, costo) "
                    + "VALUES (?, ?, 'MO CI', 1, 1, FALSE, 1, 'h', 0)")) {
                ps.setLong(1, moId);
                ps.setLong(2, insumoId);
                ps.executeUpdate();
            }
            long chapterId = insertId(
                    con,
                    "INSERT INTO capitulo (presupuesto_id, item, descripcion, orden, total) " + "VALUES (" + budgetId
                            + ", '1', 'Capítulo', 1, 0)");
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO rubro "
                    + "(capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad, precio_unitario, precio_total) "
                    + "VALUES (?, ?, '1.1', 'CI-RECALC', 'CI recalculo', 'u', 2, 0, 0)")) {
                ps.setLong(1, chapterId);
                ps.setLong(2, apuId);
                ps.executeUpdate();
            }
            return projectId;
        }
    }

    private long insertId(Connection con, String sql) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql + " RETURNING id");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
