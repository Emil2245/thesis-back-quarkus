package ec.uce.propuestas.insumo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.common.ErrorPayload;
import ec.uce.propuestas.insumo.dto.BasePersonalCrearRequest;
import ec.uce.propuestas.insumo.dto.BasePersonalResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WU-05 — Pruebas focalizadas del seam de servicio para bases PERSONALES
 * (N04 §A9, decisión N04): creación con owner forzado, listado scoped al
 * caller, aislamiento frente a CENTRAL/PROYECTO y otros dueños, validación
 * de nombre no-blanco sin persistencia, y lookup por {@code publicId} ajeno
 * que mapea a 404 vía {@link Optional#empty()}.
 *
 * <p>Convenciones del proyecto: {@code @QuarkusTest} con TRUNCATE en
 * {@code @BeforeEach} (mismo patrón que {@code InsumoResourceIT} /
 * {@code PublicIdPersistenceTest}); se invoca el servicio directamente,
 * sin capa HTTP, para focalizar las invariantes del dominio. La seam
 * REST se valida transversalmente por el módulo proyecto/usuario.</p>
 */
@QuarkusTest
class BasesPersonalesServiceTest {

    @Inject
    BasesPersonalesService basesPersonalesService;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // Crear + listar owner (camino feliz)
    // =========================================================================

    @Test
    @TestTransaction
    void TC_BP_01_crear_y_listar_base_personal_del_caller() {
        Usuario owner = persistUsuario("owner@ex.com");

        BasePersonalResponse creada =
                basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("mis-rubros"));

        assertNotNull(creada.id(), "La respuesta debe exponer el public_id UUIDv7");
        assertEquals("mis-rubros", creada.nombre());
        assertFalse(creada.archivada(), "Una base recién creada no está archivada");

        List<BasePersonalResponse> delCaller = basesPersonalesService.listar(owner.id);
        assertEquals(1, delCaller.size(), "El caller debe ver únicamente su base PERSONAL");
        assertEquals(creada.id(), delCaller.get(0).id());
        assertEquals("mis-rubros", delCaller.get(0).nombre());
        assertFalse(delCaller.get(0).archivada());
    }

    @Test
    @TestTransaction
    void TC_BP_02_crear_fuerza_tipo_personal_owner_caller_y_proyecto_null() {
        Usuario owner = persistUsuario("owner@ex.com");
        BasePersonalResponse creada =
                basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("catalogo-A"));

        // Releemos vía el seam del repositorio para verificar invariantes de la fila persistida.
        BaseInsumos fila = baseInsumosRepository
                .listarPersonalesDeUsuario(owner.id)
                .get(0);
        assertEquals(TipoBase.PERSONAL, fila.tipo, "El tipo debe quedar forzado a PERSONAL");
        assertEquals(owner.id, fila.usuarioId, "El dueño debe ser el caller, no el cliente");
        assertNotNull(fila.publicId, "El publicId debe quedar generado por la columna uuidv7()");
        assertEquals(creada.id(), fila.publicId, "La respuesta debe exponer el mismo publicId persistido");
        assertEquals(false, fila.archivada, "archivada debe nacer en false");
    }

    // =========================================================================
    // Aislamiento de dueño: la lista nunca expone bases ajenas
    // =========================================================================

    @Test
    @TestTransaction
    void TC_BP_03_listar_aísla_a_otros_usuarios() {
        Usuario alice = persistUsuario("alice@ex.com");
        Usuario bob = persistUsuario("bob@ex.com");

        basesPersonalesService.crear(alice.id, new BasePersonalCrearRequest("solo-alice"));

        List<BasePersonalResponse> basesDeAlice = basesPersonalesService.listar(alice.id);
        List<BasePersonalResponse> basesDeBob = basesPersonalesService.listar(bob.id);

        assertEquals(1, basesDeAlice.size(), "Alice debe ver su base");
        assertEquals("solo-alice", basesDeAlice.get(0).nombre());
        assertTrue(basesDeBob.isEmpty(), "Bob no debe ver la base PERSONAL de Alice");
    }

    // =========================================================================
    // Exclusión de CENTRAL/PROYECTO del listado PERSONAL
    // =========================================================================

    @Test
    @TestTransaction
    void TC_BP_04_listar_excluye_bases_centrales_y_de_proyecto() {
        Usuario owner = persistUsuario("owner@ex.com");
        Proyecto proyecto = persistProyecto(owner.id, "Puente Sur");
        persistBaseCentral("Catálogo MOP");
        persistBaseProyecto(proyecto.id, "Insumos del proyecto #" + proyecto.id);
        basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("mis-insumos"));

        List<BasePersonalResponse> delCaller = basesPersonalesService.listar(owner.id);

        assertEquals(1, delCaller.size(), "Solo debe aparecer la base PERSONAL del caller");
        assertEquals("mis-insumos", delCaller.get(0).nombre());
    }

    // =========================================================================
    // Validación: nombre no-blanco rechaza y NO persiste
    // =========================================================================

    @Test
    @TestTransaction
    void TC_BP_05_nombre_en_blanco_rechaza_y_no_persiste() {
        Usuario owner = persistUsuario("owner@ex.com");

        WebApplicationException exBlanco = assertThrows(
                WebApplicationException.class,
                () -> basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("   ")));
        assertEquals(400, exBlanco.getResponse().getStatus());
        assertEquals("validacion", codigoDe(exBlanco));

        WebApplicationException exVacio = assertThrows(
                WebApplicationException.class,
                () -> basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("")));
        assertEquals(400, exVacio.getResponse().getStatus());

        WebApplicationException exNulo = assertThrows(
                WebApplicationException.class,
                () -> basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest(null)));
        assertEquals(400, exNulo.getResponse().getStatus());

        assertTrue(
                basesPersonalesService.listar(owner.id).isEmpty(),
                "Ninguna de las solicitudes inválidas debe haber persistido");
    }

    @Test
    @TestTransaction
    void TC_BP_06_nombre_duplicado_en_segmento_personal_rechaza() {
        Usuario owner = persistUsuario("owner@ex.com");
        basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("catalogo-A"));

        WebApplicationException ex = assertThrows(
                WebApplicationException.class,
                () -> basesPersonalesService.crear(owner.id, new BasePersonalCrearRequest("  catalogo-A  ")));
        assertEquals(400, ex.getResponse().getStatus());
        assertEquals("validacion", codigoDe(ex));
        assertEquals(1, basesPersonalesService.listar(owner.id).size(), "La duplicidad no debe crear nueva fila");
    }

    // =========================================================================
    // Lookup por publicId: ajenos devuelven empty (404)
    // =========================================================================

    @Test
    @TestTransaction
    void TC_BP_07_lookup_por_public_id_ajeno_devuelve_empty() {
        Usuario alice = persistUsuario("alice@ex.com");
        Usuario bob = persistUsuario("bob@ex.com");
        BasePersonalResponse deAlice =
                basesPersonalesService.crear(alice.id, new BasePersonalCrearRequest("solo-alice"));

        Optional<BaseInsumos> comoDuenno =
                basesPersonalesService.buscarPorPublicId(deAlice.id(), alice.id);
        assertTrue(comoDuenno.isPresent(), "El dueño debe verse a sí mismo por publicId");
        assertEquals(TipoBase.PERSONAL, comoDuenno.get().tipo);
        assertEquals(alice.id, comoDuenno.get().usuarioId);

        Optional<BaseInsumos> comoAjeno =
                basesPersonalesService.buscarPorPublicId(deAlice.id(), bob.id);
        assertTrue(comoAjeno.isEmpty(), "Un caller distinto debe recibir empty → 404 (nunca 403)");
    }

    @Test
    @TestTransaction
    void TC_BP_08_lookup_por_public_id_inventado_devuelve_empty() {
        Usuario owner = persistUsuario("owner@ex.com");
        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000000");

        Optional<BaseInsumos> resuelto =
                basesPersonalesService.buscarPorPublicId(invented, owner.id);
        assertTrue(
                resuelto.isEmpty(),
                "Un UUIDv7 que no existe en BD no debe resolverse ni para el propio dueño (404)");
    }

    @Test
    @TestTransaction
    void TC_BP_09_lookup_sobre_public_id_de_otra_base_no_personal_devuelve_empty() {
        Usuario owner = persistUsuario("owner@ex.com");
        BaseInsumos central = persistBaseCentral("Solo Central");

        // El seam de servicio BasesPersonalesService.buscarPorPublicId está restringido
        // al segmento PERSONAL: una CENTRAL ajena al caller (o propia) no debe filtrarse aquí.
        Optional<BaseInsumos> resueltoSobreCentral =
                basesPersonalesService.buscarPorPublicId(central.publicId, owner.id);
        assertTrue(
                resueltoSobreCentral.isEmpty(),
                "Una fila CENTRAL no debe filtrarse por la seam PERSONAL del caller");
    }

    // =========================================================================
    // Fixtures (in-memory; sin REST ni flyway)
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
        p.plazoUnidad = PlazoUnidad.MES;
        p.estado = EstadoProyecto.BORRADOR;
        p.direccionInstitucional = "GAD Test";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        proyectoRepository.persist(p);
        return p;
    }

    private BaseInsumos persistBaseCentral(String nombre) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = nombre;
        b.tipo = TipoBase.CENTRAL;
        b.usuarioId = null;
        b.proyectoId = null;
        b.archivada = false;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        baseInsumosRepository.persist(b);
        baseInsumosRepository.getEntityManager().flush();
        return b;
    }

    private BaseInsumos persistBaseProyecto(Long proyectoId, String nombre) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = nombre;
        b.tipo = TipoBase.PROYECTO;
        b.usuarioId = null;
        b.proyectoId = proyectoId;
        b.archivada = false;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        baseInsumosRepository.persist(b);
        baseInsumosRepository.getEntityManager().flush();
        return b;
    }

    private static String codigoDe(WebApplicationException ex) {
        Object entity = ex.getResponse().getEntity();
        if (entity instanceof ErrorPayload payload) {
            return payload.codigo();
        }
        throw new AssertionError("Payload de error inesperado: "
                + (entity == null ? "null" : entity.getClass()));
    }
}
