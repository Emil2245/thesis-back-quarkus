package ec.uce.propuestas.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import ec.uce.propuestas.plantilla.repository.PlantillaApuRepository;
import ec.uce.propuestas.plantilla.repository.PlantillaProyectoRepository;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.Firmante;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.FirmanteRepository;
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
import java.sql.Connection;
import java.sql.Statement;
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
 * Hybrid identifier persistence foundation (OpenSpec WU-03).
 *
 * <p>Every API-addressable entity must declare an immutable, non-null, unique UUIDv7
 * {@code publicId} that Hibernate reads back from the database after insert
 * ({@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 * updatable=false}). Every API-addressable repository must resolve
 * {@code publicId} + owner scope to an internal {@code BIGINT} row and return
 * {@code Optional.empty()} for a foreign owner. Internal-only entities must NOT
 * declare a {@code publicId} field, and every relational foreign-key column must
 * remain {@code Long} (never {@code UUID}).
 */
@QuarkusTest
class PublicIdPersistenceTest {

    private static final List<Class<?>> PUBLIC_ID_ENTITIES = List.of(
            Usuario.class,
            Firmante.class,
            Proyecto.class,
            Presupuesto.class,
            Apu.class,
            ApuDetalle.class,
            BaseInsumos.class,
            Insumo.class,
            PlantillaApu.class,
            PlantillaProyecto.class,
            Capitulo.class,
            Rubro.class);

    private static final List<Class<?>> INTERNAL_ENTITIES =
            List.of(ParametrosSistema.class, ParametrosProyecto.class, ApuSeccion.class);

    /** Canonical UUIDv7: version nibble 7, RFC 4122 variant 8/9/a/b. */
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
    FirmanteRepository firmanteRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuDetalleRepository apuDetalleRepository;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    PlantillaApuRepository plantillaApuRepository;

    @Inject
    PlantillaProyectoRepository plantillaProyectoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, plantilla_apu, plantilla_proyecto, "
                    + "parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // Structural contracts
    // =========================================================================

    @Test
    void publicId_is_declared_on_every_api_addressable_entity_with_generated_immutable_mapping() {
        for (Class<?> entity : PUBLIC_ID_ENTITIES) {
            Field field = publicIdField(entity);
            assertNotNull(field, entity.getSimpleName() + " must declare publicId");
            Generated generated = field.getAnnotation(Generated.class);
            assertNotNull(generated, entity.getSimpleName() + ".publicId must carry @Generated");
            assertEquals(
                    List.of(EventType.INSERT),
                    List.of(generated.event()),
                    entity.getSimpleName() + ".publicId @Generated.event must be INSERT");
            jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
            assertNotNull(column, entity.getSimpleName() + ".publicId must carry @Column");
            assertFalse(column.insertable(), entity.getSimpleName() + ".publicId must be insertable=false");
            assertFalse(column.updatable(), entity.getSimpleName() + ".publicId must be updatable=false");
            assertEquals("public_id", column.name(), entity.getSimpleName() + ".publicId must map to public_id");
            assertEquals(UUID.class, field.getType(), entity.getSimpleName() + ".publicId type must be UUID");
        }
    }

    @Test
    void every_public_id_repository_exposes_findByPublicIdAndOwnerScope() {
        assertRepositoryHasOwnerScopeResolver(usuarioRepository, "Usuario");
        assertRepositoryHasOwnerScopeResolver(proyectoRepository, "Proyecto");
        assertRepositoryHasOwnerScopeResolver(firmanteRepository, "Firmante");
        assertRepositoryHasOwnerScopeResolver(presupuestoRepository, "Presupuesto");
        assertRepositoryHasOwnerScopeResolver(apuRepository, "Apu");
        assertRepositoryHasOwnerScopeResolver(apuDetalleRepository, "ApuDetalle");
        assertRepositoryHasOwnerScopeResolver(baseInsumosRepository, "BaseInsumos");
        assertRepositoryHasOwnerScopeResolver(insumoRepository, "Insumo");
        assertRepositoryHasOwnerScopeResolver(plantillaApuRepository, "PlantillaApu");
        assertRepositoryHasOwnerScopeResolver(plantillaProyectoRepository, "PlantillaProyecto");
        assertRepositoryHasOwnerScopeResolver(capituloRepository, "Capitulo");
        assertRepositoryHasOwnerScopeResolver(rubroRepository, "Rubro");
    }

    @Test
    void internal_only_entities_do_not_declare_publicId() {
        for (Class<?> entity : INTERNAL_ENTITIES) {
            assertNull(
                    lookupField(entity, "publicId"),
                    entity.getSimpleName() + " is internal-only and must NOT declare publicId");
        }
    }

    @Test
    void foreign_key_columns_remain_bigint_not_uuid() {
        assertFkLong(Apu.class, "presupuestoId");
        assertFkLong(ApuDetalle.class, "seccionId");
        assertFkLong(ApuDetalle.class, "insumoId");
        assertFkLong(BaseInsumos.class, "proyectoId");
        assertFkLong(BaseInsumos.class, "usuarioId");
        assertFkLong(Capitulo.class, "presupuestoId");
        assertFkLong(Capitulo.class, "parentId");
        assertFkLong(Firmante.class, "proyectoId");
        assertFkLong(Insumo.class, "baseId");
        assertFkLong(PlantillaApu.class, "usuarioId");
        assertFkLong(PlantillaProyecto.class, "usuarioId");
        assertFkLong(Presupuesto.class, "proyectoId");
        assertFkLong(Presupuesto.class, "origenId");
        assertFkLong(Proyecto.class, "usuarioId");
        assertFkLong(Rubro.class, "capituloId");
        assertFkLong(Rubro.class, "apuId");
        assertFkLong(Usuario.class, "id"); // primary key must also be BigInt
    }

    // =========================================================================
    // Behavioral contracts — generated UUIDv7 + owner scope
    // =========================================================================

    @Test
    @TestTransaction
    void publicId_is_generated_as_uuidv7_after_persist_and_is_unique() {
        Usuario u1 = persistUsuario("alpha@ex.com");
        Usuario u2 = persistUsuario("beta@ex.com");

        assertNotNull(u1.publicId, "Usuario.publicId must be populated after persist");
        assertNotNull(u2.publicId, "Usuario.publicId must be populated after persist");
        assertTrue(
                UUID_V7.matcher(u1.publicId.toString()).matches(),
                "Usuario.publicId must be a UUIDv7 (version nibble 7, RFC 4122 variant): " + u1.publicId);
        assertTrue(
                UUID_V7.matcher(u2.publicId.toString()).matches(), "Usuario.publicId must be a UUIDv7: " + u2.publicId);
        assertEquals(7, u1.publicId.version(), "UUIDv7 version nibble must be 7");
        assertEquals(2, u1.publicId.variant(), "UUIDv7 must use RFC 4122 variant");
        assertFalse(u1.publicId.equals(u2.publicId), "Two persisted rows must receive distinct publicIds");
    }

    @Test
    @TestTransaction
    void owner_scope_returns_the_row_for_the_owner() {
        Usuario owner = persistUsuario("owner@ex.com");
        Proyecto proyecto = persistProyecto(owner.id, "Proyecto Owner");

        Optional<Proyecto> resolved = proyectoRepository.findByPublicIdAndOwnerScope(proyecto.publicId, owner.id);
        assertTrue(resolved.isPresent(), "owner must resolve their own project by publicId");
        assertEquals(proyecto.id, resolved.get().id);
    }

    @Test
    @TestTransaction
    void owner_scope_returns_empty_for_foreign_user() {
        Usuario owner = persistUsuario("owner@ex.com");
        Usuario foreign = persistUsuario("foreign@ex.com");
        Proyecto proyecto = persistProyecto(owner.id, "Proyecto Privado");

        Optional<Proyecto> foreignResolution =
                proyectoRepository.findByPublicIdAndOwnerScope(proyecto.publicId, foreign.id);
        assertTrue(
                foreignResolution.isEmpty(), "foreign owner must NOT see another user's project (returns empty / 404)");
    }

    @Test
    @TestTransaction
    void owner_scope_returns_empty_for_nonexistent_publicId() {
        Usuario owner = persistUsuario("only@ex.com");
        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000000");

        Optional<Proyecto> resolved = proyectoRepository.findByPublicIdAndOwnerScope(invented, owner.id);
        assertTrue(
                resolved.isEmpty(),
                "An invented UUIDv7 must not resolve to any row even for the legitimate owner (404 semantics)");
    }

    @Test
    @TestTransaction
    void orm_immutability_blocks_in_process_public_id_mutation() {
        // TC-ID-02 — el ORM-layer (@Column updatable=false) bloquea la mutación de publicId
        // aún cuando el código muta el campo en memoria y vuelve a sincronizar. El trigger de
        // base de datos es una defensa de cinturón-y-tirantes para SQL crudo (verificado por
        // SchemaBaselineIT). Aquí demostramos la capa ORM con un detach + merge de un valor
        // diferente: el UPDATE de Hibernate omite la columna y el valor persistido no cambia.
        Usuario u = persistUsuario("orm-imm@ex.com");
        UUID original = u.publicId;
        UUID pretend = UUID.fromString("0192f6c4-7c8a-7abc-8def-feedfacedead");

        entityManager.clear();
        Usuario reloaded = usuarioRepository.findById(u.id);
        assertNotNull(reloaded);
        reloaded.publicId = pretend;
        reloaded.persistAndFlush();
        entityManager.clear();

        Usuario reread = usuarioRepository.findById(u.id);
        assertEquals(
                original,
                reread.publicId,
                "Hibernate must NOT persist a public_id mutation (updatable=false at ORM layer)");
    }

    @Test
    @TestTransaction
    void owner_scope_is_reusable_across_all_public_id_repositories() {
        Usuario owner = persistUsuario("owner@ex.com");
        Usuario foreign = persistUsuario("foreign@ex.com");
        Proyecto proyecto = persistProyecto(owner.id, "A");
        Firmante firmante = persistFirmante(proyecto.id, "Firmante Owner");
        Presupuesto presupuesto = persistPresupuesto(proyecto.id, (short) 1);
        Apu apu = persistApu(presupuesto.id, "APU-1");
        ApuSeccion seccion = persistApuSeccion(apu.id);
        ApuDetalle detalle = persistApuDetalle(seccion.id);
        BaseInsumos base = persistBasePersonal(owner.id, "Personal Base");
        Insumo insumo = persistInsumo(base.id, "PER-EQ-001", "EQUIPO");
        PlantillaApu plantilla = persistPlantillaApu(owner.id);
        PlantillaProyecto plantillaProyecto = persistPlantillaProyecto(owner.id);
        Capitulo capitulo = persistCapitulo(presupuesto.id, "1");
        Rubro rubro = persistRubro(capitulo.id, apu.id, "1.1", "APU-1");

        // Every repository must resolve its own row for the legitimate owner.
        assertOwnerScopePresent(firmanteRepository, firmante.publicId, owner.id, "Firmante");
        assertOwnerScopePresent(presupuestoRepository, presupuesto.publicId, owner.id, "Presupuesto");
        assertOwnerScopePresent(apuRepository, apu.publicId, owner.id, "Apu");
        assertOwnerScopePresent(apuDetalleRepository, detalle.publicId, owner.id, "ApuDetalle");
        assertOwnerScopePresent(baseInsumosRepository, base.publicId, owner.id, "BaseInsumos");
        assertOwnerScopePresent(insumoRepository, insumo.publicId, owner.id, "Insumo");
        assertOwnerScopePresent(plantillaApuRepository, plantilla.publicId, owner.id, "PlantillaApu");
        assertOwnerScopePresent(plantillaProyectoRepository, plantillaProyecto.publicId, owner.id, "PlantillaProyecto");
        assertOwnerScopePresent(capituloRepository, capitulo.publicId, owner.id, "Capitulo");
        assertOwnerScopePresent(rubroRepository, rubro.publicId, owner.id, "Rubro");

        // Every repository must hide its row from a foreign caller.
        assertOwnerScopeEmpty(firmanteRepository, firmante.publicId, foreign.id, "Firmante");
        assertOwnerScopeEmpty(presupuestoRepository, presupuesto.publicId, foreign.id, "Presupuesto");
        assertOwnerScopeEmpty(apuRepository, apu.publicId, foreign.id, "Apu");
        assertOwnerScopeEmpty(apuDetalleRepository, detalle.publicId, foreign.id, "ApuDetalle");
        assertOwnerScopeEmpty(baseInsumosRepository, base.publicId, foreign.id, "BaseInsumos");
        assertOwnerScopeEmpty(insumoRepository, insumo.publicId, foreign.id, "Insumo");
        assertOwnerScopeEmpty(plantillaApuRepository, plantilla.publicId, foreign.id, "PlantillaApu");
        assertOwnerScopeEmpty(plantillaProyectoRepository, plantillaProyecto.publicId, foreign.id, "PlantillaProyecto");
        assertOwnerScopeEmpty(capituloRepository, capitulo.publicId, foreign.id, "Capitulo");
        assertOwnerScopeEmpty(rubroRepository, rubro.publicId, foreign.id, "Rubro");
    }

    @Test
    @TestTransaction
    void capitulo_and_rubro_are_generated_as_uuidv7_after_persist() {
        // Plan 019 — Capitulo y Rubro también deben recibir publicId UUIDv7 desde la
        // columna DEFAULT uuidv7() añadida por V008, exactamente como Presupuesto y Apu.
        Usuario owner = persistUsuario("cap-rubro-owner@ex.com");
        Proyecto proyecto = persistProyecto(owner.id, "Capitulo Rubro");
        Presupuesto presupuesto = persistPresupuesto(proyecto.id, (short) 1);
        Apu apu = persistApu(presupuesto.id, "APU-CAP-1");
        Capitulo capitulo = persistCapitulo(presupuesto.id, "1");
        Rubro rubro = persistRubro(capitulo.id, apu.id, "1.1", "APU-CAP-1");

        assertNotNull(capitulo.publicId, "Capitulo.publicId must be populated after persist");
        assertNotNull(rubro.publicId, "Rubro.publicId must be populated after persist");
        assertTrue(
                UUID_V7.matcher(capitulo.publicId.toString()).matches(),
                "Capitulo.publicId must be a UUIDv7: " + capitulo.publicId);
        assertTrue(
                UUID_V7.matcher(rubro.publicId.toString()).matches(),
                "Rubro.publicId must be a UUIDv7: " + rubro.publicId);
        assertEquals(7, capitulo.publicId.version(), "Capitulo UUIDv7 version nibble must be 7");
        assertEquals(7, rubro.publicId.version(), "Rubro UUIDv7 version nibble must be 7");
        assertEquals(2, capitulo.publicId.variant(), "Capitulo UUIDv7 must use RFC 4122 variant");
        assertEquals(2, rubro.publicId.variant(), "Rubro UUIDv7 must use RFC 4122 variant");
        assertFalse(capitulo.publicId.equals(rubro.publicId), "Capitulo and Rubro must receive distinct publicIds");
    }

    @Test
    @TestTransaction
    void owner_scope_returns_empty_for_nonexistent_capitulo_and_rubro_publicIds() {
        // Plan 019 — un UUIDv7 inexistente devuelve Optional.empty() para ambas
        // entidades públicas (404 semantics, RNF-05).
        Usuario owner = persistUsuario("only-cap-rubro@ex.com");
        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000000");

        Optional<Capitulo> capitulo = capituloRepository.findByPublicIdAndOwnerScope(invented, owner.id);
        Optional<Rubro> rubro = rubroRepository.findByPublicIdAndOwnerScope(invented, owner.id);
        assertTrue(capitulo.isEmpty(), "An invented UUIDv7 must not resolve any Capitulo row");
        assertTrue(rubro.isEmpty(), "An invented UUIDv7 must not resolve any Rubro row");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void assertOwnerScopePresent(Object repository, UUID publicId, Long callerId, String label) {
        Optional<Object> resolved = invokeFindByPublicIdAndOwnerScope(repository, publicId, callerId);
        assertTrue(resolved.isPresent(), label + " must resolve for the legitimate owner");
    }

    private void assertOwnerScopeEmpty(Object repository, UUID publicId, Long callerId, String label) {
        Optional<Object> resolved = invokeFindByPublicIdAndOwnerScope(repository, publicId, callerId);
        assertTrue(resolved.isEmpty(), label + " must hide from a foreign owner");
    }

    private static Optional<Object> invokeFindByPublicIdAndOwnerScope(Object repository, UUID publicId, Long callerId) {
        try {
            Object result = repository
                    .getClass()
                    .getMethod("findByPublicIdAndOwnerScope", UUID.class, Long.class)
                    .invoke(repository, publicId, callerId);
            if (result instanceof Optional<?> optional) {
                return optional.map(o -> o);
            }
            throw new AssertionError("findByPublicIdAndOwnerScope must return Optional on "
                    + repository.getClass().getSimpleName());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "findByPublicIdAndOwnerScope missing on "
                            + repository.getClass().getSimpleName(),
                    e);
        }
    }

    private static void assertRepositoryHasOwnerScopeResolver(Object repository, String label) {
        try {
            repository.getClass().getMethod("findByPublicIdAndOwnerScope", UUID.class, Long.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(label + " repository must expose findByPublicIdAndOwnerScope(UUID, Long)", e);
        }
    }

    private static void assertFkLong(Class<?> entity, String fieldName) {
        Field field = lookupField(entity, fieldName);
        assertNotNull(field, entity.getSimpleName() + "." + fieldName + " must exist");
        assertEquals(
                Long.class,
                field.getType(),
                entity.getSimpleName() + "." + fieldName + " must be Long (BIGINT), not "
                        + field.getType().getName());
    }

    private static Field publicIdField(Class<?> entity) {
        Field field = lookupField(entity, "publicId");
        if (field == null) {
            return null;
        }
        assertEquals(UUID.class, field.getType(), entity.getSimpleName() + ".publicId must be UUID");
        return field;
    }

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

    // =========================================================================
    // Seed fixtures (in-memory; no flyway touching here)
    // =========================================================================

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

    private Proyecto persistProyecto(Long ownerId, String nombre) {
        Proyecto p = new Proyecto();
        p.usuarioId = ownerId;
        p.nombreProyecto = nombre;
        p.codigo = "P-" + nombre.hashCode();
        p.descripcion = "Proyecto de prueba";
        p.anio = (short) 2026;
        p.plazoEjecucion = (short) 6;
        p.plazoUnidad = ec.uce.propuestas.proyecto.entity.PlazoUnidad.MES;
        p.estado = ec.uce.propuestas.proyecto.entity.EstadoProyecto.BORRADOR;
        p.direccionInstitucional = "GAD Test";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        proyectoRepository.persist(p);
        return p;
    }

    private Firmante persistFirmante(Long proyectoId, String nombre) {
        Firmante firmante = new Firmante();
        firmante.proyectoId = proyectoId;
        firmante.nombre = nombre;
        firmante.cargo = "Director";
        firmante.rol = ec.uce.propuestas.proyecto.entity.RolFirmante.CONSOLIDADO;
        firmante.orden = (short) 1;
        firmanteRepository.persist(firmante);
        return firmante;
    }

    private Presupuesto persistPresupuesto(Long proyectoId, short version) {
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.proyectoId = proyectoId;
        presupuesto.version = version;
        presupuesto.esVigente = true;
        presupuesto.total = BigDecimal.ZERO;
        presupuesto.createdAt = Instant.now();
        presupuesto.updatedAt = Instant.now();
        presupuestoRepository.persist(presupuesto);
        return presupuesto;
    }

    private Apu persistApu(Long presupuestoId, String codigo) {
        Apu apu = new Apu();
        apu.presupuestoId = presupuestoId;
        apu.codigo = codigo;
        apu.descripcion = "APU de prueba";
        apu.unidad = "u";
        apu.porcentajeDescuento = BigDecimal.ZERO;
        apu.costoDirecto = BigDecimal.ZERO;
        apu.costoIndirecto = BigDecimal.ZERO;
        apu.costoTotal = BigDecimal.ZERO;
        apu.createdAt = Instant.now();
        apu.updatedAt = Instant.now();
        apuRepository.persist(apu);
        return apu;
    }

    private ApuSeccion persistApuSeccion(Long apuId) {
        ApuSeccion seccion = new ApuSeccion();
        seccion.apuId = apuId;
        seccion.tipo = ec.uce.propuestas.motor.SeccionTipo.MATERIAL;
        seccion.subtotal = BigDecimal.ZERO;
        seccion.orden = (short) 0;
        seccion.persist();
        return seccion;
    }

    private ApuDetalle persistApuDetalle(Long seccionId) {
        ApuDetalle detalle = new ApuDetalle();
        detalle.seccionId = seccionId;
        detalle.descripcion = "Detalle de prueba";
        detalle.orden = (short) 1;
        detalle.esHerramientaMenor = false;
        detalle.cantidad = new BigDecimal("1.000000");
        detalle.costoHora = BigDecimal.ZERO;
        detalle.costo = BigDecimal.ZERO;
        apuDetalleRepository.persist(detalle);
        return detalle;
    }

    private BaseInsumos persistBasePersonal(Long ownerId, String nombre) {
        BaseInsumos base = new BaseInsumos();
        base.nombre = nombre;
        base.tipo = TipoBase.PERSONAL;
        base.usuarioId = ownerId;
        base.archivada = false;
        base.createdAt = Instant.now();
        base.updatedAt = Instant.now();
        baseInsumosRepository.persist(base);
        return base;
    }

    private Insumo persistInsumo(Long baseId, String codigo, String tipo) {
        Insumo insumo = new Insumo();
        insumo.baseId = baseId;
        insumo.codigo = codigo;
        insumo.tipo = ec.uce.propuestas.insumo.entity.TipoInsumo.valueOf(tipo);
        insumo.descripcion = "Insumo de prueba";
        insumo.unidad = "h";
        insumo.precioUnitario = new BigDecimal("1.000000");
        insumo.createdAt = Instant.now();
        insumo.updatedAt = Instant.now();
        insumoRepository.persist(insumo);
        return insumo;
    }

    private PlantillaApu persistPlantillaApu(Long ownerId) {
        PlantillaApu plantilla = new PlantillaApu();
        plantilla.nombre = "Plantilla APU personal";
        plantilla.tipo = ec.uce.propuestas.plantilla.entity.PlantillaApu.Tipo.PERSONAL;
        plantilla.usuarioId = ownerId;
        plantilla.snapshotSecciones = "{}";
        plantilla.createdAt = Instant.now();
        plantilla.updatedAt = Instant.now();
        plantillaApuRepository.persist(plantilla);
        return plantilla;
    }

    private PlantillaProyecto persistPlantillaProyecto(Long ownerId) {
        PlantillaProyecto plantilla = new PlantillaProyecto();
        plantilla.usuarioId = ownerId;
        plantilla.nombre = "Plantilla proyecto personal";
        plantilla.fechaCreacion = Instant.now();
        plantilla.snapshotEstructura = "{}";
        plantillaProyectoRepository.persist(plantilla);
        return plantilla;
    }

    private Capitulo persistCapitulo(Long presupuestoId, String item) {
        Capitulo capitulo = new Capitulo();
        capitulo.presupuestoId = presupuestoId;
        capitulo.parentId = null;
        capitulo.item = item;
        capitulo.descripcion = "Capítulo de prueba " + item;
        capitulo.orden = (short) 1;
        capitulo.total = BigDecimal.ZERO;
        capituloRepository.persist(capitulo);
        return capitulo;
    }

    private Rubro persistRubro(Long capituloId, Long apuId, String item, String codigo) {
        Rubro rubro = new Rubro();
        rubro.capituloId = capituloId;
        rubro.apuId = apuId;
        rubro.item = item;
        rubro.codigo = codigo;
        rubro.descripcion = "Rubro de prueba " + codigo;
        rubro.unidad = "u";
        rubro.cantidad = new BigDecimal("1.000000");
        rubro.precioUnitario = BigDecimal.ZERO;
        rubro.precioTotal = BigDecimal.ZERO;
        rubroRepository.persist(rubro);
        return rubro;
    }
}
