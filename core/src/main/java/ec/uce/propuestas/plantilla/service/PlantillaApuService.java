package ec.uce.propuestas.plantilla.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaApuCrearRequest;
import ec.uce.propuestas.plantilla.dto.PlantillaApuDetalleResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaApuEditarRequest;
import ec.uce.propuestas.plantilla.dto.PlantillaApuResumenResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.repository.PlantillaApuRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan 04 (P-26) — Servicio de plantillas APU. Cubre el ciclo:
 * listar/guardar/renombrar/eliminar/cargar. El snapshot JSONB se persiste
 * sin precios (writer price-free, DM §12) y se lee de forma tolerante
 * (campos extra del seed V004 se ignoran — Plan 04 §1, §N04 §B.4). La
 * autorización se aplica a nivel de servicio siguiendo el seam de
 * {@link PlantillaApuRepository#findByPublicIdAndOwnerScope}.
 *
 * <p><b>Reglas de visibilidad (Plan 04 §3):</b>
 * <ul>
 *   <li>{@code SISTEMA} — visible a todos los usuarios autenticados; <b>no
 *       editable, no eliminable</b> desde el seam USUARIO. PUT/DELETE sobre
 *       SISTEMA devuelven 404 (RNF-05 — no se filtra la existencia).</li>
 *   <li>{@code PERSONAL} — visible y editable sólo por su dueño. PUT/DELETE
 *       de una PERSONAL ajena devuelven 404.</li>
 * </ul>
 *
 * <p>La seam de carga (aplicar snapshot a un APU recién creado) es invocada
 * por {@code ApuCrudService.crear} cuando el body trae {@code plantillaId}.
 * La dependencia es unidireccional — {@code ApuCrudService} →
 * {@code PlantillaApuService} — para evitar ciclo.
 */
@ApplicationScoped
public class PlantillaApuService {

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
    SnapshotApuMapper snapshotApuMapper;

    @Inject
    ResolverInsumoPlantillaService resolverInsumoPlantilla;

    // =========================================================================
    // Listar (SISTEMA + propias)
    // =========================================================================

    /**
     * Lista plantillas visibles: SISTEMA + PERSONAL del caller. Orden estable
     * (SISTEMA primero, luego propias por nombre). El campo {@code q} aplica
     * filtro case-insensitive sobre {@code nombre} y {@code descripcionRubro}.
     */
    public List<PlantillaApuResumenResponse> listar(Long callerUsuarioId, String q) {
        List<PlantillaApu> sistema = plantillaApuRepository.listarPorTipo(PlantillaApu.Tipo.SISTEMA);
        List<PlantillaApu> propias =
                plantillaApuRepository.listarPorTipoYDuenno(PlantillaApu.Tipo.PERSONAL, callerUsuarioId);
        List<PlantillaApu> todas = new ArrayList<>();
        todas.addAll(sistema);
        todas.addAll(propias);
        if (q != null && !q.isBlank()) {
            String qLower = q.toLowerCase();
            todas.removeIf(p -> {
                String n = p.nombre == null ? "" : p.nombre.toLowerCase();
                String d = p.descripcionRubro == null ? "" : p.descripcionRubro.toLowerCase();
                return !n.contains(qLower) && !d.contains(qLower);
            });
        }
        return todas.stream().map(PlantillaApuResumenResponse::from).toList();
    }

    /** Plan 001 — PostgreSQL FTS search with stable pagination and owner scope. */
    public Page<PlantillaApuResumenResponse> buscar(
            Long callerUsuarioId, List<PlantillaApu.Tipo> tipos, String q, int page, int size) {
        List<PlantillaApuResumenResponse> items =
                plantillaApuRepository.buscar(callerUsuarioId, tipos, q, page, size).stream()
                        .map(PlantillaApuResumenResponse::from)
                        .toList();
        long total = plantillaApuRepository.contarBusqueda(callerUsuarioId, tipos, q);
        return Page.of(items, total, page, size);
    }

    // =========================================================================
    // Detalle (lee snapshot y tolera campos extra del seed V004)
    // =========================================================================

    /**
     * Devuelve el detalle de una plantilla respetando la autorización. Una
     * plantilla ajena PERSONAL o una inexistente devuelven 404 (RNF-05).
     */
    public PlantillaApuDetalleResponse detalle(UUID plantillaPublicId, Long callerUsuarioId) {
        PlantillaApu p = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        return PlantillaApuDetalleResponse.from(p, snapshotApuMapper.parseJson(p.snapshotSecciones), List.of());
    }

    // =========================================================================
    // Renombrar (PUT /plantillas-apu/{id})
    // =========================================================================

    /**
     * Edita únicamente metadatos (nombre, descripcionRubro) — Plan 04 §7.
     * Devuelve 404 si la plantilla es ajena o es SISTEMA (SISTEMA es
     * read-only para usuarios; editar desde el seam USUARIO oculta la fila
     * — no se filtra existencia).
     */
    @Transactional
    public PlantillaApuResumenResponse editar(
            UUID plantillaPublicId, PlantillaApuEditarRequest req, Long callerUsuarioId) {
        if (req != null) req.validar();
        PlantillaApu p = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        if (p.tipo == PlantillaApu.Tipo.SISTEMA) {
            throw ProblemaException.noEncontrado("Plantilla no encontrada");
        }
        if (req != null && req.nombrePresente()) {
            p.nombre = req.nombreOrNull().trim();
        }
        if (req != null && req.descripcionPresente()) {
            p.descripcionRubro = req.descripcionOrNull();
        }
        plantillaApuRepository.persist(p);
        return PlantillaApuResumenResponse.from(p);
    }

    // =========================================================================
    // Eliminar (DELETE /plantillas-apu/{id})
    // =========================================================================

    @Transactional
    public void eliminar(UUID plantillaPublicId, Long callerUsuarioId) {
        PlantillaApu p = cargarPorOwner(plantillaPublicId, callerUsuarioId);
        if (p.tipo == PlantillaApu.Tipo.SISTEMA) {
            throw ProblemaException.noEncontrado("Plantilla no encontrada");
        }
        plantillaApuRepository.delete(p);
    }

    // =========================================================================
    // Guardar desde APU (POST /apus/{id}/guardar-plantilla)
    // =========================================================================

    /**
     * Crea una plantilla PERSONAL a partir de un APU existente del caller.
     * El snapshot se construye sin precios efectivos (writer price-free —
     * Plan 04 §1): se preservan código de insumo, cantidad, rendimiento,
     * sección, orden y fila HM. Cargar la plantilla NUNCA arrastra un
     * precio del APU origen — los precios se recalculan desde la base del
     * proyecto al aplicar.
     *
     * <p>Para filas con {@code insumoId} resuelto se persiste el código del
     * insumo. Para filas pendientes ({@code insumoId = null}) se conserva el
     * código pendiente guardado en {@code descripcion} (establecido en la
     * carga inicial de la plantilla) — así un APU incompleto sigue siendo
     * accionable al guardarse como plantilla.
     */
    @Transactional
    public PlantillaApuResumenResponse guardarDesdeApu(Long apuId, PlantillaApuCrearRequest req, Long callerUsuarioId) {
        if (req == null || req.nombre() == null || req.nombre().isBlank()) {
            throw ProblemaException.validacion("nombre es obligatorio");
        }
        if (req.nombre().length() > 200) {
            throw ProblemaException.validacion("nombre excede 200 caracteres");
        }
        Apu apu = apuRepository.findById(apuId);
        if (apu == null
                || apuRepository
                        .findByPublicIdAndOwnerScope(apu.publicId, callerUsuarioId)
                        .isEmpty()) {
            throw ProblemaException.noEncontrado("APU no encontrado");
        }
        String snapshot = serializarSnapshotDesdeApu(apu);

        PlantillaApu p = new PlantillaApu();
        p.nombre = req.nombre().trim();
        p.tipo = PlantillaApu.Tipo.PERSONAL;
        p.usuarioId = callerUsuarioId;
        p.descripcionRubro = req.descripcionRubro();
        p.unidad = apu.unidad;
        p.snapshotSecciones = snapshot;
        plantillaApuRepository.persist(p);
        plantillaApuRepository.getEntityManager().flush();
        return PlantillaApuResumenResponse.from(p);
    }

    /**
     * Plan 036 — seam compartido para construir el mismo snapshot price-free desde
     * los flujos PERSONAL y SISTEMA. La entidad APU ya debe estar autorizada por el
     * caller; este método solo serializa su estructura mediante el writer canónico.
     */
    public String serializarSnapshotDesdeApu(Apu apu) {
        return snapshotApuMapper.escribir(construirSnapshotDesdeApu(apu));
    }

    private List<SnapshotApuMapper.SnapshotBloque> construirSnapshotDesdeApu(Apu apu) {
        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));
        List<SnapshotApuMapper.SnapshotBloque> out = new ArrayList<>();
        for (ApuSeccion s : secciones) {
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(s.id);
            List<SnapshotApuMapper.SnapshotFila> filas = new ArrayList<>();
            for (ApuDetalle d : detalles) {
                filas.add(new SnapshotApuMapper.SnapshotFila(
                        s.tipo.name(), d.esHerramientaMenor, insumoCodigoDe(d), d.cantidad, d.rendimiento));
            }
            out.add(new SnapshotApuMapper.SnapshotBloque(s.tipo.name(), filas));
        }
        return out;
    }

    /**
     * Resuelve el código de insumo a persistir en el snapshot:
     * <ul>
     *   <li>HM → null (la fila HM no tiene insumo).</li>
     *   <li>Fila con {@code insumoId} → código del insumo.</li>
     *   <li>Fila pendiente ({@code insumoId = null}) → preserva el código
     *       pendiente guardado en {@code descripcion}.</li>
     * </ul>
     */
    private String insumoCodigoDe(ApuDetalle d) {
        if (d.esHerramientaMenor) return null;
        if (d.insumoId != null) {
            Insumo insumo = insumoRepository.findById(d.insumoId);
            if (insumo != null) {
                return insumo.codigo;
            }
        }
        // Pendiente: preservar el código que la carga inicial guardó en descripcion.
        return d.descripcion;
    }

    // =========================================================================
    // Cargar plantilla al crear APU (POST /presupuestos/{id}/apus plantillaId=…)
    // =========================================================================

    /**
     * Aplica el snapshot de una plantilla sobre un APU recién creado. La
     * operación:
     *
     * <ol>
     *   <li>Borra la fila HM auto-creada (la reemplazará el HM del snapshot
     *       si existe, o quedará como placeholder si el snapshot no trae
     *       HM).</li>
     *   <li>Inserta las filas del snapshot en orden y sección preservados.
     *       El código se resuelve con {@link ResolverInsumoPlantillaService}
     *       usando {@code PROYECTO → CENTRAL → PERSONAL → pendiente} y se
     *       filtra por la sección destino + bases no archivadas (Plan 04
     *       §N04 §B.4).</li>
     *   <li>Si el snapshot no traía HM, shift las filas no-HM del bloque M
     *       +1 e inserta la fila HM auto-recreada en {@code orden=1} para
     *       mantener la invariante de orden único y contiguo por sección.</li>
     * </ol>
     *
     * <p>El motor opera con la regla "fila pendiente → override 0 en la
     * columna de la sección" (N04 §B.4 paso 2), vía
     * {@link ec.uce.propuestas.apu.service.ApuCalculoService#snapshotDeDetalle}.
     *
     * <p>Devuelve las advertencias producidas — el caller decide el código
     * HTTP final (201 sin advertencias, 200 con advertencias).
     *
     * <p>La autorización sobre la plantilla es responsabilidad del caller —
     * aquí se recibe la entidad ya validada.
     */
    @Transactional
    public List<AdvertenciaPlantillaResponse> aplicarPlantilla(Apu apu, PlantillaApu plantilla) {
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        // Borrar HM auto-creado en bloque M (lo reemplazará el del snapshot si existe,
        // o se recreará al final si el snapshot no trae HM — Plan 04 §6).
        ApuSeccion equipo = seccionRepository
                .findByApuYTipo(apu.id, SeccionTipo.EQUIPO)
                .orElseThrow(() -> ProblemaException.noEncontrado("Sección EQUIPO no encontrada"));
        List<ApuDetalle> filasEquipo = detalleRepository.listarDeSeccion(equipo.id);
        for (ApuDetalle d : filasEquipo) {
            if (d.esHerramientaMenor) {
                detalleRepository.delete(d);
            }
        }

        // Mapa sección -> entidad persistida (E, MO, MAT, TR)
        Map<SeccionTipo, ApuSeccion> secMap = new EnumMap<>(SeccionTipo.class);
        for (SeccionTipo t : SeccionTipo.values()) {
            seccionRepository.findByApuYTipo(apu.id, t).ifPresent(s -> secMap.put(t, s));
        }

        List<AdvertenciaPlantillaResponse> advertencias = new ArrayList<>();
        List<SnapshotApuMapper.SnapshotBloque> bloques = snapshotApuMapper.leer(plantilla.snapshotSecciones);

        for (SnapshotApuMapper.SnapshotBloque bloque : bloques) {
            SeccionTipo tipo;
            try {
                tipo = SeccionTipo.valueOf(bloque.tipo());
            } catch (IllegalArgumentException e) {
                continue;
            }
            ApuSeccion seccion = secMap.get(tipo);
            if (seccion == null) continue;

            short orden = 1;
            for (SnapshotApuMapper.SnapshotFila f : bloque.lineas()) {
                ApuDetalle d = new ApuDetalle();
                d.seccionId = seccion.id;
                d.orden = orden++;
                if (f.esHerramientaMenor()) {
                    d.esHerramientaMenor = true;
                    d.descripcion = "Herramienta Menor";
                    // HM canónico — sin insumoId, sin override, sin rendimiento.
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
                        // Pendiente — fila sin insumo; el motor recibe override=0 vía
                        // snapshotDeDetalle cuando insumoId=null y la columna de la
                        // sección está vacía. Marcamos la fila como pendiente con
                        // descripcion=insumoCodigo para que reaparecer "guardar como
                        // plantilla" siga siendo accionable.
                        d.insumoId = null;
                        d.descripcion = f.insumoCodigo() != null ? f.insumoCodigo() : "(código ausente)";
                        d.unidad = switch (tipo) {
                            case EQUIPO, MANO_OBRA -> "h";
                            case MATERIAL, TRANSPORTE -> "u";
                        };
                        escribirOverridePendiente(d, tipo);
                        if (r.advertencia() != null) {
                            advertencias.add(r.advertencia());
                        }
                    }
                }
                detalleRepository.persist(d);
            }
        }

        // Si el snapshot no traía HM, los rows no-HM del bloque M quedaron en
        // orden 1, 2, 3...; shift +1 e inserta HM en orden=1 para preservar la
        // invariante de orden único y contiguo por sección.
        boolean hmEnEquipo =
                detalleRepository.listarDeSeccion(equipo.id).stream().anyMatch(x -> x.esHerramientaMenor);
        if (!hmEnEquipo) {
            List<ApuDetalle> equipoActuales = detalleRepository.listarDeSeccion(equipo.id);
            for (ApuDetalle d : equipoActuales) {
                d.orden = (short) (d.orden + 1);
                detalleRepository.persist(d);
            }
            ApuDetalle hm = new ApuDetalle();
            hm.seccionId = equipo.id;
            hm.descripcion = "Herramienta Menor";
            hm.orden = 1;
            hm.esHerramientaMenor = true;
            detalleRepository.persist(hm);
        }

        return advertencias;
    }

    private static void escribirOverridePendiente(ApuDetalle detalle, SeccionTipo tipo) {
        switch (tipo) {
            case EQUIPO, MANO_OBRA -> detalle.tarifaJornal = BigDecimal.ZERO;
            case MATERIAL, TRANSPORTE -> detalle.precioUnitarioTarifa = BigDecimal.ZERO;
        }
    }

    // =========================================================================
    // Cargar por publicId con scope de owner (RNF-05)
    // =========================================================================

    public PlantillaApu cargarPorOwner(UUID plantillaPublicId, Long callerUsuarioId) {
        if (plantillaPublicId == null) {
            throw ProblemaException.noEncontrado("Plantilla no encontrada");
        }
        Optional<PlantillaApu> row =
                plantillaApuRepository.findByPublicIdAndOwnerScope(plantillaPublicId, callerUsuarioId);
        if (row.isEmpty()) {
            throw ProblemaException.noEncontrado("Plantilla no encontrada");
        }
        return row.get();
    }
}
