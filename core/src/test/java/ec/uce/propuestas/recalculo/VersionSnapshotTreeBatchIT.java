package ec.uce.propuestas.recalculo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.motor.ApuSnapshot;
import ec.uce.propuestas.motor.CapituloSnapshot;
import ec.uce.propuestas.motor.Motor;
import ec.uce.propuestas.motor.ParametrosCalculo;
import ec.uce.propuestas.motor.RubroSnapshot;
import ec.uce.propuestas.motor.VersionSnapshot;
import ec.uce.propuestas.recalculo.internal.VersionSnapshotBuilder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VersionSnapshotTreeBatchIT {
    @Inject
    DataSource ds;

    @Inject
    EntityManager em;

    @Inject
    VersionSnapshotBuilder builder;

    @Test
    void chapterAndRubroReadsStayBoundedAsTreeGrows() throws Exception {
        long small;
        long large;
        java.util.Map<String, Long> smallIds;
        java.util.Map<String, Long> largeIds;
        try (Connection connection = isolatedConnection()) {
            VersionSnapshotBatchFixtures fixtures = new VersionSnapshotBatchFixtures(connection);
            small = tree(fixtures, 3);
            large = tree(fixtures, 33);
            smallIds = fixtures.rubroIds(small);
            largeIds = fixtures.rubroIds(large);
        }
        long smallCount = measure(small, expected(3, smallIds));
        long largeCount = measure(large, expected(33, largeIds));
        System.out.printf("Snapshot prepared statements: 3 chapters=%d, 33 chapters=%d%n", smallCount, largeCount);
        assertEquals(smallCount, largeCount, "chapter/rubro reads must not grow with chapter count");
    }

    private Connection isolatedConnection() throws Exception {
        Connection connection = ds.getConnection();
        try {
            // The runner uses test Dev Services with no JDBC URL override. Never print credentials.
            URI address = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
            assertTrue(
                    address.getPort() > 0 && address.getPort() != 5432,
                    "fixtures require the dynamically mapped test-container port");
            assertTrue(
                    io.quarkus.runtime.configuration.ConfigUtils.getProfiles().contains("test"), "perfil test activo");
            assertNotEquals(
                    Boolean.FALSE,
                    ConfigProvider.getConfig()
                            .getOptionalValue("quarkus.datasource.devservices.enabled", Boolean.class)
                            .orElse(Boolean.TRUE),
                    "Dev Services no debe estar deshabilitado");
            assertEquals(null, System.getenv("DB_URL"), "no host database URL override");
            assertEquals(null, System.getenv("QUARKUS_DATASOURCE_JDBC_URL"), "no JDBC URL override");
            assertEquals(
                    ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class),
                    connection.getMetaData().getUserName());
            // Compare the actual connection with the runner-generated effective JDBC address.
            URI generatedAddress = URI.create(ConfigProvider.getConfig()
                    .getValue("quarkus.datasource.jdbc.url", String.class)
                    .substring("jdbc:".length()));
            assertEquals(generatedAddress.getHost(), address.getHost());
            assertEquals(generatedAddress.getPort(), address.getPort());
            assertEquals(generatedAddress.getPath(), address.getPath());
            System.out.printf("Verified test Dev Services PostgreSQL mapped port: %d%n", address.getPort());
            return connection;
        } catch (Throwable failure) {
            connection.close();
            throw failure;
        }
    }

    private long tree(VersionSnapshotBatchFixtures fixtures, int count) throws Exception {
        long presupuesto = fixtures.presupuesto();
        // Insert the second root first: chapter order is orden, not item or insertion order.
        fixtures.capitulo(presupuesto, null, "2", 2);
        long root = fixtures.capitulo(presupuesto, null, "1", 1);
        long child = fixtures.capitulo(presupuesto, root, "1.1", 1);
        for (int i = count; i > 3; i--) {
            fixtures.capitulo(presupuesto, child, "1.1." + i, i);
        }
        // A different budget and its cross-budget child are not reachable from this tree.
        long other = fixtures.presupuesto();
        long otherRoot = fixtures.capitulo(other, null, "9", 1);
        long orphan = fixtures.capitulo(presupuesto, otherRoot, "9.1", 1);
        fixtures.rubro(presupuesto, orphan, "9.1.1", "excluded");
        fixtures.rubro(presupuesto, root, "1.10", "R10");
        fixtures.rubro(presupuesto, root, "1.2", "R2");
        return presupuesto;
    }

    private VersionSnapshot expected(int count, java.util.Map<String, Long> rubroIds) {
        List<CapituloSnapshot> grandchildren = new ArrayList<>();
        for (int i = 4; i <= count; i++) {
            grandchildren.add(new CapituloSnapshot("1.1." + i, "1.1." + i, 3, List.of(), List.of()));
        }
        List<RubroSnapshot> rubros = List.of(
                new RubroSnapshot(
                        VersionSnapshotBuilder.claveRubro(rubroIds.get("R2")),
                        new BigDecimal("2.000000"),
                        new ApuSnapshot("R2", null, List.of())),
                new RubroSnapshot(
                        VersionSnapshotBuilder.claveRubro(rubroIds.get("R10")),
                        new BigDecimal("2.000000"),
                        new ApuSnapshot("R10", null, List.of())));
        return new VersionSnapshot(
                new ParametrosCalculo(new BigDecimal("0.0500"), new BigDecimal("0.1800")),
                List.of(
                        new CapituloSnapshot(
                                "1",
                                "1",
                                1,
                                List.of(new CapituloSnapshot("1.1", "1.1", 2, grandchildren, List.of())),
                                rubros),
                        new CapituloSnapshot("2", "2", 1, List.of(), List.of())),
                null);
    }

    private long measure(long presupuesto, VersionSnapshot expected) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.clear();
            Statistics statistics =
                    em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            boolean previouslyEnabled = statistics.isStatisticsEnabled();
            statistics.setStatisticsEnabled(true);
            try {
                statistics.clear();
                VersionSnapshot actual = builder.build(presupuesto);
                long statements = statistics.getPrepareStatementCount();
                assertEquals(expected, actual, "complete hierarchy, depth, natural rubro order and empty APUs");
                assertEquals(Motor.consolidar(expected), Motor.consolidar(actual), "exact domain result");
                return statements;
            } finally {
                statistics.setStatisticsEnabled(previouslyEnabled);
            }
        });
    }
}
