package ec.uce.propuestas.plantilla.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.plantilla.dto.ProyectoDesdePlantillaResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import ec.uce.propuestas.plantilla.repository.PlantillaProyectoRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.mapper.ProyectoMapper;
import ec.uce.propuestas.proyecto.repository.ParametrosProyectoRepository;
import ec.uce.propuestas.proyecto.repository.ParametrosSistemaRepository;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan 06 (P-46, N04 §A8) — Servicio de plantillas de proyecto completas.
 *
 * <p>Cubre el ciclo:
 * <ul>
 *   <li>Listar/detalle — owner-scoped via {@code publicId + usuarioId}.</li>
 *   <li>Guardar desde proyecto — snapshot estructural backend-authored
 *       (recursivo, sin precios, sin IDs, sin cronograma/firmantes/log/history;
 *       reuse Plan 04 price-free). Incluye la cabecera reutilizable
 *       (codigo, descripcion, anio, fechaInicio, plazoEjecucion,
 *       plazoUnidad, direccionInstitucional, subdireccionInstitucional,
 *       tituloEt1, tituloEt2) — excluye IDs, owner, estado, logo,
 *       {@code nombreProyecto} (autoritativo del request al aplicar),
 *       lineage y timestamps.</li>
 *   <li>Eliminar — owner-to-404 (RNF-05). El borrado de la plantilla NO toca
 *       proyectos ya creados desde ella (FK
 *       {@code proyecto.plantilla_proyecto_origen_id} con
 *       {@code ON DELETE SET NULL}; V001).</li>
 *   <li>Aplicar a un nuevo proyecto — un solo agregado en una sola transacción:
 *       Proyecto (BORRADOR) + parámetros + Presupuesto v1 vigente + base
 *       PROYECTO + árbol de capítulos recursivo + rubros/APUs y filas APU
 *       (cantidad = 0 pendiente, sin cronograma/firmantes/actividades/log). La
 *       resolución de insumos reutiliza el seam
 *       {@link ResolverInsumoPlantillaService} de P-26 (PROYECTO → CENTRAL →
 *       PERSONAL → pendiente con override 0).</li>
 * </ul>
 *
 * <p><b>Cabecera y defaults (Plan 06 §1, N04 §A8):</b> si el snapshot trae
 * {@code cabecera}, sus campos se aplican al proyecto nuevo. Si el snapshot
 * no trae cabecera (caso backward-compatible de los seeds V004 mínimos), el
 * servicio usa los defaults editables mínimos — anio actual, plazo 4 MES,
 * {@code direccionInstitucional} = "Pendiente de editar". El cliente edita el
 * resto después. Si el snapshot no trae {@code parametros}, los defaults del
 * singleton {@code ParametrosSistema} se usan para poblar todos los campos
 * no-nullables de {@code ParametrosProyecto} — sin esto, la fila creada
 * durante {@code aplicar} violaría los CHECK/NOT NULL de la tabla.
 *
 * <p><b>Autorización:</b> RNF-05 — todo acceso valida que la plantilla/proyecto
 * pertenezca al caller; cualquier fila ajena devuelve {@code 404 no-encontrado}
 * (no 403, para no filtrar existencia).
 */
@ApplicationScoped
public class PlantillaProyectoService {

    @Inject
    PlantillaProyectoRepository plantillaProyectoRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    ParametrosProyectoRepository parametrosProyectoRepository;

    @Inject
    ParametrosSistemaRepository parametrosSistemaRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository apuSeccionRepository;

    @Inject
    ApuDetalleRepository apuDetalleRepository;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    ResolverInsumoPlantillaService resolverInsumoPlantilla;

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    SnapshotProyectoMapper snapshotProyectoMapper;

    // =========================================================================
    // Listar / detalle — owner-scoped via publicId (UUIDv7)
    // =========================================================================

    /** Lista las plantillas del caller. No hay SISTEMA en este módulo (P-46 owner-only). */
    public List<PlantillaProyectoResponse> listar(Long callerUsuarioId) {
        return plantillaProyectoRepository.listarDeOwner(callerUsuarioId).stream()
                .map(p -> PlantillaProyectoResponse.from(p, snapshotProyectoMapper.parseJson(p.snapshotEstructura)))
                .toList();
    }

    public PlantillaProyectoResponse detalle(UUID plantillaPublicId, Long callerUsuarioId) {
        PlantillaProyecto p = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        return PlantillaProyectoResponse.from(p, snapshotProyectoMapper.parseJson(p.snapshotEstructura));
    }

    // =========================================================================
    // Eliminar — owner-to-404
    // =========================================================================

    /**
     * Elimina la plantilla del caller. No afecta a proyectos ya creados desde
     * ella: la FK {@code proyecto.plantilla_proyecto_origen_id} tiene
     * {@code ON DELETE SET NULL} (V001), así que la línea de auditoría queda
     * nula y los proyectos persisten intactos.
     */
    @Transactional
    public void eliminar(UUID plantillaPublicId, Long callerUsuarioId) {
        PlantillaProyecto p = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        plantillaProyectoRepository.delete(p);
    }

    // =========================================================================
    // Guardar desde proyecto — snapshot estructural backend-authored
    // =========================================================================

    /**
     * Crea una plantilla PERSONAL del caller a partir del proyecto
     * {@code proyectoId} (debe pertenecer al caller; RNF-05 → 404 si no).
     *
     * <p>El snapshot es estructural, recursivo y price-free:
     * <ul>
     *   <li>Incluye la cabecera reutilizable (codigo, descripcion, anio,
     *       fechaInicio, plazoEjecucion, plazoUnidad,
     *       direccionInstitucional, subdireccionInstitucional, tituloEt1,
     *       tituloEt2), parámetros del proyecto (si existen), títulos ET
     *       (legacy, conservados por compatibilidad), árbol recursivo de
     *       capítulos (subcapítulos + rubros con su APU estructural
     *       embebido).</li>
     *   <li>NO incluye: precios efectivos, overrides, IDs de base/proyecto/
     *       rubro/cronograma/firmantes/log/history, cantidades de obra
     *       (rubros.cantidad), cronograma, actividades, firmantes, logo,
     *       owner ({@code usuarioId}), estado, nombre del proyecto
     *       (autoritativo del request al aplicar), lineage ni timestamps.</li>
     *   <li>Preserva coeficientes estructurales de APU (cantidad/rendimiento
     *       de filas APU) — NO son cantidades de obra.</li>
     * </ul>
     *
     * <p>El cliente sólo envía {@code nombre} y {@code descripcion?} — el
     * snapshot lo construye el backend a partir del estado actual del
     * proyecto. No se acepta snapshot JSON del cliente.
     */
    @Transactional
    public PlantillaProyectoResponse guardarDesdeProyecto(
            UUID proyectoPublicId, String nombre, String descripcion, Long callerUsuarioId) {
        if (nombre == null || nombre.isBlank()) {
            throw ProblemaException.validacion("nombre es obligatorio");
        }
        String nombreTrim = nombre.trim();
        if (nombreTrim.length() > 200) {
            throw ProblemaException.validacion("nombre excede 200 caracteres");
        }
        String descripcionNorm = descripcion == null ? null : (descripcion.isBlank() ? null : descripcion.trim());

        Proyecto proyecto = proyectoRepository
                .findByPublicIdAndOwnerScope(proyectoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));

        SnapshotProyectoMapper.Snapshot snap = construirSnapshotDesdeProyecto(proyecto);

        PlantillaProyecto plantilla = new PlantillaProyecto();
        plantilla.usuarioId = callerUsuarioId;
        plantilla.nombre = nombreTrim;
        plantilla.descripcion = descripcionNorm;
        plantilla.snapshotEstructura = snapshotProyectoMapper.escribir(snap);
        plantillaProyectoRepository.persist(plantilla);
        plantillaProyectoRepository.getEntityManager().flush();

        return PlantillaProyectoResponse.from(
                plantilla, snapshotProyectoMapper.parseJson(plantilla.snapshotEstructura));
    }

    private SnapshotProyectoMapper.Snapshot construirSnapshotDesdeProyecto(Proyecto proyecto) {
        // Cabecera reutilizable — capturamos todos los campos estructurales del
        // proyecto que son seguros de replicar al aplicar. NO incluimos IDs,
        // owner, estado, logo, nombreProyecto (autoritativo del request), ni
        // lineage. Si el proyecto aún no tiene un campo rellenado, queda en
        // null y el servicio de aplicar decide si cae al default editable.
        SnapshotProyectoMapper.SnapshotCabecera snapCabecera = new SnapshotProyectoMapper.SnapshotCabecera(
                proyecto.codigo,
                proyecto.descripcion,
                proyecto.anio,
                proyecto.fechaInicio,
                proyecto.plazoEjecucion,
                proyecto.plazoUnidad == null ? null : proyecto.plazoUnidad.name(),
                proyecto.direccionInstitucional,
                proyecto.subdireccionInstitucional,
                proyecto.tituloEt1,
                proyecto.tituloEt2);

        // Parametros del proyecto (puede no existir aún → null en el snapshot;
        // aplicar usará los defaults del sistema).
        ParametrosProyecto params = parametrosProyectoRepository.findById(proyecto.id);
        SnapshotProyectoMapper.SnapshotParametros snapParams = params == null
                ? null
                : new SnapshotProyectoMapper.SnapshotParametros(
                        params.porcentajeHerramientaMenor,
                        params.porcentajeIndirecto,
                        params.iva,
                        params.moneda,
                        params.mostrarSeccionesVacias,
                        params.sufijosSeccionActivos,
                        params.mostrarSubtotalesSeccion,
                        params.mostrarSubtotalesPie,
                        params.mostrarNombreProyectoHeader,
                        params.enumerarApus,
                        params.mensajeFooter,
                        params.modoCodigoRubro == null ? null : params.modoCodigoRubro.name());

        // Títulos ET legacy — la cabecera canónica ya los lleva; este bloque
        // se conserva por compatibilidad con snapshots previos (Plan 06 §1).
        SnapshotProyectoMapper.SnapshotTitulos snapTitulos =
                new SnapshotProyectoMapper.SnapshotTitulos(proyecto.tituloEt1, proyecto.tituloEt2);

        // Árbol recursivo de capítulos del presupuesto v1 vigente (única
        // operativa tras guardar; P-31 versiones diferidas).
        Presupuesto vigente =
                presupuestoRepository.findVigenteDeProyecto(proyecto.id).orElse(null);
        List<SnapshotProyectoMapper.SnapshotCapitulo> caps =
                vigente == null ? List.of() : construirCapitulosRecursivos(vigente.id);

        return new SnapshotProyectoMapper.Snapshot(snapCabecera, snapParams, snapTitulos, caps);
    }

    private List<SnapshotProyectoMapper.SnapshotCapitulo> construirCapitulosRecursivos(Long presupuestoId) {
        // Cargamos todos los capítulos del presupuesto y los indexamos por item.
        List<CapituloConPadre> rows = cargarCapitulos(presupuestoId);
        Map<String, SnapshotProyectoMapper.SnapshotCapitulo> byItem = new java.util.LinkedHashMap<>();
        for (CapituloConPadre row : rows) {
            List<SnapshotProyectoMapper.SnapshotRubro> rubros = cargarRubros(row.id);
            SnapshotProyectoMapper.SnapshotCapitulo cap = new SnapshotProyectoMapper.SnapshotCapitulo(
                    row.item, row.descripcion, row.orden, row.parentItem, new ArrayList<>(), rubros);
            byItem.put(row.item, cap);
        }
        // Enlazamos hijos por parentItem.
        List<SnapshotProyectoMapper.SnapshotCapitulo> raices = new ArrayList<>();
        for (CapituloConPadre row : rows) {
            SnapshotProyectoMapper.SnapshotCapitulo cap = byItem.get(row.item);
            if (row.parentItem == null) {
                raices.add(cap);
            } else {
                SnapshotProyectoMapper.SnapshotCapitulo padre = byItem.get(row.parentItem);
                if (padre != null) {
                    padre.hijos().add(cap);
                } else {
                    raices.add(cap);
                }
            }
        }
        // Orden estable por item (lexicográfico respeta el orden jerárquico natural).
        raices.sort(Comparator.comparing(SnapshotProyectoMapper.SnapshotCapitulo::item));
        for (SnapshotProyectoMapper.SnapshotCapitulo cap : raices) {
            ordenarRecursivo(cap);
        }
        return raices;
    }

    private static void ordenarRecursivo(SnapshotProyectoMapper.SnapshotCapitulo cap) {
        cap.hijos().sort(Comparator.comparing(SnapshotProyectoMapper.SnapshotCapitulo::item));
        for (SnapshotProyectoMapper.SnapshotCapitulo h : cap.hijos()) {
            ordenarRecursivo(h);
        }
    }

    private List<CapituloConPadre> cargarCapitulos(Long presupuestoId) {
        // SQL nativo: capitulo/rubro no son entidades JPA en el árbol activo
        // (las relaciones capítulo/rubro se manipulan vía SQL; los seeds y los
        // resources CRUD usan el mismo patrón — ver
        // {@code PlantillaApuServiceTest#insertarPresupuesto}).
        @SuppressWarnings("unchecked")
        List<Object[]> rows = plantillaProyectoRepository
                .getEntityManager()
                .createNativeQuery("select c.id, c.item, c.descripcion, c.orden, "
                        + "(select p.item from capitulo p where p.id = c.parent_id) "
                        + "from capitulo c where c.presupuesto_id = :p order by c.item")
                .setParameter("p", presupuestoId)
                .getResultList();
        return rows.stream()
                .map(r -> new CapituloConPadre(
                        ((Number) r[0]).longValue(), (String) r[1], (String) r[2], ((Number) r[3]).intValue(), (String)
                                r[4]))
                .toList();
    }

    private List<SnapshotProyectoMapper.SnapshotRubro> cargarRubros(Long capituloId) {
        // Cada rubro referencia un APU (D-09 1:1). El snapshot captura los
        // coeficientes estructurales del APU (cantidad/rendimiento de filas
        // APU) — NO la cantidad de obra del rubro.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = plantillaProyectoRepository
                .getEntityManager()
                .createNativeQuery("select r.id, r.item, r.codigo, r.descripcion, r.unidad, "
                        + "a.id, a.codigo, a.descripcion, a.unidad "
                        + "from rubro r join apu a on a.id = r.apu_id "
                        + "where r.capitulo_id = :c order by r.item")
                .setParameter("c", capituloId)
                .getResultList();
        List<SnapshotProyectoMapper.SnapshotRubro> out = new ArrayList<>();
        for (Object[] r : rows) {
            Long apuId = (Long) r[5];
            SnapshotProyectoMapper.SnapshotApuEstructural apu = new SnapshotProyectoMapper.SnapshotApuEstructural(
                    (String) r[6], (String) r[7], (String) r[8], cargarFilasApu(apuId));
            out.add(new SnapshotProyectoMapper.SnapshotRubro(
                    (String) r[1], (String) r[2], (String) r[3], (String) r[4], apu));
        }
        return out;
    }

    private List<SnapshotProyectoMapper.SnapshotFilaApu> cargarFilasApu(Long apuId) {
        List<ApuSeccion> secciones = apuSeccionRepository.listarDeApu(apuId);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));
        List<SnapshotProyectoMapper.SnapshotFilaApu> out = new ArrayList<>();
        for (ApuSeccion sec : secciones) {
            List<ApuDetalle> detalles = apuDetalleRepository.listarDeSeccion(sec.id);
            for (ApuDetalle d : detalles) {
                String insumoCodigo = null;
                if (d.esHerramientaMenor) {
                    out.add(new SnapshotProyectoMapper.SnapshotFilaApu(sec.tipo.name(), true, null, null, null));
                    continue;
                }
                if (d.insumoId != null) {
                    Insumo insumo =
                            plantillaProyectoRepository.getEntityManager().find(Insumo.class, d.insumoId);
                    insumoCodigo = insumo == null ? d.descripcion : insumo.codigo;
                } else {
                    insumoCodigo = d.descripcion;
                }
                out.add(new SnapshotProyectoMapper.SnapshotFilaApu(
                        sec.tipo.name(), false, insumoCodigo, d.cantidad, d.rendimiento));
            }
        }
        return out;
    }

    private record CapituloConPadre(Long id, String item, String descripcion, int orden, String parentItem) {}

    // =========================================================================
    // Aplicar plantilla → nuevo proyecto (operación monolítica transaccional)
    // =========================================================================

    /**
     * Crea un nuevo proyecto del {@code callerUsuarioId} a partir de la
     * plantilla {@code plantillaPublicId}. El cliente sólo envía el
     * {@code nombreProyecto}; el resto lo construye el backend a partir del
     * snapshot estructural.
     *
     * <p>La operación es atómica: cualquier error intermedio deshace TODO el
     * agregado. NO se crean cronograma, firmantes, actividades, ni log
     * (PlantillaProyectoService no los modela — Plan 06 §1 "operational data").
     *
     * <p>Resultado:
     * <ol>
     *   <li>{@code Proyecto} BORRADOR (nuevo, ID y publicId distintos del
     *       origen), con {@code plantilla_proyecto_origen_id} para lineage. El
     *       {@code nombreProyecto} lo fija el cliente (request), NUNCA el
     *       snapshot — el snapshot puede traer codigo/descripcion/anio/etc.
     *       en {@code cabecera} pero no el nombre.</li>
     *   <li>Cabecera del proyecto: si el snapshot trae {@code cabecera}, se
     *       aplican sus campos (codigo, descripcion, anio, fechaInicio,
     *       plazoEjecucion, plazoUnidad, direccionInstitucional,
     *       subdireccionInstitucional, tituloEt1, tituloEt2). Si falta
     *       cabecera (caso backward-compatible del seed V004), se usan los
     *       defaults editables mínimos — anio actual, plazo 4 MES,
     *       direccionInstitucional = "Pendiente de editar".</li>
     *   <li>{@code ParametrosProyecto} poblado primero con los defaults del
     *       singleton {@code ParametrosSistema} (todos los campos
     *       no-nullables), y luego con el overlay del snapshot si
     *       {@code parametros} está presente. Esto hace válida la fila
     *       incluso cuando el snapshot carece de parámetros (V004 mínimos).</li>
     *   <li>Una base {@code BaseInsumos} PROYECTO del nuevo proyecto (vacía —
     *       los insumos se materializan al cargar APUs).</li>
     *   <li>Una versión de {@code Presupuesto} v1 vigente (NOTAS = "Plantilla …").</li>
     *   <li>Árbol recursivo de capítulos + rubros/APUs y filas APU
     *       reconstruidos. Los rubros arrancan con {@code cantidad = 0.000000}
     *       (pendiente; D-09 1:1) — V007 explícitamente permite
     *       {@code cantidad >= 0}, así que el INSERT nativo no bypasea
     *       ninguna CHECK: la fila entra bajo el dominio válido y el usuario
     *       la edita después a un valor {@code > 0}.</li>
     *   <li>APUs con sus 4 secciones canónicas y filas (HM + coeficientes).
     *       Resolución de insumos vía el seam P-26
     *       ({@link ResolverInsumoPlantillaService}): PROYECTO → CENTRAL →
     *       PERSONAL → pendiente con override 0. Las advertencias se
     *       acumulan y se devuelven.</li>
     * </ol>
     *
     * <p>Rollback: la operación completa vive dentro de un solo
     * {@code @Transactional} — un fallo intermedio deshace TODO. Las
     * asignaciones de FK entre entidades del agregado usan sólo BIGINTs
     * internos nuevos, así que no hay riesgo de "medio agregado" visible.
     *
     * <p>Owner mismatch → 404 (RNF-05).
     */
    @Transactional
    public ProyectoDesdePlantillaResponse aplicar(UUID plantillaPublicId, String nombreProyecto, Long callerUsuarioId) {
        if (nombreProyecto == null || nombreProyecto.isBlank()) {
            throw ProblemaException.validacion("nombre es obligatorio");
        }
        if (nombreProyecto.length() > 200) {
            throw ProblemaException.validacion("nombre excede 200 caracteres");
        }
        PlantillaProyecto plantilla = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        SnapshotProyectoMapper.Snapshot snap = snapshotProyectoMapper.leer(plantilla.snapshotEstructura);

        // 1) Proyecto nuevo BORRADOR (cabecera del snapshot + nombre del request).
        SnapshotProyectoMapper.SnapshotCabecera cab = snap.cabecera();
        Proyecto nuevo = new Proyecto();
        nuevo.usuarioId = callerUsuarioId;
        nuevo.nombreProyecto = nombreProyecto.trim();
        // Cabecera: si está presente, replica los campos seguros del snapshot;
        // si falta (V004 mínimo), cae a los defaults editables.
        nuevo.codigo = cab == null ? null : cab.codigo();
        nuevo.descripcion = cab == null ? null : cab.descripcion();
        nuevo.anio = cab != null && cab.anio() != null
                ? cab.anio()
                : (short) LocalDate.now().getYear();
        nuevo.fechaInicio = cab == null ? null : cab.fechaInicio();
        nuevo.plazoEjecucion = cab != null && cab.plazoEjecucion() != null ? cab.plazoEjecucion() : (short) 4;
        nuevo.plazoUnidad = cab != null && cab.plazoUnidad() != null
                ? PlazoUnidad.valueOf(cab.plazoUnidad().toUpperCase())
                : PlazoUnidad.MES;
        nuevo.direccionInstitucional = cab != null && cab.direccionInstitucional() != null
                ? cab.direccionInstitucional()
                : "Pendiente de editar";
        nuevo.subdireccionInstitucional = cab == null ? null : cab.subdireccionInstitucional();
        nuevo.tituloEt1 = cab == null ? null : cab.tituloEt1();
        nuevo.tituloEt2 = cab == null ? null : cab.tituloEt2();
        nuevo.estado = EstadoProyecto.BORRADOR;
        nuevo.plantillaProyectoOrigenId = plantilla.id;
        proyectoRepository.persist(nuevo);
        proyectoRepository.getEntityManager().flush();

        // 2) Parámetros del proyecto: defaults del sistema + overlay del snapshot.
        aplicarParametros(nuevo.id, snap.parametros());

        // 3) Base PROYECTO del nuevo proyecto (vacía — los insumos se copian al cargar APUs).
        baseInsumosService.asegurarBaseProyecto(nuevo.id);

        // 4) Presupuesto v1 vigente (única versión operativa al aplicar).
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.proyectoId = nuevo.id;
        presupuesto.version = (short) 1;
        presupuesto.esVigente = true;
        presupuesto.total = BigDecimal.ZERO;
        presupuesto.notas = "Plantilla '" + plantilla.nombre + "' aplicada";
        presupuestoRepository.persist(presupuesto);
        presupuestoRepository.getEntityManager().flush();

        // 5) Reconstruir árbol de capítulos + rubros/APUs y filas APU.
        List<AdvertenciaPlantillaResponse> advertencias = new ArrayList<>();
        if (snap.capitulos() != null && !snap.capitulos().isEmpty()) {
            for (SnapshotProyectoMapper.SnapshotCapitulo cap : snap.capitulos()) {
                reconstruirCapitulosRecursivos(cap, null, presupuesto.id, nuevo.id, advertencias);
            }
        }

        // 6) Recalcular write-through de los APUs creados (totales, subtotales de sección).
        for (Apu apu : apuCreadosEnTransaccion(nuevo.id)) {
            apuCalculoService.recalcular(apu);
        }

        // 7) Asegurar que el response se construye con el proyecto persistido.
        ProyectoResponse resp = ProyectoMapper.toResponse(nuevo);
        return new ProyectoDesdePlantillaResponse(resp, List.copyOf(advertencias));
    }

    /**
     * Puebla {@code ParametrosProyecto} con los defaults del singleton
     * {@code ParametrosSistema} (todos los campos no-nullables) y aplica el
     * overlay de los valores del snapshot cuando están presentes. Esto
     * garantiza que la fila resultante satisfaga los CHECK/NOT NULL de la
     * tabla, incluso cuando el snapshot carece del bloque {@code parametros}
     * (caso backward-compatible del seed V004 mínimo).
     */
    private void aplicarParametros(Long proyectoId, SnapshotProyectoMapper.SnapshotParametros snapParams) {
        ParametrosProyecto p = parametrosProyectoRepository.findById(proyectoId);
        if (p == null) {
            p = new ParametrosProyecto();
            p.proyectoId = proyectoId;
        }
        ParametrosSistema sistema = parametrosSistemaRepository.findById((short) 1);
        if (sistema != null) {
            // Defaults del sistema — fuente autoritativa de los defaults
            // (idéntica a ParametrosProyectoService#obtenerOCrear).
            p.porcentajeHerramientaMenor = sistema.porcentajeHerramientaMenor;
            p.porcentajeIndirecto = sistema.porcentajeIndirecto;
            p.iva = sistema.iva;
            p.moneda = sistema.moneda;
            p.mostrarSeccionesVacias = sistema.mostrarSeccionesVacias;
            p.sufijosSeccionActivos = sistema.sufijosSeccionActivos;
            p.mostrarSubtotalesSeccion = sistema.mostrarSubtotalesSeccion;
            p.mostrarSubtotalesPie = sistema.mostrarSubtotalesPie;
            p.mostrarNombreProyectoHeader = sistema.mostrarNombreProyectoHeader;
            p.enumerarApus = sistema.enumerarApus;
            p.mensajeFooter = sistema.mensajeFooter;
            p.modoCodigoRubro = sistema.modoCodigoRubro;
        }
        // Overlay del snapshot — los valores presentes en el snapshot ganan
        // sobre los defaults del sistema (no nulos).
        if (snapParams != null) {
            if (snapParams.porcentajeHerramientaMenor() != null) {
                p.porcentajeHerramientaMenor = snapParams.porcentajeHerramientaMenor();
            }
            if (snapParams.porcentajeIndirecto() != null) {
                p.porcentajeIndirecto = snapParams.porcentajeIndirecto();
            }
            if (snapParams.iva() != null) p.iva = snapParams.iva();
            if (snapParams.moneda() != null) p.moneda = snapParams.moneda();
            if (snapParams.mostrarSeccionesVacias() != null)
                p.mostrarSeccionesVacias = snapParams.mostrarSeccionesVacias();
            if (snapParams.sufijosSeccionActivos() != null)
                p.sufijosSeccionActivos = snapParams.sufijosSeccionActivos();
            if (snapParams.mostrarSubtotalesSeccion() != null)
                p.mostrarSubtotalesSeccion = snapParams.mostrarSubtotalesSeccion();
            if (snapParams.mostrarSubtotalesPie() != null) p.mostrarSubtotalesPie = snapParams.mostrarSubtotalesPie();
            if (snapParams.mostrarNombreProyectoHeader() != null) {
                p.mostrarNombreProyectoHeader = snapParams.mostrarNombreProyectoHeader();
            }
            if (snapParams.enumerarApus() != null) p.enumerarApus = snapParams.enumerarApus();
            if (snapParams.mensajeFooter() != null) p.mensajeFooter = snapParams.mensajeFooter();
            if (snapParams.modoCodigoRubro() != null) {
                p.modoCodigoRubro =
                        ModoCodigoRubro.valueOf(snapParams.modoCodigoRubro().toUpperCase());
            }
        }
        parametrosProyectoRepository.persist(p);
    }

    private void reconstruirCapitulosRecursivos(
            SnapshotProyectoMapper.SnapshotCapitulo snap,
            Long parentCapituloId,
            Long presupuestoId,
            Long proyectoId,
            List<AdvertenciaPlantillaResponse> advertencias) {
        Long capituloId = insertarCapitulo(snap, parentCapituloId, presupuestoId);
        // Rubros con sus APUs y filas.
        if (snap.rubros() != null) {
            for (SnapshotProyectoMapper.SnapshotRubro r : snap.rubros()) {
                if (r.apu() == null || r.apu().codigo() == null) continue;
                Apu apu = crearApuEstructural(r.apu(), presupuestoId, proyectoId, advertencias);
                insertarRubro(r, apu.id, capituloId);
            }
        }
        // Hijos recursivos.
        if (snap.hijos() != null) {
            for (SnapshotProyectoMapper.SnapshotCapitulo h : snap.hijos()) {
                reconstruirCapitulosRecursivos(h, capituloId, presupuestoId, proyectoId, advertencias);
            }
        }
    }

    private Long insertarCapitulo(SnapshotProyectoMapper.SnapshotCapitulo snap, Long parentId, Long presupuestoId) {
        return insertarCapituloNativo(presupuestoId, parentId, snap.item(), snap.descripcion(), snap.orden());
    }

    /**
     * Inserta un capítulo vía SQL nativo (no hay entity JPA para Capitulo en
     * el árbol activo; reutilizamos el patrón de
     * {@code PlantillaApuServiceTest#insertarPresupuesto} de los IT existentes).
     * Mantenemos la invariante {@code total = 0} (write-through se recalcula
     * cuando haya rubros + APUs).
     */
    private Long insertarCapituloNativo(Long presupuestoId, Long parentId, String item, String descripcion, int orden) {
        Object result = plantillaProyectoRepository
                .getEntityManager()
                .createNativeQuery("INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                        + "VALUES (:presupuestoId, :parentId, :item, :descripcion, :orden, 0) RETURNING id")
                .setParameter("presupuestoId", presupuestoId)
                .setParameter("parentId", parentId)
                .setParameter("item", item)
                .setParameter("descripcion", descripcion)
                .setParameter("orden", orden)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    private Apu crearApuEstructural(
            SnapshotProyectoMapper.SnapshotApuEstructural snap,
            Long presupuestoId,
            Long proyectoId,
            List<AdvertenciaPlantillaResponse> advertencias) {
        // Cabecera mínima del APU — código y descripción estructural.
        Apu apu = new Apu();
        apu.presupuestoId = presupuestoId;
        apu.codigo = snap.codigo();
        apu.descripcion = snap.descripcion() == null ? "(sin descripción)" : snap.descripcion();
        apu.unidad = snap.unidad() == null ? "u" : snap.unidad();
        apuRepository.persist(apu);
        apuRepository.getEntityManager().flush();

        // 4 secciones canónicas en orden M/N/O/P + HM auto-creado en M.
        crearSeccionesCanónicas(apu);

        if (snap.filas() != null && !snap.filas().isEmpty()) {
            // Mapa sección → entidad persistida.
            Map<SeccionTipo, ApuSeccion> secMap = new EnumMap<>(SeccionTipo.class);
            for (SeccionTipo t : SeccionTipo.values()) {
                apuSeccionRepository.findByApuYTipo(apu.id, t).ifPresent(s -> secMap.put(t, s));
            }
            ApuSeccion equipo = secMap.get(SeccionTipo.EQUIPO);

            // Borrar HM auto-creado — el snapshot lo recreará si trae HM.
            if (equipo != null) {
                for (ApuDetalle d : apuDetalleRepository.listarDeSeccion(equipo.id)) {
                    if (d.esHerramientaMenor) {
                        apuDetalleRepository.delete(d);
                    }
                }
            }

            // Insertar filas en orden preservado.
            for (SnapshotProyectoMapper.SnapshotFilaApu f : snap.filas()) {
                SeccionTipo tipo;
                try {
                    tipo = SeccionTipo.valueOf(f.seccionTipo());
                } catch (IllegalArgumentException | NullPointerException e) {
                    continue;
                }
                ApuSeccion seccion = secMap.get(tipo);
                if (seccion == null) continue;
                insertarFilaEstructural(f, tipo, seccion, proyectoId, advertencias);
            }

            // Si el snapshot no trae HM, shift +1 + HM auto-recreado en orden=1
            // (mismo patrón que Plan 04 §6).
            if (equipo != null
                    && apuDetalleRepository.listarDeSeccion(equipo.id).stream().noneMatch(x -> x.esHerramientaMenor)) {
                List<ApuDetalle> equipoActuales = apuDetalleRepository.listarDeSeccion(equipo.id);
                for (ApuDetalle d : equipoActuales) {
                    d.orden = (short) (d.orden + 1);
                    apuDetalleRepository.persist(d);
                }
                ApuDetalle hm = new ApuDetalle();
                hm.seccionId = equipo.id;
                hm.descripcion = "Herramienta Menor";
                hm.orden = 1;
                hm.esHerramientaMenor = true;
                apuDetalleRepository.persist(hm);
            }
        }

        return apu;
    }

    private void crearSeccionesCanónicas(Apu apu) {
        short orden = 1;
        for (SeccionTipo tipo :
                List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE)) {
            ApuSeccion s = new ApuSeccion();
            s.apuId = apu.id;
            s.tipo = tipo;
            s.orden = orden++;
            apuSeccionRepository.persist(s);
        }
        // HM auto-creado en M (lo borramos/recreamos según el snapshot).
        ApuSeccion equipo =
                apuSeccionRepository.findByApuYTipo(apu.id, SeccionTipo.EQUIPO).orElseThrow();
        ApuDetalle hm = new ApuDetalle();
        hm.seccionId = equipo.id;
        hm.descripcion = "Herramienta Menor";
        hm.orden = 1;
        hm.esHerramientaMenor = true;
        apuDetalleRepository.persist(hm);
    }

    private void insertarFilaEstructural(
            SnapshotProyectoMapper.SnapshotFilaApu f,
            SeccionTipo tipo,
            ApuSeccion seccion,
            Long proyectoId,
            List<AdvertenciaPlantillaResponse> advertencias) {
        ApuDetalle d = new ApuDetalle();
        d.seccionId = seccion.id;
        if (f.esHerramientaMenor()) {
            d.esHerramientaMenor = true;
            d.descripcion = "Herramienta Menor";
            // HM: sin insumo, sin override, sin rendimiento.
        } else {
            d.esHerramientaMenor = false;
            d.cantidad = f.cantidad() != null ? f.cantidad() : BigDecimal.ONE;
            d.rendimiento = (tipo == SeccionTipo.EQUIPO || tipo == SeccionTipo.MANO_OBRA)
                    ? (f.rendimiento() != null ? f.rendimiento() : BigDecimal.ONE)
                    : null;
            ResolverInsumoPlantillaService.Resultado r =
                    resolverInsumoPlantilla.resolver(f.insumoCodigo(), tipo, proyectoId);
            if (r.insumoProyecto().isPresent()) {
                Insumo insumo = r.insumoProyecto().get();
                d.insumoId = insumo.id;
                d.descripcion = insumo.descripcion;
                d.unidad = switch (tipo) {
                    case EQUIPO, MANO_OBRA -> "h";
                    case MATERIAL, TRANSPORTE -> insumo.unidad;
                };
            } else {
                d.insumoId = null;
                d.descripcion = f.insumoCodigo() != null ? f.insumoCodigo() : "(código ausente)";
                d.unidad = switch (tipo) {
                    case EQUIPO, MANO_OBRA -> "h";
                    case MATERIAL, TRANSPORTE -> "u";
                };
                // Override 0 según P-26 + V005.
                switch (tipo) {
                    case EQUIPO, MANO_OBRA -> d.tarifaJornal = BigDecimal.ZERO;
                    case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa = BigDecimal.ZERO;
                }
                if (r.advertencia() != null) {
                    advertencias.add(r.advertencia());
                }
            }
        }
        // orden: persistimos en el orden que llega; el motor lo respeta en lectura.
        d.orden = (short) (apuDetalleRepository.maxOrdenEnSeccion(seccion.id) + 1);
        apuDetalleRepository.persist(d);
    }

    private void insertarRubro(SnapshotProyectoMapper.SnapshotRubro snap, Long apuId, Long capituloId) {
        // V007 (migración aplicada) relaja el CHECK de `rubro.cantidad` de
        // `> 0` a `>= 0`. INSERT inicial con `cantidad = 0` (pendiente; D-09
        // 1:1) entra bajo el dominio válido — el usuario rellena la cantidad
        // al editar. La columna sigue NOT NULL (no se hace nullable); V007
        // NO bypasea ningún CHECK.
        insertarRubroNativo(capituloId, apuId, snap.item(), snap.codigo(), snap.descripcion(), snap.unidad());
    }

    private void insertarRubroNativo(
            Long capituloId, Long apuId, String item, String codigo, String descripcion, String unidad) {
        plantillaProyectoRepository
                .getEntityManager()
                .createNativeQuery("INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                        + "cantidad, precio_unitario, precio_total) "
                        + "VALUES (:capituloId, :apuId, :item, :codigo, :descripcion, :unidad, 0, 0, 0)")
                .setParameter("capituloId", capituloId)
                .setParameter("apuId", apuId)
                .setParameter("item", item)
                .setParameter("codigo", codigo)
                .setParameter("descripcion", descripcion)
                .setParameter("unidad", unidad == null ? "u" : unidad)
                .executeUpdate();
    }

    /**
     * Lista los APUs del presupuesto v1 del nuevo proyecto — los creados en
     * esta transacción. Se usa para forzar el write-through de
     * {@link ApuCalculoService#recalcular}.
     */
    private List<Apu> apuCreadosEnTransaccion(Long proyectoId) {
        return plantillaProyectoRepository
                .getEntityManager()
                .createQuery(
                        "select a from Apu a where a.presupuestoId in "
                                + "(select p.id from Presupuesto p where p.proyectoId = :p and p.version = 1) "
                                + "order by a.id",
                        Apu.class)
                .setParameter("p", proyectoId)
                .getResultList();
    }

    // =========================================================================
    // Lookup owner-scoped (RNF-05)
    // =========================================================================

    private PlantillaProyecto cargarPorOwner(UUID plantillaPublicId, Long callerUsuarioId) {
        if (plantillaPublicId == null) {
            throw ProblemaException.noEncontrado("Plantilla de proyecto no encontrada");
        }
        Optional<PlantillaProyecto> row =
                plantillaProyectoRepository.findByPublicIdAndOwnerScope(plantillaPublicId, callerUsuarioId);
        if (row.isEmpty()) {
            throw ProblemaException.noEncontrado("Plantilla de proyecto no encontrada");
        }
        return row.get();
    }
}
