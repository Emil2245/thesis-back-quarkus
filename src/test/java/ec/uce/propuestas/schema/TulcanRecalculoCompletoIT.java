package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionCrearRequest;
import ec.uce.propuestas.presupuesto.service.VersionadoService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Caracterización: recálculo completo desde las filas semilla (V014/V016); difiere del libro por las
 * causas documentadas en thesis-docs {@code plan/domain/_artifacts/diagnostico-tulcan-2026-10.md}.
 *
 * <p>No es un golden master: fija lo que hoy produce crear una versión nueva del presupuesto Tulcán
 * sembrado (la versión 1 conserva los PU tipeados del libro, TOTAL 395 115.32). No toca GM-19/20/21.
 */
@QuarkusTest
class TulcanRecalculoCompletoIT {

    @Inject
    DataSource ds;

    @Inject
    VersionadoService versionado;

    @Test
    void version_nueva_recalcula_apus_y_totales_desde_las_filas_semilla() throws Exception {
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        long ownerId = leerLong("SELECT usuario_id FROM proyecto WHERE nombre_proyecto = 'Cetro Médico Tulcán'");
        UUID proyecto = UUID.fromString(
                leerString("SELECT public_id::text FROM proyecto WHERE nombre_proyecto = 'Cetro Médico Tulcán'"));
        UUID origen = UUID.fromString(
                leerString(
                        "SELECT p.public_id::text FROM presupuesto p "
                                + "JOIN proyecto y ON y.id = p.proyecto_id WHERE y.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1"));

        versionado.copiarVersion(
                proyecto, origen, new PresupuestoVersionCrearRequest(origen, "TC_P32 caracterizacion"), ownerId);

        String v2 = "(SELECT p.id FROM presupuesto p JOIN proyecto y ON y.id = p.proyecto_id "
                + "WHERE y.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 2)";

        // Cada APU copiado persiste su costo (plan 032, parte A).
        assertEquals(0L, leerLong("SELECT count(*) FROM apu WHERE presupuesto_id = " + v2 + " AND costo_total = 0"));

        // TOTAL de la versión recalculada (el libro/versión 1 da 395 115.32) y los 7 capítulos raíz.
        assertEquals("394019.171000", leerString("SELECT total::text FROM presupuesto WHERE id = " + v2));
        Map<String, String> caps = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT item, total::text FROM capitulo WHERE presupuesto_id = " + v2
                        + " AND parent_id IS NULL ORDER BY orden")) {
            while (rs.next()) caps.put(rs.getString(1), rs.getString(2));
        }
        assertEquals(
                Map.of(
                        "1", "157908.805400",
                        "2", "39843.084000",
                        "3", "18791.300000",
                        "4", "12598.021300",
                        "5", "155481.870000",
                        "6", "1776.730000",
                        "7", "7619.360300"),
                caps);

        // PU = trunc2(costo_total persistido) salvo 5 rubros documentados en el informe:
        //  - 1.6.1, 1.6.2, 1.10.8, 5.1.3.4: stubs provisionales V016 cuyo CT exacto cae justo bajo un centavo
        //    y se persiste redondeado a escala 6 (el PU usa la precisión completa del motor);
        //  - 1.3.3: RecalculoService.consolidarVersionYPropagar indexa por rubro.codigo, que se repite
        //    (501AL2 en 1.3.3 y 4.2.25); gana el último. Defecto fuera de motor/**, reportado aparte.
        List<String> excepciones = new ArrayList<>();
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT r.item FROM rubro r JOIN capitulo c ON c.id = r.capitulo_id "
                        + "JOIN apu a ON a.id = r.apu_id WHERE c.presupuesto_id = " + v2
                        + " AND r.precio_unitario <> trunc(a.costo_total, 2) ORDER BY r.item")) {
            while (rs.next()) excepciones.add(rs.getString(1));
        }
        assertEquals(List.of("1.10.8", "1.3.3", "1.6.1", "1.6.2", "5.1.3.4"), excepciones);
    }

    private long leerLong(String sql) throws Exception {
        return Long.parseLong(leerString(sql));
    }

    private String leerString(String sql) throws Exception {
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
