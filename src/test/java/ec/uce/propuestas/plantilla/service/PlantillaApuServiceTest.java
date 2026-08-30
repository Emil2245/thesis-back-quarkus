package ec.uce.propuestas.plantilla.service;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaApuCrearRequest;
import ec.uce.propuestas.plantilla.dto.PlantillaApuDetalleResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaApuEditarRequest;
import ec.uce.propuestas.plantilla.dto.PlantillaApuResumenResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.repository.PlantillaApuRepository;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Plan 04 (P-26) — Pruebas focalizadas del servicio de plantillas APU.
 * Patrón: {@code @QuarkusTest} + TRUNCATE en {@code @BeforeEach}.
 */
@QuarkusTest
class PlantillaApuServiceTest {

    @Inject
    PlantillaApuService plantillaApuService;

    @Inject
    ApuCrudService apuCrudService;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    SnapshotApuMapper snapshotApuMapper;

    @Inject
    PlantillaApuRepository plantillaApuRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // A. Listar / detalle / renombrar / eliminar — autorización
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_01_listar_devuelve_sistema_y_propias_y_filtra_ajenas() {
        Usuario alice = persistUsuario("alice@ex.com");
        Usuario bob = persistUsuario("bob@ex.com");
        persistPlantillaSistema("Catálogo sistema");
        PlantillaApu resumenAlice = persistPlantillaPersonal(alice.id, "Mis obras");

        List<PlantillaApuResumenResponse> deAlice = plantillaApuService.listar(alice.id, null);
        List<PlantillaApuResumenResponse> deBob = plantillaApuService.listar(bob.id, null);

        assertEquals(2, deAlice.size(), "Alice debe ver SISTEMA + su PERSONAL");
        assertTrue(deAlice.stream().anyMatch(p -> p.tipo() == PlantillaApu.Tipo.SISTEMA));
        assertTrue(deAlice.stream()
                .anyMatch(p -> p.tipo() == PlantillaApu.Tipo.PERSONAL && p.id().equals(resumenAlice.publicId)));

        assertEquals(1, deBob.size(), "Bob solo debe ver SISTEMA");
        assertEquals(PlantillaApu.Tipo.SISTEMA, deBob.get(0).tipo());
    }

    @Test
    @Transactional
    void TC_PL_02_detalle_personal_ajena_devuelve_404() {
        Usuario alice = persistUsuario("alice@ex.com");
        Usuario bob = persistUsuario("bob@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonal(alice.id, "Privada");

        ProblemaException ex =
                assertThrows(ProblemaException.class, () -> plantillaApuService.detalle(deAlice.publicId, bob.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PL_03_editar_sistema_devuelve_404() {
        PlantillaApu sistema = persistPlantillaSistema("Sistema");
        Usuario alice = persistUsuario("alice@ex.com");

        PlantillaApuEditarRequest req =
                new PlantillaApuEditarRequest(JsonNullable.of("Sistema renombrado"), JsonNullable.undefined());
        ProblemaException ex = assertThrows(
                ProblemaException.class, () -> plantillaApuService.editar(sistema.publicId, req, alice.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PL_04_editar_personal_ajena_devuelve_404() {
        Usuario alice = persistUsuario("alice@ex.com");
        Usuario bob = persistUsuario("bob@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonal(alice.id, "Solo Alice");

        PlantillaApuEditarRequest req =
                new PlantillaApuEditarRequest(JsonNullable.of("Tomada por Bob"), JsonNullable.undefined());
        ProblemaException ex =
                assertThrows(ProblemaException.class, () -> plantillaApuService.editar(deAlice.publicId, req, bob.id));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void TC_PL_05_editar_personal_propia_actualiza_nombre() {
        Usuario alice = persistUsuario("alice@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonal(alice.id, "Original");

        PlantillaApuEditarRequest req =
                new PlantillaApuEditarRequest(JsonNullable.of("Renombrada"), JsonNullable.of("desc"));
        PlantillaApuResumenResponse out = plantillaApuService.editar(deAlice.publicId, req, alice.id);

        assertEquals("Renombrada", out.nombre());
        assertEquals("desc", out.descripcionRubro());
    }

    @Test
    @Transactional
    void TC_PL_06_eliminar_sistema_devuelve_404() {
        PlantillaApu sistema = persistPlantillaSistema("Sistema");
        Usuario alice = persistUsuario("alice@ex.com");

        ProblemaException ex =
                assertThrows(ProblemaException.class, () -> plantillaApuService.eliminar(sistema.publicId, alice.id));
        assertEquals(404, ex.getResponse().getStatus());
        assertNotNull(plantillaApuRepository.findById(sistema.id));
    }

    @Test
    @Transactional
    void TC_PL_07_eliminar_personal_propia_borra_fila() {
        Usuario alice = persistUsuario("alice@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonal(alice.id, "Borrable");

        plantillaApuService.eliminar(deAlice.publicId, alice.id);
        assertNull(plantillaApuRepository.findById(deAlice.id), "Debe estar borrada");
    }

    @Test
    @Transactional
    void TC_PL_08_detalle_devuelve_snapshot_parseable() {
        Usuario alice = persistUsuario("alice@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonalConSnapshot(
                alice.id,
                "Con snapshot",
                "{\"secciones\":[{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-099\","
                        + "\"cantidad\":\"0.5\",\"rendimiento\":\"1.0\"}]}]}");

        PlantillaApuDetalleResponse det = plantillaApuService.detalle(deAlice.publicId, alice.id);
        assertNotNull(det.snapshotSecciones());
        assertTrue(det.snapshotSecciones().has("secciones"));
        assertEquals(2, det.snapshotSecciones().get("secciones").size());
    }

    // =========================================================================
    // B. Guardar desde APU — snapshot price-free
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_10_guardar_desde_apu_produce_snapshot_sin_precios() throws Exception {
        Usuario alice = persistUsuario("alice@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Consultorio");
        Long presupuestoId = insertarPresupuesto(proyecto.id);

        // Sembramos una base PROYECTO e insumo MO-001
        BaseInsumos base = persistBaseProyecto(proyecto.id);
        Insumo mo = persistInsumo(base.id, "MO-001", TipoInsumo.MANO_OBRA, "h", "4.75");

        // Creamos el APU origen con una fila MO y guardamos como plantilla
        ApuResponse apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-CONS", "Pozo", "m"));
        Apu apu = apuRepository.findById(internalId(apuResp.id()));
        ApuSeccion seccionMo =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();
        ApuDetalle d = new ApuDetalle();
        d.seccionId = seccionMo.id;
        d.insumoId = mo.id;
        d.descripcion = mo.descripcion;
        d.orden = 1;
        d.esHerramientaMenor = false;
        d.cantidad = new BigDecimal("1");
        d.rendimiento = new BigDecimal("0.5");
        d.unidad = "h";
        detalleRepository.persist(d);

        PlantillaApuResumenResponse resp = plantillaApuService.guardarDesdeApu(
                apu.id, new PlantillaApuCrearRequest("Plantilla Pozo", "desc opcional"), alice.id);
        assertNotNull(resp.id());
        assertEquals(PlantillaApu.Tipo.PERSONAL, resp.tipo());

        String snapshot = snapshotDePlantilla(resp.id());
        assertFalse(snapshot.contains("precioOverride"), "Sin override en el APU origen → sin override en snapshot");
        assertFalse(snapshot.contains("\"insumoId\""), "Snapshot no debe persistir insumoId");
        assertFalse(snapshot.contains("\"precioUnitarioTarifa\""), "Sin override en MATERIAL/TRANSPORTE");
        assertFalse(snapshot.contains("\"tarifaJornal\""), "Sin override en EQUIPO/MANO_OBRA");
        assertFalse(snapshot.contains("\"costo\""), "Sin costo calculado");
        assertFalse(snapshot.contains("\"apuId\""), "Sin link al APU origen");
        assertTrue(snapshot.contains("\"insumoCodigo\":\"MO-001\""), "Persiste el código");
        assertTrue(snapshot.contains("\"esHerramientaMenor\":true"), "HM persistido");
    }

    @Test
    @Transactional
    void TC_PL_11_guardar_desde_apu_preserva_hm_y_orden() throws Exception {
        Usuario alice = persistUsuario("alice@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Orden HM");
        Long presupuestoId = insertarPresupuesto(proyecto.id);
        BaseInsumos base = persistBaseProyecto(proyecto.id);
        Insumo mo1 = persistInsumo(base.id, "MO-1", TipoInsumo.MANO_OBRA, "h", "3.00");
        Insumo mo2 = persistInsumo(base.id, "MO-2", TipoInsumo.MANO_OBRA, "h", "3.50");

        ApuResponse apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-ORD", "Orden", "m"));
        Apu apu = apuRepository.findById(internalId(apuResp.id()));
        ApuSeccion equipo =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.EQUIPO).orElseThrow();
        ApuSeccion moSec =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();
        // HM ya existe (auto-creado por ApuCrudService.crear). Borramos la auto y creamos 2 filas MO con orden
        // explícito.
        for (ApuDetalle d : detalleRepository.listarDeSeccion(equipo.id)) {
            detalleRepository.delete(d);
        }
        ApuDetalle hm = new ApuDetalle();
        hm.seccionId = equipo.id;
        hm.descripcion = "Herramienta Menor";
        hm.orden = 1;
        hm.esHerramientaMenor = true;
        detalleRepository.persist(hm);

        ApuDetalle d1 = new ApuDetalle();
        d1.seccionId = moSec.id;
        d1.insumoId = mo1.id;
        d1.descripcion = "MO-1";
        d1.orden = 1;
        d1.esHerramientaMenor = false;
        d1.cantidad = BigDecimal.ONE;
        d1.rendimiento = new BigDecimal("0.1");
        d1.unidad = "h";
        detalleRepository.persist(d1);
        ApuDetalle d2 = new ApuDetalle();
        d2.seccionId = moSec.id;
        d2.insumoId = mo2.id;
        d2.descripcion = "MO-2";
        d2.orden = 2;
        d2.esHerramientaMenor = false;
        d2.cantidad = new BigDecimal("2");
        d2.rendimiento = new BigDecimal("0.2");
        d2.unidad = "h";
        detalleRepository.persist(d2);

        PlantillaApuResumenResponse resp =
                plantillaApuService.guardarDesdeApu(apu.id, new PlantillaApuCrearRequest("Con orden", null), alice.id);
        String snapshot = snapshotDePlantilla(resp.id());
        // Verificamos orden/estructura: HM en M orden 1, MO-1 orden 1, MO-2 orden 2
        assertTrue(snapshot.contains("\"tipo\":\"EQUIPO\""), "Sección EQUIPO presente");
        assertTrue(snapshot.contains("\"esHerramientaMenor\":true"));
        assertTrue(snapshot.contains("\"insumoCodigo\":\"MO-1\""));
        assertTrue(snapshot.contains("\"insumoCodigo\":\"MO-2\""));
    }

    // =========================================================================
    // D. Missing code → fila con insumoId=null, override 0, advertencia
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_20_faltante_crea_fila_pendiente_y_advertencia() throws Exception {
        Usuario alice = persistUsuario("alice@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Plan piloto");
        Long presupuestoId = insertarPresupuesto(proyecto.id);

        // Plantilla con un código que NO existe en ninguna base
        PlantillaApu plantilla = persistPlantillaPersonalConSnapshot(
                alice.id,
                "Con faltante",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-FALTA\","
                        + "\"cantidad\":\"1.0\",\"rendimiento\":\"0.5\"}]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
                        + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        ApuCrudService.ResultadoCrear r = apuCrudService.crear(
                presupuestoId, new ApuCrearRequest("APU-FALTA", "Faltante", "m", plantilla.publicId), alice.id);

        assertTrue(r.tieneAdvertencias(), "Debe traer advertencias");
        assertEquals(1, r.advertencias().size());
        AdvertenciaPlantillaResponse adv = r.advertencias().get(0);
        assertEquals("MO-FALTA", adv.insumoCodigo());
        assertEquals(AdvertenciaPlantillaResponse.MOTIVO_NO_EXISTE, adv.motivo());
        assertTrue(adv.mensaje().length() > 0, "Mensaje accionable");

        Apu cargado = apuRepository.findById(internalId(r.apu().id()));
        ApuDetalle filaFaltante = todosLosDetallesDe(cargado).stream()
                .filter(d -> "MO-FALTA".equals(d.descripcion))
                .findFirst()
                .orElseThrow();
        assertNull(filaFaltante.insumoId, "Fila pendiente → insumoId NULL");
        assertNotNull(filaFaltante.tarifaJornal, "Fila pendiente → override 0 en columna MO");
        assertEquals(0, filaFaltante.tarifaJornal.compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("1.0").compareTo(filaFaltante.cantidad));

        // El motor debe poder recalcular sin NPE
        ApuCalculado recalc = apuCalculoService.recalcular(cargado);
        assertNotNull(recalc);
        assertEquals(
                0, recalc.subtotalN().compareTo(BigDecimal.ZERO), "Subtotal N = 0 con fila pendiente (override 0)");
    }

    // =========================================================================
    // E. V004 unknown price fields tolerados e ignorados
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_30_v004_campos_extra_se_ignoran_en_lectura() {
        Usuario alice = persistUsuario("alice@ex.com");
        PlantillaApu deAlice = persistPlantillaPersonalConSnapshot(
                alice.id,
                "Estilo V004",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true,"
                        + "\"tarifaJornal\":\"99.99\",\"costo\":\"999\"}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-001\","
                        + "\"cantidad\":\"0.1\",\"rendimiento\":\"0.5\",\"precioOverride\":\"5.55\","
                        + "\"tarifaJornal\":\"5.55\",\"costo\":\"0.5\",\"orden\":2,\"descripcion\":\"heredado\","
                        + "\"seccionTipo\":\"MANO_OBRA\",\"tipoInsumo\":\"MANO_OBRA\","
                        + "\"publicId\":\"0192f6c4-7c8a-7000-8000-000000abcdef\"}]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        // El detalle no debe lanzar y debe exponer sólo los campos canónicos.
        PlantillaApuDetalleResponse det = plantillaApuService.detalle(deAlice.publicId, alice.id);
        assertNotNull(det.snapshotSecciones());

        // Las líneas no-HM deben estar bien parseadas, con los campos canónicos
        // preservados y los precios heredados del V004 IGNORADOS (snapshot
        // price-free — Plan 04 §1). El record SnapshotLinea ya no expone el
        // campo de precio; si el reader lo expusiera, este test fallaría en
        // compilación.
        List<SnapshotApuMapper.SnapshotLinea> lineas = snapshotApuMapper.lineasNoHm(deAlice.snapshotSecciones);
        assertEquals(1, lineas.size());
        SnapshotApuMapper.SnapshotLinea fila = lineas.get(0);
        assertEquals("MANO_OBRA", fila.seccionTipo());
        assertEquals("MO-001", fila.insumoCodigo());
        assertEquals(0, new BigDecimal("0.1").compareTo(fila.cantidad()));
        assertEquals(0, new BigDecimal("0.5").compareTo(fila.rendimiento()));
    }

    // =========================================================================
    // F. Snapshot preserva código de insumo (no IDs) + HM fallback coherente
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_40_guardar_desde_apu_persiste_codigo_de_insumo_no_id() throws Exception {
        // El snapshot guarda el código del insumo (resolución por codigo → bigint
        // → codigo de nuevo), nunca el bigint del insumo. Garantiza que la
        // plantilla sea portable entre bases PROYECTO.
        Usuario alice = persistUsuario("alice-cod@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Con código");
        Long presupuestoId = insertarPresupuesto(proyecto.id);
        BaseInsumos base = persistBaseProyecto(proyecto.id);
        Insumo mo = persistInsumo(base.id, "MO-COD", TipoInsumo.MANO_OBRA, "h", "3.21");

        ApuResponse apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-COD", "Codigo", "m"));
        Apu apu = apuRepository.findById(internalId(apuResp.id()));
        ApuSeccion moSec =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();
        ApuDetalle d = new ApuDetalle();
        d.seccionId = moSec.id;
        d.insumoId = mo.id;
        d.descripcion = mo.descripcion;
        d.orden = 1;
        d.esHerramientaMenor = false;
        d.cantidad = BigDecimal.ONE;
        d.rendimiento = BigDecimal.ONE;
        d.unidad = "h";
        detalleRepository.persist(d);

        PlantillaApuResumenResponse resp =
                plantillaApuService.guardarDesdeApu(apu.id, new PlantillaApuCrearRequest("Con código", null), alice.id);
        String snapshot = snapshotDePlantilla(resp.id());

        assertTrue(snapshot.contains("\"insumoCodigo\":\"MO-COD\""), "Persiste código de insumo");
        assertFalse(snapshot.contains("\"insumoId\""), "No persiste IDs");
    }

    @Test
    @Transactional
    void TC_PL_41_guardar_desde_apu_con_pendiente_persiste_codigo_pendiente() throws Exception {
        // Una fila pendiente (insumoId=null) tiene d.descripcion=insumoCodigo. El
        // guardado desde APU debe preservar ese código pendiente para que la
        // plantilla siga siendo accionable.
        Usuario alice = persistUsuario("alice-pen@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Pendiente");
        Long presupuestoId = insertarPresupuesto(proyecto.id);

        ApuResponse apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-PEN", "Pendiente", "m"));
        Apu apu = apuRepository.findById(internalId(apuResp.id()));
        ApuSeccion moSec =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();
        ApuDetalle d = new ApuDetalle();
        d.seccionId = moSec.id;
        d.insumoId = null;
        d.descripcion = "MO-PEND";
        d.orden = 1;
        d.esHerramientaMenor = false;
        d.cantidad = BigDecimal.ONE;
        d.rendimiento = BigDecimal.ONE;
        d.unidad = "h";
        d.tarifaJornal = BigDecimal.ZERO;
        detalleRepository.persist(d);

        PlantillaApuResumenResponse resp = plantillaApuService.guardarDesdeApu(
                apu.id, new PlantillaApuCrearRequest("Con pendiente", null), alice.id);
        String snapshot = snapshotDePlantilla(resp.id());
        assertTrue(snapshot.contains("\"insumoCodigo\":\"MO-PEND\""), "Preserva el código pendiente desde descripcion");
    }

    @Test
    @Transactional
    void TC_PL_42_aplicar_plantilla_sin_hm_inserta_hm_y_shiftea_orden() throws Exception {
        // Si el snapshot NO trae fila HM, la carga debe:
        //   1. Eliminar la fila HM auto-creada por el CRUD.
        //   2. Procesar las filas del snapshot en orden.
        //   3. Si tras procesar la sección EQUIPO quedó sin HM, hacer shift de
        //      las filas existentes +1 e insertar el HM auto-recreado en
        //      orden=1 para mantener orden único y contiguo.
        Usuario alice = persistUsuario("alice-fb@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "Fallback HM");
        Long presupuestoId = insertarPresupuesto(proyecto.id);
        BaseInsumos base = persistBaseProyecto(proyecto.id);
        Insumo eq = persistInsumo(base.id, "EQ-FB", TipoInsumo.EQUIPO, "h", "7.00");

        // Snapshot sin HM (sólo EQUIPO con 1 fila + MO vacío).
        PlantillaApu plantilla = persistPlantillaPersonalConSnapshot(
                alice.id,
                "Sin HM",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"insumoCodigo\":\"EQ-FB\","
                        + "\"cantidad\":\"1.0\",\"rendimiento\":\"1.0\"}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
                        + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        ApuCrudService.ResultadoCrear r = apuCrudService.crear(
                presupuestoId, new ApuCrearRequest("APU-FB", "Fallback", "m", plantilla.publicId), alice.id);

        Apu cargado = apuRepository.findById(internalId(r.apu().id()));
        ApuSeccion equipo =
                seccionRepository.findByApuYTipo(cargado.id, SeccionTipo.EQUIPO).orElseThrow();
        List<ApuDetalle> filasEquipo = detalleRepository.listarDeSeccion(equipo.id);

        // Exactamente una fila HM (auto-recreada) en orden 1.
        long hmCount = filasEquipo.stream().filter(x -> x.esHerramientaMenor).count();
        assertEquals(1, hmCount, "Debe haber exactamente una fila HM auto-recreada");
        ApuDetalle hmPersistida = filasEquipo.stream()
                .filter(x -> x.esHerramientaMenor)
                .findFirst()
                .orElseThrow();
        assertEquals(1, hmPersistida.orden.shortValue(), "HM auto-recreada en orden=1");

        // La fila EQUIPO no-HM se shifteó a orden=2 (no duplica orden=1 con HM).
        ApuDetalle eqPersistida = filasEquipo.stream()
                .filter(x -> !x.esHerramientaMenor)
                .findFirst()
                .orElseThrow();
        assertEquals(2, eqPersistida.orden.shortValue(), "Fila EQUIPO no-HM debe estar en orden=2 (shift por HM)");
        assertEquals(eq.id, eqPersistida.insumoId, "Insumo resuelto correctamente");
    }

    // =========================================================================
    // G. Recalcular con HM en orden no primero preserva costos de fila
    // =========================================================================

    @Test
    @Transactional
    void TC_PL_50_recalcular_con_hm_no_primero_preserva_costos_de_fila() throws Exception {
        // ApuCalculoService.recalcular() ya no asume que el HM está en orden 1.
        // Aquí construimos manualmente un APU con HM en orden=2 y verificamos
        // que el write-through escribe costo/costoHora correctos en cada fila,
        // sin mezclar el costo del HM con el de la fila EQUIPO adyacente.
        Usuario alice = persistUsuario("alice-hm2@ex.com");
        Proyecto proyecto = persistProyecto(alice.id, "HM no primero");
        Long presupuestoId = insertarPresupuesto(proyecto.id);
        BaseInsumos base = persistBaseProyecto(proyecto.id);
        Insumo mo = persistInsumo(base.id, "MO-HM2", TipoInsumo.MANO_OBRA, "h", "4.00");

        // Crear APU con HM auto-creado en orden=1 y 1 fila MO en orden=1 de MO.
        ApuResponse apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-HM2", "HM no primero", "m"));
        Apu apu = apuRepository.findById(internalId(apuResp.id()));
        ApuSeccion equipo =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.EQUIPO).orElseThrow();
        ApuSeccion moSec =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();

        // Fila MO en orden 1 de MO.
        ApuDetalle moRow = new ApuDetalle();
        moRow.seccionId = moSec.id;
        moRow.insumoId = mo.id;
        moRow.descripcion = "MO fila";
        moRow.orden = 1;
        moRow.esHerramientaMenor = false;
        moRow.cantidad = BigDecimal.ONE;
        moRow.rendimiento = BigDecimal.ONE;
        moRow.unidad = "h";
        detalleRepository.persist(moRow);

        // Reemplazar el HM auto-creado por [EQUIPO fila orden=1, HM orden=2]
        // para forzar el escenario "HM no primero".
        for (ApuDetalle d : detalleRepository.listarDeSeccion(equipo.id)) {
            detalleRepository.delete(d);
        }
        ApuDetalle equipoRow = new ApuDetalle();
        equipoRow.seccionId = equipo.id;
        equipoRow.insumoId = null; // pendiente
        equipoRow.descripcion = "EQ fila";
        equipoRow.orden = 1;
        equipoRow.esHerramientaMenor = false;
        equipoRow.cantidad = BigDecimal.ONE;
        equipoRow.rendimiento = BigDecimal.ONE;
        equipoRow.unidad = "h";
        equipoRow.tarifaJornal = BigDecimal.ZERO; // override 0 → costo 0
        detalleRepository.persist(equipoRow);
        ApuDetalle hm = new ApuDetalle();
        hm.seccionId = equipo.id;
        hm.descripcion = "Herramienta Menor";
        hm.orden = 2;
        hm.esHerramientaMenor = true;
        detalleRepository.persist(hm);

        // Forzar recálculo.
        ApuCalculado recalc = apuCalculoService.recalcular(apu);

        // La fila MO debe tener costo = 1 × 4 × 1 = 4 y costoHora = 1 × 4 = 4.
        // (Antes del fix, la fila MO recibía el costo del HM por la pareja
        // por índice paralelo.)
        ApuDetalle moPersistida = detalleRepository
                .find("seccionId = ?1 and esHerramientaMenor = false", moSec.id)
                .firstResult();
        assertEquals(
                0,
                new BigDecimal("4.000000").compareTo(moPersistida.costo),
                "MO fila: costo = 1 × 4 × 1 = 4 (no mezclado con HM)");
        assertEquals(
                0,
                new BigDecimal("4.000000").compareTo(moPersistida.costoHora),
                "MO fila: costoHora = 1 × 4 = 4 (no mezclado con HM)");

        // La fila EQUIPO pendiente debe tener costo 0 (override 0).
        ApuDetalle equipoPersistida = detalleRepository
                .find("seccionId = ?1 and esHerramientaMenor = false", equipo.id)
                .firstResult();
        assertEquals(
                0,
                equipoPersistida.costo.compareTo(BigDecimal.ZERO),
                "EQUIPO fila: costo = 1 × 0 × 1 = 0 (override 0)");

        // El HM debe tener costo = %HM × subtotalN = 0.05 × 4.0 = 0.2
        // (HM se evalúa después de MO en el motor, así que ya ve el subtotalN
        // calculado). Su descripción se regenera a "Herramienta Menor 5%MO".
        ApuDetalle hmPersistida = detalleRepository
                .find("seccionId = ?1 and esHerramientaMenor = true", equipo.id)
                .firstResult();
        assertEquals(
                0, new BigDecimal("0.200000").compareTo(hmPersistida.costo), "HM: costo = 0.05 × subtotalN(4.0) = 0.2");
        assertEquals("Herramienta Menor 5%MO", hmPersistida.descripcion, "Descripción HM regenerada por recalcular");

        // Sanity: el cálculo global es coherente.
        assertEquals(0, recalc.subtotalN().compareTo(new BigDecimal("4.0")));
        assertEquals(
                0,
                recalc.subtotalM().compareTo(new BigDecimal("0.2")),
                "subtotalM = HM(0.2) + EQUIPO pendiente(0) = 0.2");
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
        u.createdAt = Instant.now();
        u.updatedAt = Instant.now();
        usuarioRepository.persist(u);
        return u;
    }

    private Proyecto persistProyecto(Long duennoId, String nombre) {
        Proyecto p = new Proyecto();
        p.usuarioId = duennoId;
        p.nombreProyecto = nombre;
        p.codigo = "P-" + nombre.hashCode();
        p.descripcion = "Proyecto demo";
        p.anio = (short) 2026;
        p.plazoEjecucion = (short) 4;
        p.plazoUnidad = PlazoUnidad.MES;
        p.estado = EstadoProyecto.BORRADOR;
        p.direccionInstitucional = "UCE";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        proyectoRepository.persist(p);
        return p;
    }

    private BaseInsumos persistBaseProyecto(Long proyectoId) {
        BaseInsumos b = new BaseInsumos();
        b.nombre = "Insumos del proyecto " + proyectoId;
        b.tipo = TipoBase.PROYECTO;
        b.proyectoId = proyectoId;
        b.archivada = false;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
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
        insumo.createdAt = Instant.now();
        insumo.updatedAt = Instant.now();
        insumoRepository.persist(insumo);
        return insumo;
    }

    private PlantillaApu persistPlantillaSistema(String nombre) {
        PlantillaApu p = new PlantillaApu();
        p.nombre = nombre;
        p.tipo = PlantillaApu.Tipo.SISTEMA;
        p.usuarioId = null;
        p.snapshotSecciones = "{\"secciones\":[]}";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        plantillaApuRepository.persist(p);
        return p;
    }

    private PlantillaApu persistPlantillaPersonal(Long duennoId, String nombre) {
        PlantillaApu p = new PlantillaApu();
        p.nombre = nombre;
        p.tipo = PlantillaApu.Tipo.PERSONAL;
        p.usuarioId = duennoId;
        p.snapshotSecciones = "{\"secciones\":[]}";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        plantillaApuRepository.persist(p);
        return p;
    }

    private PlantillaApu persistPlantillaPersonalConSnapshot(Long duennoId, String nombre, String snapshot) {
        PlantillaApu p = new PlantillaApu();
        p.nombre = nombre;
        p.tipo = PlantillaApu.Tipo.PERSONAL;
        p.usuarioId = duennoId;
        p.snapshotSecciones = snapshot;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        plantillaApuRepository.persist(p);
        return p;
    }

    private Long insertarPresupuesto(Long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, 1, TRUE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long internalId(UUID publicId) {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String snapshotDePlantilla(UUID plantillaPublicId) {
        PlantillaApu p =
                plantillaApuRepository.find("publicId = ?1", plantillaPublicId).firstResult();
        return p.snapshotSecciones;
    }

    private List<ApuDetalle> todosLosDetallesDe(Apu apu) {
        List<ApuDetalle> todos = new java.util.ArrayList<>();
        for (ApuSeccion s : seccionRepository.listarDeApu(apu.id)) {
            todos.addAll(detalleRepository.listarDeSeccion(s.id));
        }
        return todos;
    }
}
