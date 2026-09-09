package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 027 — focused invariants for the {@code actividad} JPA entity and
 * repository. Mirrors {@link CronogramaJpaIT} at the actividad level.
 */
@QuarkusTest
class ActividadJpaIT {

    private static final Pattern UUID_V7 =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    @Inject
    DataSource dataSource;

    @Inject
    EntityManager entityManager;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @BeforeEach
    void reset() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void actividad_entity_declares_uuid_public_id_with_generated_immutable_mapping() throws Exception {
        Field publicIdField = lookupField(Actividad.class, "publicId");
        assertNotNull(publicIdField, "Actividad must declare publicId field");
        assertEquals(UUID.class, publicIdField.getType(), "Actividad.publicId must be UUID");

        Generated generated = publicIdField.getAnnotation(Generated.class);
        assertNotNull(generated, "Actividad.publicId must carry @Generated");
        assertEquals(
                List.of(EventType.INSERT),
                List.of(generated.event()),
                "Actividad.publicId @Generated.event must be INSERT");

        jakarta.persistence.Column column = publicIdField.getAnnotation(jakarta.persistence.Column.class);
        assertNotNull(column, "Actividad.publicId must carry @Column");
        assertFalse(column.insertable(), "Actividad.publicId must be insertable=false");
        assertFalse(column.updatable(), "Actividad.publicId must be updatable=false");
        assertEquals("public_id", column.name(), "Actividad.publicId must map to public_id");

        // FK remains BIGINT Long
        Field cronogramaIdField = lookupField(Actividad.class, "cronogramaId");
        Field rubroIdField = lookupField(Actividad.class, "rubroId");
        assertNotNull(cronogramaIdField, "Actividad must declare cronogramaId");
        assertNotNull(rubroIdField, "Actividad must declare rubroId");
        assertEquals(Long.class, cronogramaIdField.getType(), "Actividad.cronogramaId must remain Long (BIGINT)");
        assertEquals(Long.class, rubroIdField.getType(), "Actividad.rubroId must remain Long (BIGINT)");
    }

    @Test
    @TestTransaction
    void actividad_publicId_is_generated_as_uuidv7_after_persist() {
        Seed seed = seedFullGraph();
        Actividad actividad = new Actividad();
        actividad.cronogramaId = seed.cronogramaId;
        actividad.rubroId = seed.rubroId;
        actividad.pesoPonderado = new BigDecimal("0.5000");
        actividad.avancePorPeriodo = "{\"1\":\"0.1250\",\"2\":\"0.1250\"}";
        actividadRepository.persist(actividad);

        assertNotNull(actividad.publicId, "Actividad.publicId must be populated after persist");
        assertTrue(
                UUID_V7.matcher(actividad.publicId.toString()).matches(),
                "Actividad.publicId must be a UUIDv7: " + actividad.publicId);
        assertEquals(7, actividad.publicId.version(), "Actividad UUIDv7 version nibble must be 7");
        assertEquals(2, actividad.publicId.variant(), "Actividad UUIDv7 must use RFC 4122 variant");
    }

    @Test
    @TestTransaction
    void actividad_orm_immutability_blocks_public_id_mutation() {
        Seed seed = seedFullGraph();
        Actividad actividad = new Actividad();
        actividad.cronogramaId = seed.cronogramaId;
        actividad.rubroId = seed.rubroId;
        actividad.pesoPonderado = BigDecimal.ZERO;
        actividad.avancePorPeriodo = "{}";
        actividadRepository.persist(actividad);

        UUID original = actividad.publicId;
        UUID pretend = UUID.fromString("0192f6c4-7c8a-7abc-8def-feedfacedead");

        entityManager.clear();
        Actividad reloaded = actividadRepository.findById(actividad.id);
        assertNotNull(reloaded);
        reloaded.publicId = pretend;
        actividadRepository.persist(reloaded);

        entityManager.clear();
        Actividad reread = actividadRepository.findById(actividad.id);
        assertEquals(
                original, reread.publicId, "Actividad.publicId must NOT be mutable through the ORM (updatable=false)");
    }

    @Test
    @TestTransaction
    void actividad_owner_scope_includes_the_nested_cronograma() {
        Seed seed = seedFullGraph();
        Usuario foreign = persistUsuario("foreign-act@ex.com");
        Actividad actividad = persistActividad(seed.cronogramaId, seed.rubroId);
        Cronograma cronograma = cronogramaRepository.findById(seed.cronogramaId);

        Optional<Actividad> ownerResolved = actividadRepository.findByPublicIdAndCronogramaAndOwnerScope(
                actividad.publicId, cronograma.publicId, seed.ownerId);
        assertTrue(ownerResolved.isPresent(), "owner must resolve their own actividad in the nested cronograma");
        assertEquals(actividad.id, ownerResolved.get().id);

        Optional<Actividad> foreignResolved = actividadRepository.findByPublicIdAndCronogramaAndOwnerScope(
                actividad.publicId, cronograma.publicId, foreign.id);
        assertTrue(foreignResolved.isEmpty(), "foreign owner must NOT see another user's actividad (404 semantics)");

        Cronograma otherCronograma = persistOtherCronograma(seed.ownerId);
        Optional<Actividad> crossCronograma = actividadRepository.findByPublicIdAndCronogramaAndOwnerScope(
                actividad.publicId, otherCronograma.publicId, seed.ownerId);
        assertTrue(
                crossCronograma.isEmpty(),
                "an activity in another cronograma owned by the same user must not resolve in the nested path");

        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000000");
        Optional<Actividad> inventedResolved = actividadRepository.findByPublicIdAndCronogramaAndOwnerScope(
                invented, cronograma.publicId, seed.ownerId);
        assertTrue(inventedResolved.isEmpty(), "An invented UUIDv7 must not resolve to any row (404 semantics)");
    }

    @Test
    @TestTransaction
    void actividad_repository_lists_by_cronograma_in_canonical_rubro_order() {
        Seed seed = seedFullGraph();
        Rubro laterRubro = rubroRepository.findById(seed.rubroId);
        laterRubro.item = "2.1";
        Rubro earlierRubro = persistAdditionalRubro(seed.presupuestoId, "1.9");

        Actividad later = persistActividad(seed.cronogramaId, laterRubro.id);
        Actividad earlier = persistActividad(seed.cronogramaId, earlierRubro.id);

        List<Actividad> rows = actividadRepository.listarPorCronograma(seed.cronogramaId);
        assertEquals(2, rows.size(), "internal lookup must return both seeded activities");
        assertEquals(earlier.id, rows.get(0).id, "activities must follow canonical rubro.item order");
        assertEquals(later.id, rows.get(1).id, "ordering must not depend on insertion order");
        assertEquals(
                rows.stream().map(row -> row.id).toList(),
                actividadRepository.listarPorCronograma(seed.cronogramaId).stream()
                        .map(row -> row.id)
                        .toList(),
                "repeated listings must preserve deterministic order");
    }

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private static Field lookupField(Class<?> entity, String name) {
        for (Class<?> current = entity; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }

    private Usuario persistUsuario(String email) {
        Usuario u = new Usuario();
        u.nombre = "Usuario " + email;
        u.email = email;
        u.passwordHash = "hash-placeholder";
        u.rol = Rol.USUARIO;
        u.emailVerificado = true;
        u.activo = true;
        u.createdAt = Instant.now();
        u.updatedAt = Instant.now();
        usuarioRepository.persist(u);
        return u;
    }

    private Actividad persistActividad(Long cronogramaId, Long rubroId) {
        Actividad actividad = new Actividad();
        actividad.cronogramaId = cronogramaId;
        actividad.rubroId = rubroId;
        actividad.pesoPonderado = new BigDecimal("0.5000");
        actividad.avancePorPeriodo = "{}";
        actividadRepository.persist(actividad);
        return actividad;
    }

    private Cronograma persistOtherCronograma(Long ownerId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Proyecto proyecto = new Proyecto();
        proyecto.usuarioId = ownerId;
        proyecto.nombreProyecto = "Otro cronograma";
        proyecto.codigo = "P-OTHER-" + suffix;
        proyecto.descripcion = "Nested scope test";
        proyecto.anio = (short) 2026;
        proyecto.plazoEjecucion = (short) 6;
        proyecto.plazoUnidad = ec.uce.propuestas.proyecto.entity.PlazoUnidad.MES;
        proyecto.estado = ec.uce.propuestas.proyecto.entity.EstadoProyecto.BORRADOR;
        proyecto.direccionInstitucional = "GAD Test";
        proyecto.createdAt = Instant.now();
        proyecto.updatedAt = Instant.now();
        proyectoRepository.persist(proyecto);

        Presupuesto presupuesto = new Presupuesto();
        presupuesto.proyectoId = proyecto.id;
        presupuesto.version = (short) 1;
        presupuesto.esVigente = true;
        presupuesto.total = BigDecimal.ZERO;
        presupuesto.createdAt = Instant.now();
        presupuesto.updatedAt = Instant.now();
        presupuestoRepository.persist(presupuesto);

        Cronograma cronograma = new Cronograma();
        cronograma.presupuestoId = presupuesto.id;
        cronograma.unidadTiempo = "SEMANA";
        cronograma.numeroPeriodos = 12;
        cronogramaRepository.persist(cronograma);
        return cronograma;
    }

    private Rubro persistAdditionalRubro(Long presupuestoId, String item) {
        Apu apu = new Apu();
        apu.presupuestoId = presupuestoId;
        apu.codigo = "APU-" + item;
        apu.descripcion = "APU adicional";
        apu.unidad = "u";
        apu.costoDirecto = BigDecimal.ZERO;
        apu.costoIndirecto = BigDecimal.ZERO;
        apu.costoTotal = BigDecimal.ZERO;
        apu.createdAt = Instant.now();
        apu.updatedAt = Instant.now();
        apuRepository.persist(apu);

        Capitulo capitulo = new Capitulo();
        capitulo.presupuestoId = presupuestoId;
        capitulo.item = "9";
        capitulo.descripcion = "Capitulo adicional";
        capitulo.orden = (short) 2;
        capitulo.total = BigDecimal.ZERO;
        capituloRepository.persist(capitulo);

        Rubro rubro = new Rubro();
        rubro.capituloId = capitulo.id;
        rubro.apuId = apu.id;
        rubro.item = item;
        rubro.codigo = apu.codigo;
        rubro.descripcion = "Rubro adicional";
        rubro.unidad = "u";
        rubro.cantidad = BigDecimal.ONE;
        rubro.precioUnitario = BigDecimal.ZERO;
        rubro.precioTotal = BigDecimal.ZERO;
        rubroRepository.persist(rubro);
        return rubro;
    }

    private Seed seedFullGraph() {
        Usuario owner = persistUsuario("owner-act@ex.com");

        Proyecto proyecto = new Proyecto();
        proyecto.usuarioId = owner.id;
        proyecto.nombreProyecto = "Actividad Owner";
        proyecto.codigo = "P-ACT";
        proyecto.descripcion = "Actividad test";
        proyecto.anio = (short) 2026;
        proyecto.plazoEjecucion = (short) 6;
        proyecto.plazoUnidad = ec.uce.propuestas.proyecto.entity.PlazoUnidad.MES;
        proyecto.estado = ec.uce.propuestas.proyecto.entity.EstadoProyecto.BORRADOR;
        proyecto.direccionInstitucional = "GAD Test";
        proyecto.createdAt = Instant.now();
        proyecto.updatedAt = Instant.now();
        proyectoRepository.persist(proyecto);

        Presupuesto presupuesto = new Presupuesto();
        presupuesto.proyectoId = proyecto.id;
        presupuesto.version = (short) 1;
        presupuesto.esVigente = true;
        presupuesto.total = BigDecimal.ZERO;
        presupuesto.createdAt = Instant.now();
        presupuesto.updatedAt = Instant.now();
        presupuestoRepository.persist(presupuesto);

        Apu apu = new Apu();
        apu.presupuestoId = presupuesto.id;
        apu.codigo = "APU-ACT";
        apu.descripcion = "APU actividad test";
        apu.unidad = "u";
        apu.porcentajeIndirecto = null;
        apu.costoDirecto = BigDecimal.ZERO;
        apu.costoIndirecto = BigDecimal.ZERO;
        apu.costoTotal = BigDecimal.ZERO;
        apu.createdAt = Instant.now();
        apu.updatedAt = Instant.now();
        apuRepository.persist(apu);

        Capitulo capitulo = new Capitulo();
        capitulo.presupuestoId = presupuesto.id;
        capitulo.parentId = null;
        capitulo.item = "1";
        capitulo.descripcion = "Capitulo actividad test";
        capitulo.orden = (short) 1;
        capitulo.total = BigDecimal.ZERO;
        capituloRepository.persist(capitulo);

        Rubro rubro = new Rubro();
        rubro.capituloId = capitulo.id;
        rubro.apuId = apu.id;
        rubro.item = "1.1";
        rubro.codigo = "APU-ACT";
        rubro.descripcion = "Rubro actividad test";
        rubro.unidad = "u";
        rubro.cantidad = new BigDecimal("1.000000");
        rubro.precioUnitario = BigDecimal.ZERO;
        rubro.precioTotal = BigDecimal.ZERO;
        rubroRepository.persist(rubro);

        Cronograma cronograma = new Cronograma();
        cronograma.presupuestoId = presupuesto.id;
        cronograma.unidadTiempo = "SEMANA";
        cronograma.numeroPeriodos = 12;
        cronograma.totalGeneralRevisado = null;
        cronograma.fechaRevision = null;
        cronograma.presupuestoFingerprintRevisado = null;
        cronogramaRepository.persist(cronograma);

        return new Seed(owner.id, presupuesto.id, cronograma.id, rubro.id);
    }

    private record Seed(Long ownerId, Long presupuestoId, Long cronogramaId, Long rubroId) {}
}
