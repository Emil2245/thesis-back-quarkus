package ec.uce.propuestas.recalculo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.motor.*;
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
class VersionSnapshotApuBatchIT {
    @Inject
    DataSource ds;

    @Inject
    EntityManager em;

    @Inject
    VersionSnapshotBuilder builder;

    @Test
    void referencedApuReadsStayBoundedWithExactSnapshotsAndMotor() throws Exception {
        long small;
        long large;
        try (Connection connection = isolatedConnection()) {
            VersionSnapshotBatchFixtures fixtures = new VersionSnapshotBatchFixtures(connection);
            small = fixtures.apus(300);
            large = fixtures.apus(1000);
        }
        long smallCount = measure(small, expected(300), 300);
        long largeCount = measure(large, expected(1000), 1000);
        System.out.printf("Snapshot prepared statements: 300 APUs=%d, 1000 APUs=%d%n", smallCount, largeCount);
        assertEquals(smallCount, largeCount, "APU/section/detail/project reads must not grow with APU count");
    }

    private Connection isolatedConnection() throws Exception {
        Connection connection = ds.getConnection();
        try {
            URI address = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
            assertTrue(
                    address.getPort() > 0 && address.getPort() != 5432,
                    "fixtures require the dynamically mapped test-container port");
            assertEquals("test", System.getenv("QUARKUS_PROFILE"));
            assertEquals("true", System.getenv("QUARKUS_DATASOURCE_DEVSERVICES_ENABLED"));
            assertEquals(null, System.getenv("DB_URL"));
            assertEquals(null, System.getenv("QUARKUS_DATASOURCE_JDBC_URL"));
            assertEquals("quarkus_test", connection.getMetaData().getUserName());
            URI generated = URI.create(ConfigProvider.getConfig()
                    .getValue("quarkus.datasource.jdbc.url", String.class)
                    .substring("jdbc:".length()));
            assertEquals(generated.getHost(), address.getHost());
            assertEquals(generated.getPort(), address.getPort());
            assertEquals(generated.getPath(), address.getPath());
            System.out.printf("Verified test Dev Services PostgreSQL mapped port: %d%n", address.getPort());
            return connection;
        } catch (Throwable failure) {
            connection.close();
            throw failure;
        }
    }

    private VersionSnapshot expected(int count) {
        List<RubroSnapshot> rubros = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            List<FilaSnapshot> filas = new ArrayList<>();
            for (SeccionTipo tipo : SeccionTipo.values()) {
                filas.add(new FilaSnapshot(
                        tipo,
                        false,
                        decimal("2.000000"),
                        decimal("0.500000"),
                        decimal((11 + tipo.ordinal()) + ".000000"),
                        null));
                filas.add(new FilaSnapshot(
                        tipo,
                        false,
                        decimal("3.000000"),
                        decimal("0.500000"),
                        decimal((11 + tipo.ordinal()) + ".000000"),
                        decimal(
                                tipo == SeccionTipo.EQUIPO || tipo == SeccionTipo.MANO_OBRA
                                        ? "20.000000"
                                        : "30.000000")));
                filas.add(
                        new FilaSnapshot(tipo, false, decimal("4.000000"), decimal("0.500000"), null, BigDecimal.ZERO));
                if (tipo == SeccionTipo.EQUIPO) {
                    filas.add(
                            new FilaSnapshot(tipo, true, decimal(i == count ? "7.0000" : "5.0000"), null, null, null));
                }
            }
            rubros.add(new RubroSnapshot(
                    "A" + i,
                    decimal("2.000000"),
                    new ApuSnapshot("A" + i, i % 2 == 0 ? decimal("0.1200") : null, filas)));
        }
        return new VersionSnapshot(
                new ParametrosCalculo(decimal("0.0500"), decimal("0.1800")),
                List.of(new CapituloSnapshot("1", "1", 1, List.of(), rubros)),
                null);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private long measure(long presupuesto, VersionSnapshot expected, int count) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.clear();
            Statistics statistics =
                    em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            boolean enabled = statistics.isStatisticsEnabled();
            statistics.setStatisticsEnabled(true);
            try {
                statistics.clear();
                VersionSnapshot actual = builder.build(presupuesto);
                long statements = statistics.getPrepareStatementCount();
                assertEquals(
                        expected,
                        actual,
                        "complete ordered rows, inherited/override/pending prices and foreign-project HM");
                assertEquals(Motor.consolidar(expected), Motor.consolidar(actual), "exact nonzero domain result");
                assertEquals(
                        count,
                        statistics
                                .getEntityStatistics("ec.uce.propuestas.apu.entity.Apu")
                                .getLoadCount(),
                        "only linked APUs are hydrated");
                assertEquals(
                        count * 4L,
                        statistics
                                .getEntityStatistics("ec.uce.propuestas.apu.entity.ApuSeccion")
                                .getLoadCount());
                assertEquals(
                        count * 13L,
                        statistics
                                .getEntityStatistics("ec.uce.propuestas.apu.entity.ApuDetalle")
                                .getLoadCount());
                assertEquals(
                        4,
                        statistics
                                .getEntityStatistics("ec.uce.propuestas.insumo.entity.Insumo")
                                .getLoadCount());
                return statements;
            } finally {
                statistics.setStatisticsEnabled(enabled);
            }
        });
    }
}
