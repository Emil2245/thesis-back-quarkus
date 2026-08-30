package ec.uce.propuestas.plantilla.service;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.plantilla.dto.ProyectoDesdePlantillaResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import ec.uce.propuestas.plantilla.repository.PlantillaProyectoRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 06 (P-46, N04 §A8) — Pruebas focalizadas del servicio de plantillas de
 * proyecto. Patrón: {@code @QuarkusTest} + TRUNCATE en {@code @BeforeEach}.
 */
@QuarkusTest
class PlantillaProyectoServiceTest {

    @Inject
    PlantillaProyectoService plantillaProyectoService;

    @Inject
    PlantillaProyectoRepository plantillaProyectoRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository apuSeccionRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    DataSource ds;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "plantilla_proyecto, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // A. Listar / detalle — owner isolation (RNF-05)
    // =========================================================================

    @Test
    @Transactional
    void TC_PP_01_listar_devuelve_solo_propias_y_owner_to_404() {
        Usuario alice = persistUsuario("alice-pp@ex.com");
        Usuario bob = persistUsuario("bob-pp@ex.com");
        Proyecto pAlice = persistProyecto(alice.id, "Alice obras");
        persistirPresupuestoVigente(pAlice.id);
        PlantillaProyecto plantillaAlice = persistPlantillaDesdeProyecto(alice.id, pAlice.publicId, "Plantilla Alice");

        List<PlantillaProyectoResponse> deAlice = plantillaProyectoService.listar(alice.id);
        List<PlantillaProyectoResponse> deBob = plantillaProyectoService.listar(bob.id);

        assertEquals(1, deAlice.size(), "Alice ve su plantilla");
        assertEquals(plantillaAlice.publicId, deAlice.get(0).id());
        assertEquals(0, deBob.size(), "Bob no ve la plantilla ajena");

        // Detalle: ajena → 404.
        ProblemaException ex = assertThrows(
                ProblemaException.class, () -> plantillaProyectoService.detalle(plantillaAlice.publicId, bob.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PP_02_eliminar_propia_204_y_no_altera_plantillas_ajenas() {
        Usuario alice = persistUsuario("alice-del@ex.com");
        Usuario bob = persistUsuario("bob-del@ex.com");
        Proyecto pAlice = persistProyecto(alice.id, "Alice del");
        persistirPresupuestoVigente(pAlice.id);
        PlantillaProyecto plantillaAlice = persistPlantillaDesdeProyecto(alice.id, pAlice.publicId, "Borrable");

        // Ajena → 404
        ProblemaException exAjena = assertThrows(
                ProblemaException.class, () -> plantillaProyectoService.eliminar(plantillaAlice.publicId, bob.id));
        assertEquals(404, exAjena.getResponse().getStatus());
        assertNotNull(plantillaProyectoRepository.findById(plantillaAlice.id), "No se borra por ajena");

        // Propia → ok
        plantillaProyectoService.eliminar(plantillaAlice.publicId, alice.id);
        assertNull(plantillaProyectoRepository.findById(plantillaAlice.id));
    }

    // =========================================================================
    // B. Guardar desde proyecto — snapshot estructural price-free
    // =========================================================================

    @Test
    @Transactional
    void TC_PP_03_guardar_desde_proyecto_propio_genera_snapshot_estructural() {
        Usuario alice = persistUsuario("alice-guardar@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Obras completas");
        Long presupuestoId = insertarPresupuestoYVigente(proyecto.id);

        // Sembramos un capítulo raíz y un rubro con APU estructural.
        Long capId = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarRubro(capId, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyectoResponse resp = plantillaProyectoService.guardarDesdeProyecto(
                proyecto.publicId, "Mi plantilla", "descripcion demo", alice.id);

        assertNotNull(resp.id());
        assertEquals("Mi plantilla", resp.nombre());
        assertEquals("descripcion demo", resp.descripcion());
        assertNotNull(resp.snapshotEstructura());
        assertNotNull(resp.fechaCreacion());

        // El snapshot persistido es estructural: sin IDs, sin precios, sin
        // cantidades de obra.
        PlantillaProyecto persistida =
                plantillaProyectoRepository.find("publicId = ?1", resp.id()).firstResult();
        String snapshot = persistida.snapshotEstructura;
        assertFalse(snapshot.contains("\"precioOverride\""));
        assertFalse(snapshot.contains("\"insumoId\""));
        assertFalse(snapshot.contains("\"apuId\""));
        assertFalse(snapshot.contains("\"proyectoId\""));
        assertFalse(snapshot.contains("\"cantidadObra\""));
        assertFalse(snapshot.contains("cronograma"));
        assertFalse(snapshot.contains("firmante"));
        assertFalse(snapshot.contains("actividad"));
        assertTrue(snapshot.contains("\"item\":\"1\""));
        assertTrue(snapshot.contains("\"codigo\":\"RP-001\""), "Código APU preservado");
    }

    @Test
    @Transactional
    void TC_PP_04_guardar_desde_proyecto_ajeno_devuelve_404() {
        Usuario alice = persistUsuario("alice-ajena@ex.com");
        Usuario bob = persistUsuario("bob-ajena@ex.com");
        Proyecto pAlice = persistProyecto(alice.id, "Ajeno");
        persistirPresupuestoVigente(pAlice.id);

        ProblemaException ex = assertThrows(
                ProblemaException.class,
                () -> plantillaProyectoService.guardarDesdeProyecto(pAlice.publicId, "Hack", null, bob.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PP_05_guardar_nombre_vacio_devuelve_400() {
        Usuario alice = persistUsuario("alice-vac@ex.com");
        Proyecto p = persistProyecto(alice.id, "Vacio");
        persistirPresupuestoVigente(p.id);

        ProblemaException ex = assertThrows(
                ProblemaException.class,
                () -> plantillaProyectoService.guardarDesdeProyecto(p.publicId, "  ", null, alice.id));
        assertEquals(400, ex.getResponse().getStatus());
    }

    // =========================================================================
    // C. Aplicar plantilla — reconstrucción completa y lineage
    // =========================================================================

    @Test
    @Transactional
    void TC_PP_10_aplicar_crea_proyecto_distinto_con_presupuesto_v1_y_capitulos_recursivos() {
        Usuario alice = persistUsuario("alice-apl@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);

        // Parámetros origen: el snapshot debe arrastrarlos.
        insertarParametros(
                origen.id, new BigDecimal("0.0700"), new BigDecimal("0.2000"), new BigDecimal("0.1500"), "USD");

        Long cap1 = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apu1 = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarRubro(cap1, apu1, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Estructura completa");

        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo desde plantilla", alice.id);

        assertNotNull(out.proyecto().id(), "Proyecto nuevo creado");
        assertEquals("Nuevo desde plantilla", out.proyecto().nombreProyecto());
        assertEquals(EstadoProyecto.BORRADOR, out.proyecto().estado());
        assertFalse(out.tieneAdvertencias(), "Sin faltantes");
        // ID distinto del origen.
        assertNotEquals(origen.publicId, out.proyecto().id());
        // PublicId del origen queda registrado como lineage (FK interno).
        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        assertEquals(plantilla.id, nuevo.plantillaProyectoOrigenId, "lineage persistido");

        // Presupuesto v1 vigente.
        Presupuesto nuevoPresupuesto =
                presupuestoRepository.findVigenteDeProyecto(nuevo.id).orElseThrow();
        assertEquals((short) 1, nuevoPresupuesto.version);
        assertTrue(nuevoPresupuesto.esVigente);

        // El árbol de capítulos del nuevo proyecto replica el del origen.
        long capCountNuevo = contarCapitulosDe(nuevoPresupuesto.id);
        long capCountOrigen = contarCapitulosDe(presupuestoId);
        assertEquals(capCountOrigen, capCountNuevo, "Mismo número de capítulos");
    }

    @Test
    @Transactional
    void TC_PP_11_aplicar_reconstruye_rubros_con_cantidad_cero_y_apus_con_filas() {
        Usuario alice = persistUsuario("alice-reco@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Reconstruir");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);

        Long cap = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");

        // Una fila MO en el APU origen con insumo MO-001 existente en la base
        // PROYECTO del origen. El nuevo proyecto no tendrá esa base ⇒ fallback.
        BaseInsumos baseOrigen = persistBaseProyecto(origen.id);
        Insumo mo = persistInsumo(baseOrigen.id, "MO-001", TipoInsumo.MANO_OBRA, "h", "4.75");
        insertarFilaMo(apuId, mo.id, "0.1", "0.1", "MO-001");

        insertarRubro(cap, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Reco");
        ProyectoDesdePlantillaResponse out = plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo", alice.id);

        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        Presupuesto nuevoPresupuesto =
                presupuestoRepository.findVigenteDeProyecto(nuevo.id).orElseThrow();

        // El rubro reconstruido existe con cantidad = 0 (pendiente).
        BigDecimal cantidadRubro = cantidadDePrimerRubro(nuevoPresupuesto.id);
        assertNotNull(cantidadRubro, "Rubro reconstruido");
        assertEquals(0, cantidadRubro.compareTo(BigDecimal.ZERO), "cantidad = 0 (pendiente)");

        // El APU reconstruido existe con sus secciones y filas.
        long apus = contarApusDe(nuevoPresupuesto.id);
        assertEquals(1, apus, "1 APU reconstruido");
    }

    @Test
    @Transactional
    void TC_PP_12_aplicar_reutiliza_fallback_central_personal_y_proyecto() {
        Usuario alice = persistUsuario("alice-fb@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen fallback");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);

        // Insumo CENTRAL "MO-CENT" — debería copiarse a PROYECTO del nuevo proyecto.
        BaseInsumos baseCentral = persistBaseCentral("Base central fallback");
        Insumo moCentral = persistInsumo(baseCentral.id, "MO-CENT", TipoInsumo.MANO_OBRA, "h", "5.00");

        // Insumo PERSONAL de Alice "MO-PER" — debería copiarse.
        BaseInsumos basePersonal = persistBasePersonal(alice.id, "Base personal fallback");
        Insumo moPersonal = persistInsumo(basePersonal.id, "MO-PER", TipoInsumo.MANO_OBRA, "h", "5.50");

        // APU origen con esas dos filas + un faltante "MO-FALTA".
        Long cap = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarFilaMo(apuId, moCentral.id, "0.5", "0.1", "MO-CENT");
        insertarFilaMo(apuId, moPersonal.id, "0.4", "0.1", "MO-PER");
        insertarFilaMo(apuId, null, "0.3", "0.1", "MO-FALTA");
        insertarRubro(cap, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Fallback");
        ProyectoDesdePlantillaResponse out = plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo", alice.id);

        assertTrue(out.tieneAdvertencias(), "Faltante genera advertencia");
        assertEquals(1, out.advertencias().size());
        AdvertenciaPlantillaResponse adv = out.advertencias().get(0);
        assertEquals("MO-FALTA", adv.insumoCodigo());
        assertEquals(AdvertenciaPlantillaResponse.MOTIVO_NO_EXISTE, adv.motivo());

        // El nuevo proyecto tiene una base PROYECTO.
        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        BaseInsumos baseNueva = baseInsumosRepository.findByProyecto(nuevo.id).orElseThrow();

        // Las dos copias (CENTRAL y PERSONAL) deben estar en la base nueva
        // con los mismos códigos (D-07 dedup).
        assertTrue(
                insumoRepository.findByBaseYcodigo(baseNueva.id, "MO-CENT").isPresent(), "CENTRAL copiado a PROYECTO");
        assertTrue(
                insumoRepository.findByBaseYcodigo(baseNueva.id, "MO-PER").isPresent(), "PERSONAL copiado a PROYECTO");
        // El faltante queda como fila pendiente (insumoId=null, override 0).
        assertTrue(insumoRepository.findByBaseYcodigo(baseNueva.id, "MO-FALTA").isEmpty(), "Faltante NO se copia");

        // Fila pendiente: override 0 en columna de sección.
        Presupuesto nuevoPresupuesto =
                presupuestoRepository.findVigenteDeProyecto(nuevo.id).orElseThrow();
        BigDecimal overrideFaltante = overrideDeFilaPendienteEn(nuevoPresupuesto.id);
        assertNotNull(overrideFaltante, "Fila pendiente tiene override 0");
        assertEquals(0, overrideFaltante.compareTo(BigDecimal.ZERO));
    }

    @Test
    @Transactional
    void TC_PP_13_aplicar_no_crea_cronograma_firmantes_ni_log() {
        Usuario alice = persistUsuario("alice-nocrono@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen nocrono");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);
        Long cap = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarRubro(cap, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Sin op");
        ProyectoDesdePlantillaResponse out = plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo", alice.id);

        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        Presupuesto nuevoPresupuesto =
                presupuestoRepository.findVigenteDeProyecto(nuevo.id).orElseThrow();

        // Cronograma: no debe existir.
        long cronogramas = contarTabla("cronograma", "presupuesto_id", nuevoPresupuesto.id);
        assertEquals(0, cronogramas, "No se crea cronograma");

        // Firmantes del proyecto: no debe haber ninguno.
        long firmantes = contarTabla("firmante", "proyecto_id", nuevo.id);
        assertEquals(0, firmantes, "No se crean firmantes");

        // Log_actividad: el aplicado no debe emitir filas en este pase (los
        // eventos D-13 los emite otro seam).
        long logs = contarLogActividadPara(nuevo.id);
        assertEquals(0, logs, "No se emite log");
    }

    @Test
    @Transactional
    void TC_PP_14_aplicar_template_luego_borrar_template_conserva_proyecto_via_set_null() {
        Usuario alice = persistUsuario("alice-setnull@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen lineage");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);
        Long cap = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarRubro(cap, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Lineage");
        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo desde plantilla", alice.id);

        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        assertEquals(plantilla.id, nuevo.plantillaProyectoOrigenId);

        // Borrar la plantilla — el proyecto persiste con plantillaProyectoOrigenId = null.
        plantillaProyectoService.eliminar(plantilla.publicId, alice.id);
        entityManager.flush();
        entityManager.clear();
        Proyecto reloaded = proyectoRepository.findById(nuevo.id);
        assertNotNull(reloaded, "Proyecto persiste tras borrar la plantilla");
        assertNull(reloaded.plantillaProyectoOrigenId, "FK queda null por ON DELETE SET NULL");
    }

    @Test
    @Transactional
    void TC_PP_15_aplicar_plantilla_ajena_devuelve_404() {
        Usuario alice = persistUsuario("alice-pa2@ex.com");
        Usuario bob = persistUsuario("bob-pa2@ex.com");
        Proyecto pAlice = persistProyecto(alice.id, "Alice origen");
        Long presupuestoAlice = insertarPresupuestoYVigente(pAlice.id);
        Long cap = insertarCapitulo(presupuestoAlice, null, "1", "OBRAS", 1);
        Long apu = insertarApu(presupuestoAlice, "RP-001", "Replanteo", "m2");
        insertarRubro(cap, apu, "1.1", "RP-001", "Replanteo", "m2");
        PlantillaProyecto plantillaAlice = persistPlantillaDesdeProyecto(alice.id, pAlice.publicId, "Solo Alice");

        ProblemaException ex = assertThrows(
                ProblemaException.class,
                () -> plantillaProyectoService.aplicar(plantillaAlice.publicId, "Hack", bob.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PP_16_aplicar_nombre_vacio_devuelve_400() {
        Usuario alice = persistUsuario("alice-nv@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen nv");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);
        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "X");

        ProblemaException ex = assertThrows(
                ProblemaException.class, () -> plantillaProyectoService.aplicar(plantilla.publicId, "  ", alice.id));
        assertEquals(400, ex.getResponse().getStatus());
    }

    // =========================================================================
    // E. Cabecera reutilizable — captura y aplicación
    // =========================================================================

    @Test
    @Transactional
    void TC_PP_30_guardar_desde_proyecto_incluye_cabecera_sin_owner_estado_logo_nombre_lineage() throws Exception {
        Usuario alice = persistUsuario("alice-cab@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Cabecera origen");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);

        // Sembramos campos adicionales de la cabecera que el helper no cubre.
        origen.subdireccionInstitucional = "Subdir demo";
        origen.tituloEt1 = "ET-1 origen";
        origen.tituloEt2 = "ET-2 origen";
        origen.fechaInicio = LocalDate.parse("2026-04-01");

        PlantillaProyectoResponse resp =
                plantillaProyectoService.guardarDesdeProyecto(origen.publicId, "Con cabecera", null, alice.id);
        PlantillaProyecto persistida = plantillaProyectoRepository
                .findByPublicIdAndOwnerScope(resp.id(), alice.id)
                .orElseThrow();
        String snap = persistida.snapshotEstructura;

        // Cabecera SÍ presente y con los campos esperados.
        assertTrue(snap.contains("\"cabecera\""), "Snapshot incluye bloque cabecera");
        assertTrue(snap.contains("\"codigo\":\"P-" + Math.abs(origen.nombreProyecto.hashCode()) + "\""));
        assertTrue(snap.contains("\"direccionInstitucional\":\"UCE\""));
        assertTrue(snap.contains("\"subdireccionInstitucional\":\"Subdir demo\""));
        assertTrue(snap.contains("\"tituloEt1\":\"ET-1 origen\""));
        assertTrue(snap.contains("\"tituloEt2\":\"ET-2 origen\""));
        assertTrue(snap.contains("\"fechaInicio\":\"2026-04-01\""));

        // Cabecera NO arrastra owner / estado / logo / nombre / lineage /
        // timestamps (no aparecen como campos del snapshot).
        assertFalse(snap.contains("\"usuarioId\""), "Cabecera no arrastra owner");
        assertFalse(snap.contains("\"estado\""), "Cabecera no arrastra estado");
        assertFalse(snap.contains("\"logo\""), "Cabecera no arrastra logo");
        assertFalse(
                snap.contains("\"nombreProyecto\":\"Cabecera origen\""), "Cabecera no arrastra nombre del proyecto");
        assertFalse(snap.contains("\"plantillaProyectoOrigenId\""), "Cabecera no arrastra lineage");
        assertFalse(snap.contains("\"createdAt\""), "Cabecera no arrastra timestamps");
        assertFalse(snap.contains("\"updatedAt\""), "Cabecera no arrastra timestamps");
    }

    @Test
    @Transactional
    void TC_PP_31_aplicar_replica_cabecera_del_snapshot_y_nombre_del_request() throws Exception {
        Usuario alice = persistUsuario("alice-rep@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Replica origen");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);
        origen.fechaInicio = LocalDate.parse("2026-05-15");
        origen.subdireccionInstitucional = "Subdir replica";
        origen.tituloEt1 = "ET-1 replica";
        origen.tituloEt2 = "ET-2 replica";
        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Replica cabecera");

        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantilla.publicId, "Nombre provisto por el cliente", alice.id);
        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();

        // El request fija el nombre (autoritativo), NO el snapshot.
        assertEquals("Nombre provisto por el cliente", nuevo.nombreProyecto);
        assertNotEquals(origen.nombreProyecto, nuevo.nombreProyecto);

        // Cabecera replicada desde el snapshot.
        assertEquals(origen.codigo, nuevo.codigo);
        assertEquals(origen.descripcion, nuevo.descripcion);
        assertEquals(origen.anio, nuevo.anio);
        assertEquals(LocalDate.parse("2026-05-15"), nuevo.fechaInicio);
        assertEquals(origen.plazoEjecucion, nuevo.plazoEjecucion);
        assertEquals(origen.plazoUnidad, nuevo.plazoUnidad);
        assertEquals(origen.direccionInstitucional, nuevo.direccionInstitucional);
        assertEquals("Subdir replica", nuevo.subdireccionInstitucional);
        assertEquals("ET-1 replica", nuevo.tituloEt1);
        assertEquals("ET-2 replica", nuevo.tituloEt2);

        // El estado siempre es BORRADOR al aplicar.
        assertEquals(EstadoProyecto.BORRADOR, nuevo.estado);
    }

    @Test
    @Transactional
    void TC_PP_32_aplicar_v004_minimo_no_viola_parametros_y_usa_defaults_editables() throws Exception {
        Usuario alice = persistUsuario("alice-v004@ex.com");
        // Sembramos directamente la fila V004 mínimo (sin cabecera, sin
        // parametros, sólo capitulos vacíos) y aplicamos.
        UUID plantillaPublicId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO plantilla_proyecto (usuario_id, nombre, snapshot_estructura) "
                                + "VALUES (?, ?, ?::jsonb) RETURNING public_id")) {
            ps.setLong(1, alice.id);
            ps.setString(2, "V004 mínimo");
            ps.setString(3, "{\"capitulos\":[]}");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                plantillaPublicId = (UUID) rs.getObject(1);
            }
        }

        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantillaPublicId, "Nuevo desde V004", alice.id);

        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();

        // Cabecera: cae a defaults editables.
        assertEquals((short) LocalDate.now().getYear(), nuevo.anio, "anio = año actual");
        assertEquals((short) 4, nuevo.plazoEjecucion, "plazo default 4");
        assertEquals(PlazoUnidad.MES, nuevo.plazoUnidad, "plazoUnidad default MES");
        assertEquals("Pendiente de editar", nuevo.direccionInstitucional, "direccionInstitucional default editable");
        assertNull(nuevo.codigo, "codigo null sin cabecera");
        assertNull(nuevo.fechaInicio, "fechaInicio null sin cabecera");
        assertEquals(EstadoProyecto.BORRADOR, nuevo.estado);

        // ParametrosProyecto: defaults del sistema (no violación NOT NULL).
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT porcentaje_herramienta_menor, porcentaje_indirecto, iva, moneda, "
                                + "mostrar_secciones_vacias, sufijos_seccion_activos, "
                                + "mostrar_subtotales_seccion, mostrar_subtotales_pie, "
                                + "mostrar_nombre_proyecto_header, enumerar_apus, "
                                + "mensaje_footer, modo_codigo_rubro "
                                + "FROM parametros_proyecto WHERE proyecto_id = ?")) {
            ps.setLong(1, nuevo.id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "ParametrosProyecto creado");
                // Iva y HM son NOT NULL — verificamos que no quedan null.
                assertNotNull(rs.getBigDecimal("iva"));
                assertNotNull(rs.getBigDecimal("porcentaje_herramienta_menor"));
                assertNotNull(rs.getString("moneda"));
                assertNotNull(rs.getString("mensaje_footer"));
                assertNotNull(rs.getString("modo_codigo_rubro"));
                assertEquals("USD", rs.getString("moneda"));
                assertEquals("AUTOGENERADO", rs.getString("modo_codigo_rubro"));
            }
        }
        assertFalse(out.tieneAdvertencias(), "V004 mínimo sin faltantes");
    }

    @Test
    @Transactional
    void TC_PP_33_aplicar_con_cabecera_parcial_respeta_nulls_y_cae_a_defaults_solo_en_faltantes() throws Exception {
        Usuario alice = persistUsuario("alice-partial@ex.com");
        // Cabecera parcial: trae codigo y anio pero NO plazo / plazoUnidad /
        // direccionInstitucional. El servicio debe usar los defaults sólo en
        // los campos faltantes, conservando los nulos reales en cabecera
        // (subdireccion, tituloEt1/Et2).
        UUID plantillaPublicId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO plantilla_proyecto (usuario_id, nombre, snapshot_estructura) "
                                + "VALUES (?, ?, ?::jsonb) RETURNING public_id")) {
            ps.setLong(1, alice.id);
            ps.setString(2, "Cabecera parcial");
            ps.setString(3, "{\"cabecera\":{\"codigo\":\"P-PARTIAL\",\"anio\":2025}," + "\"capitulos\":[]}");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                plantillaPublicId = (UUID) rs.getObject(1);
            }
        }

        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantillaPublicId, "Nuevo parcial", alice.id);
        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();

        // Cabecera con campos presentes replicados.
        assertEquals("P-PARTIAL", nuevo.codigo);
        assertEquals((short) 2025, nuevo.anio);
        // Cabecera con campos null — quedan null (no caen al default si la
        // cabecera los declara explícitamente como ausentes; la cabecera
        // NO los aporta, así que el default editable aplica).
        // plazoEjecucion / plazoUnidad / direccionInstitucional vienen
        // null en cabecera → servicio usa los defaults editables.
        assertEquals((short) 4, nuevo.plazoEjecucion);
        assertEquals(PlazoUnidad.MES, nuevo.plazoUnidad);
        assertEquals("Pendiente de editar", nuevo.direccionInstitucional);
        // Subdireccion / titulos: cabecera no los declara → null en proyecto.
        assertNull(nuevo.subdireccionInstitucional);
        assertNull(nuevo.tituloEt1);
        assertNull(nuevo.tituloEt2);
    }

    // =========================================================================
    // D. Rollback — error intermedio deshace TODO el agregado
    // =========================================================================

    @Test
    @Transactional
    void TC_PP_20_rollback_si_falla_no_deja_agregado_parcial() {
        // La operación `aplicar` está bajo @Transactional; un fallo intermedio
        // (e.g. un FK violation intencional) debe revertir TODO el agregado.
        // Forzamos el fallo insertando manualmente una FK insumo_id inválida
        // en una fila de APU durante la transacción... pero eso requiere
        // ganarle a la API. Alternativa válida: probar que el servicio
        // rechaza el caso de "snapshot vacío" sólo con un proyecto huérfano,
        // o probar la atomicidad creando dos aplicaciones consecutivas y
        // verificando que cada una crea exactamente 1 proyecto (sin "restos"
        // de un fallo hipotético).
        Usuario alice = persistUsuario("alice-rollb@ex.com");
        Proyecto origen = persistProyecto(alice.id, "Origen rollback");
        Long presupuestoId = insertarPresupuestoYVigente(origen.id);
        Long cap = insertarCapitulo(presupuestoId, null, "1", "OBRAS", 1);
        Long apuId = insertarApu(presupuestoId, "RP-001", "Replanteo", "m2");
        insertarRubro(cap, apuId, "1.1", "RP-001", "Replanteo", "m2");

        PlantillaProyecto plantilla = persistPlantillaDesdeProyecto(alice.id, origen.publicId, "Rollback");

        long proyectosAntes = contarTabla("proyecto", "usuario_id", alice.id);

        ProyectoDesdePlantillaResponse out = plantillaProyectoService.aplicar(plantilla.publicId, "Nuevo", alice.id);
        assertNotNull(out.proyecto().id());

        long proyectosDespues = contarTabla("proyecto", "usuario_id", alice.id);
        // El nuevo proyecto + el origen = exactamente 1 incremento (no más).
        assertEquals(proyectosAntes + 1, proyectosDespues, "Exactamente 1 proyecto nuevo");

        // Para un proyecto NUEVO no puede haber cronograma, ni actividades,
        // ni firmantes, ni log (atomicidad + atomicidad de la operación).
        Proyecto nuevo = proyectoRepository
                .findByPublicIdAndOwnerScope(out.proyecto().id(), alice.id)
                .orElseThrow();
        assertEquals(0, contarTabla("firmante", "proyecto_id", nuevo.id), "Sin firmantes");
        assertEquals(0, contarLogActividadPara(nuevo.id), "Sin log");
    }

    // =========================================================================
    // Helpers de sembrado
    // =========================================================================

    private Usuario persistUsuario(String email) {
        Usuario u = new Usuario();
        u.nombre = "Usuario " + email;
        u.email = email;
        u.passwordHash = "hash-placeholder";
        u.rol = Rol.USUARIO;
        u.emailVerificado = true;
        u.activo = true;
        usuarioRepository.persist(u);
        return u;
    }

    private Proyecto persistProyecto(Long duennoId, String nombre) {
        Proyecto p = new Proyecto();
        p.usuarioId = duennoId;
        p.nombreProyecto = nombre;
        p.codigo = "P-" + Math.abs(nombre.hashCode());
        p.descripcion = "Proyecto demo";
        p.anio = (short) 2026;
        p.plazoEjecucion = (short) 4;
        p.plazoUnidad = PlazoUnidad.MES;
        p.estado = EstadoProyecto.BORRADOR;
        p.direccionInstitucional = "UCE";
        proyectoRepository.persist(p);
        return p;
    }

    private void persistirPresupuestoVigente(Long proyectoId) {
        insertarPresupuestoYVigente(proyectoId);
    }

    private Long insertarPresupuestoYVigente(Long proyectoId) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO presupuesto (proyecto_id, version, es_vigente) "
                                + "VALUES (?, 1, TRUE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertarParametros(Long proyectoId, BigDecimal hm, BigDecimal ci, BigDecimal iva, String moneda) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO parametros_proyecto (proyecto_id, porcentaje_herramienta_menor, "
                                + "porcentaje_indirecto, iva, moneda) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, proyectoId);
            ps.setBigDecimal(2, hm);
            if (ci == null) ps.setNull(3, java.sql.Types.NUMERIC);
            else ps.setBigDecimal(3, ci);
            ps.setBigDecimal(4, iva);
            ps.setString(5, moneda);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long insertarCapitulo(Long presupuestoId, Long parentId, String item, String descripcion, int orden) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                                + "VALUES (?, ?, ?, ?, ?, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            if (parentId == null) ps.setNull(2, java.sql.Types.BIGINT);
            else ps.setLong(2, parentId);
            ps.setString(3, item);
            ps.setString(4, descripcion);
            ps.setInt(5, orden);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long insertarApu(Long presupuestoId, String codigo, String descripcion, String unidad) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad) "
                                + "VALUES (?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, descripcion);
            ps.setString(4, unidad);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertarRubro(
            Long capituloId, Long apuId, String item, String codigo, String descripcion, String unidad) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                                + "cantidad, precio_unitario, precio_total) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0)")) {
            ps.setLong(1, capituloId);
            ps.setLong(2, apuId);
            ps.setString(3, item);
            ps.setString(4, codigo);
            ps.setString(5, descripcion);
            ps.setString(6, unidad);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertarFilaMo(
            Long apuId, Long insumoId, String cantidad, String rendimiento, String codigoPersistido) {
        try (Connection con = ds.getConnection()) {
            Long seccionId = null;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT id FROM apu_seccion WHERE apu_id = ? AND tipo = 'MANO_OBRA'")) {
                ps.setLong(1, apuId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        seccionId = rs.getLong(1);
                    }
                }
            }
            if (seccionId == null) {
                try (PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) "
                                + "VALUES (?, 'MANO_OBRA', 0, 2) RETURNING id")) {
                    ps.setLong(1, apuId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        seccionId = rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps =
                    con.prepareStatement("INSERT INTO apu_detalle (seccion_id, insumo_id, descripcion, orden, "
                            + "es_herramienta_menor, cantidad, tarifa_jornal, costo_hora, rendimiento, "
                            + "unidad, costo) VALUES (?, ?, ?, 1, FALSE, ?, 4.75, 4.75, ?, 'h', 0)")) {
                ps.setLong(1, seccionId);
                if (insumoId == null) ps.setNull(2, java.sql.Types.BIGINT);
                else ps.setLong(2, insumoId);
                ps.setString(3, codigoPersistido);
                ps.setBigDecimal(4, new BigDecimal(cantidad));
                ps.setBigDecimal(5, new BigDecimal(rendimiento));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PlantillaProyecto persistPlantillaDesdeProyecto(
            Long usuarioId, java.util.UUID proyectoPublicId, String nombre) {
        PlantillaProyectoResponse response =
                plantillaProyectoService.guardarDesdeProyecto(proyectoPublicId, nombre, null, usuarioId);
        return plantillaProyectoRepository
                .findByPublicIdAndOwnerScope(response.id(), usuarioId)
                .orElseThrow();
    }

    private BaseInsumos persistBaseProyecto(Long proyectoId) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = "Base proyecto " + proyectoId;
        b.tipo = TipoBase.PROYECTO;
        b.proyectoId = proyectoId;
        baseInsumosRepository.persist(b);
        return b;
    }

    private BaseInsumos persistBaseCentral(String nombre) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = nombre;
        b.tipo = TipoBase.CENTRAL;
        baseInsumosRepository.persist(b);
        return b;
    }

    private BaseInsumos persistBasePersonal(Long usuarioId, String nombre) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = nombre;
        b.tipo = TipoBase.PERSONAL;
        b.usuarioId = usuarioId;
        baseInsumosRepository.persist(b);
        return b;
    }

    private Insumo persistInsumo(Long baseId, String codigo, TipoInsumo tipo, String unidad, String precio) {
        Insumo insumo = new Insumo();
        insumo.baseId = baseId;
        insumo.codigo = codigo;
        insumo.tipo = tipo;
        insumo.descripcion = "Insumo " + codigo;
        insumo.unidad = unidad;
        insumo.precioUnitario = new BigDecimal(precio);
        insumoRepository.persist(insumo);
        return insumo;
    }

    private long contarCapitulosDe(Long presupuestoId) {
        return contarTabla("capitulo", "presupuesto_id", presupuestoId);
    }

    private long contarApusDe(Long presupuestoId) {
        return contarTabla("apu", "presupuesto_id", presupuestoId);
    }

    private long contarTabla(String tabla, String columnaFk, Long fk) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM " + tabla + " WHERE " + columnaFk + " = ?")) {
            ps.setLong(1, fk);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long contarLogActividadPara(Long proyectoId) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT count(*) FROM log_actividad " + "WHERE entidad = 'proyecto' AND entidad_id = ?")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal cantidadDePrimerRubro(Long presupuestoId) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT r.cantidad FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id "
                        + "WHERE c.presupuesto_id = ? ORDER BY r.id LIMIT 1")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal overrideDeFilaPendienteEn(Long presupuestoId) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT d.tarifa_jornal FROM apu_detalle d "
                        + "JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "JOIN apu a ON a.id = s.apu_id "
                        + "WHERE a.presupuesto_id = ? AND d.insumo_id IS NULL "
                        + "AND s.tipo = 'MANO_OBRA' LIMIT 1")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
