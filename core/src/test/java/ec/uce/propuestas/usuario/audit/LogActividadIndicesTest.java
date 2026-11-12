package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogActividadIndicesTest {

    @Inject
    DataSource ds;

    @Test
    void conserva_indices_base_y_agrega_indice_publico_unico() throws Exception {
        Set<String> indices = new HashSet<>();
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select indexname from pg_indexes where schemaname = current_schema() and tablename = 'log_actividad'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                indices.add(rs.getString(1));
            }
        }
        assertTrue(indices.contains("ix_log_fecha"));
        assertTrue(indices.contains("ix_log_usuario"));
        assertTrue(indices.contains("ux_log_actividad_public_id"));
    }

    @Test
    void public_id_es_uuid_no_nulo_con_default_indice_unico_y_trigger_inmutable() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select data_type, is_nullable, column_default from information_schema.columns "
                                + "where table_schema = current_schema() and table_name = 'log_actividad' "
                                + "and column_name = 'public_id'");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("uuid", rs.getString("data_type"));
            assertEquals("NO", rs.getString("is_nullable"));
            assertTrue(rs.getString("column_default").contains("uuidv7"));
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select count(*) from pg_trigger where tgname = 'trg_log_actividad_public_id_immutable' "
                                + "and tgrelid = 'log_actividad'::regclass and not tgisinternal");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertEquals(1L, rs.getLong(1));
        }
    }

    @Test
    void entidad_public_id_no_tiene_fk_default_ni_unique() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("select column_default, is_nullable from information_schema.columns "
                                + "where table_schema = current_schema() and table_name = 'log_actividad' "
                                + "and column_name = 'entidad_public_id'");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(null, rs.getString("column_default"));
            assertEquals("YES", rs.getString("is_nullable"));
        }

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("select count(*) from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid "
                        + "join pg_attribute a on a.attrelid = t.oid and a.attnum = any(c.conkey) "
                        + "where t.relname = 'log_actividad' and a.attname = 'entidad_public_id' "
                        + "and c.contype in ('f', 'u')");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertEquals(0L, rs.getLong(1));
        }
    }
}
