package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionalException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogActividadServiceTest {

    @Inject
    LogActividadService service;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad RESTART IDENTITY");
        }
    }

    @Test
    void mandatory_rechaza_emision_sin_transaccion_exterior() throws Exception {
        assertThrows(
                TransactionalException.class,
                () -> service.emitir(null, EventoLogActividad.AUTH_LOGIN, null, null, Map.of("resultado", "ok")));
        assertEquals(0L, contar());
    }

    @Test
    void persiste_actor_nullable_evento_enum_public_id_y_entidad_publica() throws Exception {
        UUID entidadId = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000201");
        QuarkusTransaction.requiringNew()
                .run(() -> service.emitir(null, EventoLogActividad.PROYECTO_CREADO, "proyecto", entidadId, Map.of()));

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select public_id, usuario_id, evento, entidad_public_id, entidad_id, created_at "
                                + "from log_actividad")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                UUID publicId = rs.getObject("public_id", UUID.class);
                assertEquals(7, publicId.version());
                assertEquals(2, publicId.variant());
                assertNull(rs.getObject("usuario_id"));
                assertEquals("proyecto.creado", rs.getString("evento"));
                assertEquals(entidadId, rs.getObject("entidad_public_id", UUID.class));
                assertNull(rs.getObject("entidad_id"));
                assertNotNull(rs.getObject("created_at"));
            }
        }
    }

    @Test
    void rollback_exterior_elimina_el_evento() throws Exception {
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> {
                    service.emitir(null, EventoLogActividad.AUTH_LOGOUT, null, null, Map.of("resultado", "ok"));
                    throw new IllegalStateException("rollback intencional");
                }));
        assertEquals(0L, contar());
    }

    @Test
    void validador_falla_antes_de_persistir() throws Exception {
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew()
                .run(() ->
                        service.emitir(null, EventoLogActividad.AUTH_LOGIN, null, null, Map.of("resultado", "fallo"))));
        assertEquals(0L, contar());
    }

    private long contar() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("select count(*) from log_actividad");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
