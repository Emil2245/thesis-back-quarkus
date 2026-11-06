package ec.uce.propuestas.documento.exportacion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(DocumentoSnapshotIT.SnapshotProfile.class)
class DocumentoSnapshotIT {
    /** Perfil independiente: los suites legacy truncan sus fixtures antes de nuestras mediciones. */
    public static final class SnapshotProfile implements io.quarkus.test.junit.QuarkusTestProfile {}

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    CapturaDocumentoService captura;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    jakarta.transaction.TransactionManager tm;

    record Fixture(long proyecto, long caller, long presupuesto, UUID id, long insumo, long apu, long seccion) {}

    static OpcionesDocumento opciones() {
        return new OpcionesDocumento("xlsx", Map.of());
    }

    static long insertar(Connection c, String sql, Object... values) throws SQLException {
        try (var p = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                return r.getLong(1);
            }
        }
    }

    static void ejecutar(Connection c, String sql, Object... values) throws SQLException {
        try (var p = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            p.executeUpdate();
        }
    }

    static Fixture fixture(DataSource ds, RecordingEnviadorCorreo mailbox) throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "snapshot-" + System.nanoTime() + "@test.ec");
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "Snapshot",
                        "codigo",
                        "S" + System.nanoTime(),
                        "anio",
                        2026,
                        "plazoEjecucion",
                        6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        try (var c = ds.getConnection()) {
            c.setAutoCommit(false);
            long proyecto;
            long caller;
            long presupuesto;
            UUID id;
            try (var p = c.prepareStatement(
                    "select pr.id,pr.usuario_id,p.id,p.public_id from proyecto pr join presupuesto p on p.proyecto_id=pr.id where pr.public_id=?")) {
                p.setObject(1, UUID.fromString(project));
                try (var r = p.executeQuery()) {
                    assertTrue(r.next());
                    proyecto = r.getLong(1);
                    caller = r.getLong(2);
                    presupuesto = r.getLong(3);
                    id = r.getObject(4, UUID.class);
                }
            }
            ejecutar(
                    c,
                    "update parametros_proyecto set mensaje_footer='anterior',porcentaje_indirecto=0.1000 where proyecto_id=?",
                    proyecto);
            ejecutar(
                    c,
                    "insert into firmante(proyecto_id,nombre,cargo,rol,orden) values (?,'anterior','Ingeniero','APROBADO',1)",
                    proyecto);
            long base = insertar(
                    c,
                    "insert into base_insumos(nombre,tipo,proyecto_id) values ('Snapshot','PROYECTO',?) returning id",
                    proyecto);
            long input = insertar(
                    c,
                    "insert into insumo(base_id,codigo,tipo,descripcion,unidad,precio_unitario) values (?,'I','MATERIAL','anterior','u',10) returning id",
                    base);
            long chapter = insertar(
                    c,
                    "insert into capitulo(presupuesto_id,item,descripcion,orden,total) values (?,'1','C',1,11) returning id",
                    presupuesto);
            long apu = insertar(
                    c,
                    "insert into apu(presupuesto_id,codigo,descripcion,unidad,costo_directo,costo_indirecto,costo_total) values (?,'A','APU','u',10,1,11) returning id",
                    presupuesto);
            long section = insertar(
                    c,
                    "insert into apu_seccion(apu_id,tipo,subtotal,orden) values (?,'MATERIAL',10,3) returning id",
                    apu);
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,unidad,costo) values (?,?,'anterior',1,1,'u',10)",
                    section,
                    input);
            ejecutar(
                    c,
                    "insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) values (?,?,'1.1','A','R','u',1,11,11)",
                    chapter,
                    apu);
            ejecutar(c, "update presupuesto set total=11 where id=?", presupuesto);
            c.commit();
            return new Fixture(proyecto, caller, presupuesto, id, input, apu, section);
        }
    }

    static void cambiar(DataSource ds, Fixture f) throws Exception {
        try (var c = ds.getConnection()) {
            c.setAutoCommit(false);
            ejecutar(
                    c,
                    "update parametros_proyecto set mensaje_footer='posterior',porcentaje_indirecto=0.2000 where proyecto_id=?",
                    f.proyecto());
            ejecutar(c, "update firmante set nombre='posterior' where proyecto_id=?", f.proyecto());
            ejecutar(c, "update insumo set descripcion='posterior',precio_unitario=20 where id=?", f.insumo());
            ejecutar(c, "update apu_detalle set descripcion='posterior',costo=20 where seccion_id=?", f.seccion());
            ejecutar(c, "update apu_seccion set subtotal=20 where id=?", f.seccion());
            ejecutar(c, "update apu set costo_directo=20,costo_indirecto=4,costo_total=24 where id=?", f.apu());
            ejecutar(c, "update rubro set precio_unitario=24,precio_total=24 where apu_id=?", f.apu());
            ejecutar(c, "update capitulo set total=24 where presupuesto_id=?", f.presupuesto());
            ejecutar(c, "update presupuesto set total=24 where id=?", f.presupuesto());
            c.commit();
        }
    }

    @Test
    void aislamientoSqlRealYReadOnlyEnConexionEnlistada() throws Exception {
        var f = fixture(ds, mailbox);
        captura.capturar(f.id(), f.caller(), "apus", opciones(), () -> {
            var session = em.unwrap(org.hibernate.Session.class);
            assertEquals(org.hibernate.FlushMode.MANUAL, session.getHibernateFlushMode());
            assertTrue(session.isDefaultReadOnly());
            assertEquals(
                    "repeatable read",
                    em.createNativeQuery("show transaction_isolation", String.class)
                            .getSingleResult());
            assertEquals(
                    "on",
                    em.createNativeQuery("show transaction_read_only", String.class)
                            .getSingleResult());
        });
        var error = assertThrows(
                RuntimeException.class,
                () -> captura.capturar(f.id(), f.caller(), "apus", opciones(), () -> {
                    em.unwrap(org.hibernate.Session.class).doWork(c -> {
                        try (var statement = c.prepareStatement("update presupuesto set total=999 where id=?")) {
                            statement.setLong(1, f.presupuesto());
                            statement.executeUpdate();
                        }
                    });
                }));
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        assertInstanceOf(SQLException.class, cause);
        assertEquals("25006", ((SQLException) cause).getSQLState(), "PostgreSQL rechazó escritura read-only");
        assertEquals(
                0,
                captura.capturar(f.id(), f.caller(), "apus", opciones()).total().compareTo(new BigDecimal("11")));
    }

    @Test
    void callerSuspendidoNoContaminaNiSeReutilizaSuContexto() throws Exception {
        var f = fixture(ds, mailbox);
        tm.begin();
        try {
            var budget = em.find(ec.uce.propuestas.presupuesto.entity.Presupuesto.class, f.presupuesto());
            var params = em.find(ec.uce.propuestas.proyecto.entity.ParametrosProyecto.class, f.proyecto());
            var callerPid = em.createNativeQuery("select pg_backend_pid()", Integer.class)
                    .getSingleResult();
            budget.total = new BigDecimal("999");
            params.mensajeFooter = "caller-no-flush";
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                            cambiar(ds, f);
                            return null;
                        })
                        .get(15, java.util.concurrent.TimeUnit.SECONDS);
                var snapshot = captura.capturar(f.id(), f.caller(), "apus", opciones(), () -> {
                    assertNotEquals(
                            callerPid,
                            em.createNativeQuery("select pg_backend_pid()", Integer.class)
                                    .getSingleResult());
                    assertNotSame(
                            budget, em.find(ec.uce.propuestas.presupuesto.entity.Presupuesto.class, f.presupuesto()));
                    assertEquals(
                            "on",
                            em.createNativeQuery("show transaction_read_only", String.class)
                                    .getSingleResult());
                });
                assertEquals("posterior", snapshot.parametros().mensajeFooter());
                assertEquals(0, snapshot.total().compareTo(new BigDecimal("24")));
                assertSame(budget, em.find(ec.uce.propuestas.presupuesto.entity.Presupuesto.class, f.presupuesto()));
                assertEquals("caller-no-flush", params.mensajeFooter);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        } finally {
            tm.rollback(); // Solo caller de fixture: descarta los cambios sucios no enviados.
        }
        assertEquals(
                "posterior",
                captura.capturar(f.id(), f.caller(), "apus", opciones())
                        .parametros()
                        .mensajeFooter());
    }

    @Test
    void parametrosAusentesNoSeMaterializanYVersionNoVigenteSeConserva() throws Exception {
        var f = fixture(ds, mailbox);
        long project;
        UUID publicId;
        try (var c = ds.getConnection()) {
            project = insertar(
                    c,
                    "insert into proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional) values (?,'Sin parámetros',2026,'UCE') returning id",
                    f.caller());
            insertar(
                    c,
                    "insert into presupuesto(proyecto_id,version,es_vigente) values (?,1,true) returning id",
                    project);
            try (var p = c.prepareStatement(
                    "insert into presupuesto(proyecto_id,version,es_vigente) values (?,2,false) returning public_id")) {
                p.setLong(1, project);
                try (var r = p.executeQuery()) {
                    assertTrue(r.next());
                    publicId = r.getObject(1, UUID.class);
                }
            }
        }
        var s = captura.capturar(publicId, f.caller(), "apus", opciones());
        assertEquals(2, s.version());
        assertFalse(s.vigente());
        assertEquals("USD", s.parametros().moneda());
        assertEquals(
                java.util.List.of("presupuesto-vacio", "apus-vacios"),
                s.preflight().bloqueos().stream()
                        .map(PreflightDocumento.Detalle::codigo)
                        .toList());
        // Segunda captura no vacía: ejercita calcular(Apu) sin obtenerOCrear.
        try (var c = ds.getConnection()) {
            long budget = insertar(c, "select id from presupuesto where public_id=?", publicId);
            long chapter = insertar(
                    c,
                    "insert into capitulo(presupuesto_id,item,descripcion,orden) values (?,'1','C',1) returning id",
                    budget);
            long apu = insertar(
                    c,
                    "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,'SIN-PARAM','A','u') returning id",
                    budget);
            long section =
                    insertar(c, "insert into apu_seccion(apu_id,tipo,orden) values (?,'MATERIAL',3) returning id", apu);
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,descripcion,orden,cantidad,precio_unitario_tarifa) values (?,'Override sin insumo',1,1,1.000001)",
                    section);
            ejecutar(
                    c,
                    "insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) values (?,?,'1.1','SIN-PARAM','R','u',1,1.000001,1.000001)",
                    chapter,
                    apu);
        }
        var conCalculo = captura.capturar(publicId, f.caller(), "apus", opciones());
        assertEquals(0, conCalculo.apus().getFirst().calculado().costoTotal().compareTo(new BigDecimal("1.000001")));
        try (var c = ds.getConnection();
                var p = c.prepareStatement("select count(*) from parametros_proyecto where proyecto_id=?")) {
            p.setLong(1, project);
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                assertEquals(0, r.getLong(1));
            }
        }
    }

    @Test
    void insumoAjenoFallaCerradoAntesDeCalcular() throws Exception {
        var selected = fixture(ds, mailbox);
        var foreign = fixture(ds, mailbox);
        try (var c = ds.getConnection()) {
            ejecutar(c, "update apu_detalle set insumo_id=? where seccion_id=?", foreign.insumo(), selected.seccion());
        }
        var error = assertThrows(
                ec.uce.propuestas.common.ProblemaException.class,
                () -> captura.capturar(selected.id(), selected.caller(), "apus", opciones()));
        assertEquals(409, error.getResponse().getStatus());
        var payload =
                (ec.uce.propuestas.common.ErrorPayload) error.getResponse().getEntity();
        assertEquals("export-inconsistente", payload.codigo());
        assertEquals("Insumo ilegible", payload.mensaje());
    }

    @Inject
    ec.uce.propuestas.apu.service.ApuCalculoService calculo;

    @Test
    void equivalenciaCanonicaConCiHmInsumosYOverridesSinPersistirDerivados() throws Exception {
        var f = fixture(ds, mailbox);
        try (var c = ds.getConnection()) {
            c.setAutoCommit(false);
            ejecutar(c, "update apu set porcentaje_indirecto=0.1700 where id=?", f.apu());
            ejecutar(
                    c,
                    "update parametros_proyecto set porcentaje_herramienta_menor=0.0500 where proyecto_id=?",
                    f.proyecto());
            long mo = insertar(
                    c, "insert into apu_seccion(apu_id,tipo,orden) values (?,'MANO_OBRA',2) returning id", f.apu());
            long equipo = insertar(
                    c, "insert into apu_seccion(apu_id,tipo,orden) values (?,'EQUIPO',1) returning id", f.apu());
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,descripcion,orden,cantidad,rendimiento,tarifa_jornal) values (?,'MO override',1,2,0.5,7)",
                    mo);
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,descripcion,orden,cantidad,rendimiento,tarifa_jornal) values (?,'Equipo override',1,1,0.5,4)",
                    equipo);
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,descripcion,orden,es_herramienta_menor) values (?,'HM',2,true)",
                    equipo);
            ejecutar(
                    c,
                    "insert into apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,precio_unitario_tarifa) values (?,?,'Material override',2,3,2)",
                    f.seccion(),
                    f.insumo());
            c.commit();
        }
        var s = captura.capturar(f.id(), f.caller(), "apus", opciones());
        var a = s.apus().getFirst();
        tm.begin();
        try {
            assertEquals(calculo.calcular(em.find(ec.uce.propuestas.apu.entity.Apu.class, f.apu())), a.calculado());
        } finally {
            tm.rollback();
        }
        // Stored material subtotal and APU total remain distinct from the pure calculation.
        assertEquals(0, a.costoTotal().compareTo(new BigDecimal("11")));
        assertNotEquals(0, a.calculado().costoTotal().compareTo(a.costoTotal()));
        assertEquals(0, a.porcentajeIndirecto().compareTo(new BigDecimal("0.17")));
        assertEquals(0, s.parametros().porcentajeHerramientaMenor().compareTo(new BigDecimal("0.05")));
        assertTrue(a.secciones().stream()
                .flatMap(section -> section.detalles().stream())
                .anyMatch(row -> row.herramientaMenor()));
        assertEquals(s, captura.capturar(f.id(), f.caller(), "apus", opciones()), "No derived writes");
    }

    @Test
    void consultasNoCrecenPorApuNiSeccion() throws Exception {
        var f = fixture(ds, mailbox);
        // Calienta el pipeline y las estadísticas antes de comparar contextos nuevos.
        captura.capturar(f.id(), f.caller(), "apus", opciones());
        var stats = em.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getPrepareStatementCount();
            captura.capturar(f.id(), f.caller(), "apus", opciones());
            long small = stats.getPrepareStatementCount() - before;
            try (var c = ds.getConnection()) {
                c.setAutoCommit(false);
                for (int n = 2; n <= 32; n++) {
                    long a = insertar(
                            c,
                            "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,?,'Cohort','u') returning id",
                            f.presupuesto(),
                            "COHORT-" + n);
                    for (String tipo : java.util.List.of("EQUIPO", "MANO_OBRA", "MATERIAL", "TRANSPORTE")) {
                        long section = insertar(
                                c, "insert into apu_seccion(apu_id,tipo,orden) values (?,?,1) returning id", a, tipo);
                        ejecutar(
                                c,
                                "insert into apu_detalle(seccion_id,descripcion,orden,cantidad,rendimiento,tarifa_jornal,precio_unitario_tarifa) values (?,'Override',1,1,1,2,2)",
                                section);
                    }
                    ejecutar(
                            c,
                            "insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) select capitulo_id,?,? ,?,'Cohort','u',1,1,1 from rubro where apu_id=?",
                            a,
                            "1." + n,
                            "COHORT-" + n,
                            f.apu());
                }
                c.commit();
            }
            before = stats.getPrepareStatementCount();
            var large = captura.capturar(f.id(), f.caller(), "apus", opciones());
            long big = stats.getPrepareStatementCount() - before;
            System.out.printf(
                    "EXP04 cohort: one=%d large=%d apus=%d%n",
                    small, big, large.apus().size());
            assertTrue(small > 0);
            assertEquals(32, large.apus().size());
            assertTrue(big <= small + 2, "No SQL por APU/sección: one=" + small + " large=" + big);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    @Test
    void mideCapturaSeedRealSinRenderNiLimitesArtificiales() throws Exception {
        UUID id;
        long caller;
        try (var c = ds.getConnection();
                var p = c.prepareStatement(
                        "select p.public_id,pr.usuario_id from presupuesto p join proyecto pr on pr.id=p.proyecto_id where pr.nombre_proyecto='Cetro Médico Tulcán' and p.version=1")) {
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                id = r.getObject(1, UUID.class);
                caller = r.getLong(2);
            }
        }
        var factory = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class);
        var statistics = factory.getStatistics();
        statistics.setStatisticsEnabled(true);
        long before = statistics.getPrepareStatementCount();
        long start = System.nanoTime();
        var s = captura.capturar(id, caller, "apus", opciones());
        long elapsed = System.nanoTime() - start;
        long queries = statistics.getPrepareStatementCount() - before;
        statistics.setStatisticsEnabled(false);
        assertTrue(queries > 0, "La instrumentación debe contar SQL real, no cero por estar deshabilitada");
        verificarValoresCongelados(s);
        verificarOrden(s.capitulos());
        // Comparación independiente con el camino ordinario sobre el mismo estado persistido.
        tm.begin();
        try {
            for (var a : s.apus()) {
                var entity = em.createQuery("from Apu where publicId = :id", ec.uce.propuestas.apu.entity.Apu.class)
                        .setParameter("id", a.id())
                        .getSingleResult();
                assertEquals(calculo.calcular(entity), a.calculado(), "Canonical equivalence " + a.codigo());
            }
        } finally {
            tm.rollback();
        }
        assertEquals(298, s.apus().size(), "El seed vigente contiene 298 identidades, no 288");
        assertEquals(298, contarRubros(s.capitulos()));
        System.out.printf(
                "EXP04 seed: apus=%d rubros=%d statements=%d latencyMs=%.3f Hibernate=%s%n",
                s.apus().size(),
                contarRubros(s.capitulos()),
                queries,
                elapsed / 1_000_000.0,
                org.hibernate.Version.getVersionString());
    }

    private static long contarRubros(java.util.List<SnapshotDocumento.Capitulo> cs) {
        return cs.stream()
                .mapToLong(c -> c.rubros().size() + contarRubros(c.hijos()))
                .sum();
    }

    private static void verificarValoresCongelados(Object value) throws Exception {
        if (value == null) return;
        assertFalse(value instanceof io.quarkus.hibernate.orm.panache.PanacheEntityBase);
        assertFalse(value instanceof Long, "Ninguna FK BIGINT en la captura");
        if (value instanceof java.util.List<?> values) {
            assertThrows(UnsupportedOperationException.class, values::clear);
            for (var element : values) verificarValoresCongelados(element);
        } else if (value instanceof java.util.Map<?, ?> values) {
            assertThrows(UnsupportedOperationException.class, values::clear);
            for (var element : values.values()) verificarValoresCongelados(element);
        } else if (value.getClass().isRecord()) {
            for (var component : value.getClass().getRecordComponents())
                verificarValoresCongelados(component.getAccessor().invoke(value));
        } else {
            assertTrue(value instanceof String
                    || value instanceof Number
                    || value instanceof Boolean
                    || value instanceof UUID
                    || value instanceof Enum<?>
                    || value instanceof java.time.LocalDate);
        }
    }

    private static void verificarOrden(java.util.List<SnapshotDocumento.Capitulo> cs) {
        var items = cs.stream().map(SnapshotDocumento.Capitulo::item).toList();
        assertEquals(
                items.stream()
                        .sorted(ec.uce.propuestas.common.ItemJerarquico.ORDEN)
                        .toList(),
                items);
        for (var c : cs) {
            var rubros = c.rubros().stream().map(SnapshotDocumento.Rubro::item).toList();
            assertEquals(
                    rubros.stream()
                            .sorted(ec.uce.propuestas.common.ItemJerarquico.ORDEN)
                            .toList(),
                    rubros);
            verificarOrden(c.hijos());
        }
    }

    @Test
    void completaSinLazyYDecimalExacto() throws Exception {
        var f = fixture(ds, mailbox);
        var s = captura.capturar(f.id(), f.caller(), "apus", opciones());
        verificarValoresCongelados(s);
        assertEquals(f.id(), s.presupuestoId());
        assertEquals(0, s.total().compareTo(new BigDecimal("11")));
        assertEquals("UCE", s.proyecto().direccionInstitucional());
        assertEquals("anterior", s.parametros().mensajeFooter());
        assertEquals("anterior", s.firmantes().getFirst().nombre());
        assertEquals(0, s.apus().getFirst().calculado().costoTotal().compareTo(new BigDecimal("11")));
        assertEquals(
                "anterior",
                s.apus()
                        .getFirst()
                        .secciones()
                        .getFirst()
                        .detalles()
                        .getFirst()
                        .insumo()
                        .descripcion());
        assertEquals(
                s.apus().getFirst().id(),
                s.capitulos().getFirst().rubros().getFirst().apuId());
        assertThrows(UnsupportedOperationException.class, () -> s.apus().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> s.capitulos().getFirst().rubros().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> s.apus().getFirst().secciones().getFirst().detalles().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> s.apus().getFirst().calculado().filas().clear());
        assertFalse(s.preflight().exportable()); // P-32 sin cronograma se conserva.
    }
}
