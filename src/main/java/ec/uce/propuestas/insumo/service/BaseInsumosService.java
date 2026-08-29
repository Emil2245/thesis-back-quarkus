package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.mapper.BaseInsumosMapper;
import ec.uce.propuestas.insumo.mapper.InsumoMapper;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BaseInsumosService {

    public static final int PAGE_SIZE_DEFAULT = 25;
    public static final long DESACTUALIZADO_DAYS = 90;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    /** Base PROYECTO del proyecto; la crea si no existe (cada proyecto tiene una). */
    @Transactional
    public BaseInsumos asegurarBaseProyecto(Long proyectoId) {
        return baseInsumosRepository.findByProyecto(proyectoId).orElseGet(() -> {
            BaseInsumos base = new BaseInsumos();
            base.nombre = "Insumos del proyecto #" + proyectoId;
            base.tipo = TipoBase.PROYECTO;
            base.proyectoId = proyectoId;
            base.archivada = false;
            baseInsumosRepository.persist(base);
            return base;
        });
    }

    public BaseInsumos obtenerBaseProyecto(Long proyectoId) {
        return baseInsumosRepository
                .findByProyecto(proyectoId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("El proyecto no tiene base de insumos"));
    }

    /** Bases centrales (P-13). Las archivadas no se exponen (D-12). */
    public List<BaseInsumosResponse> listarCentrales() {
        return baseInsumosRepository.listarCentralesActivas().stream()
                .map(b -> BaseInsumosMapper.toResponse(b, insumoRepository.contarDeBase(b.id)))
                .toList();
    }

    /**
     * Plan 05 — Variante admin que devuelve las entidades crudas, no el DTO
     * interno de Long id. La capa de presentación (AdminBaseCentralResource
     * expone UUID id por convención WU-03) mapea con la información
     * completa, sin tocar el DTO público de la ruta no-admin.
     */
    public List<BaseInsumos> listarCentralesAdminEntidades(boolean incluirArchivadas) {
        return baseInsumosRepository.listarCentralesAdmin(incluirArchivadas);
    }

    // ========================================================================
    // Plan 05 — ciclo de vida administrativo de bases CENTRALES (D-12, P-39)
    // ========================================================================

    /** Plan 05 — Crea una base CENTRAL. Valida nombre no-blanco y unicidad. */
    @Transactional
    public BaseInsumos crearCentral(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw ProblemaException.validacion("El nombre de la base es obligatorio");
        }
        nombre = nombre.trim();
        if (baseInsumosRepository.contarCentralPorNombre(nombre) > 0) {
            throw ProblemaException.validacion("Ya existe una base CENTRAL con ese nombre");
        }
        BaseInsumos base = new BaseInsumos();
        base.nombre = nombre;
        base.tipo = TipoBase.CENTRAL;
        base.usuarioId = null;
        base.proyectoId = null;
        base.archivada = false;
        baseInsumosRepository.persist(base);
        baseInsumosRepository.getEntityManager().flush();
        return base;
    }

    /** Plan 05 — Renombra una base CENTRAL existente (D-12 sin bloqueo). */
    @Transactional
    public BaseInsumos renombrarCentral(UUID publicId, String nuevoNombre) {
        BaseInsumos base = baseInsumosRepository
                .findCentralByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base central no encontrada"));
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw ProblemaException.validacion("El nombre de la base es obligatorio");
        }
        nuevoNombre = nuevoNombre.trim();
        if (!nuevoNombre.equals(base.nombre) && baseInsumosRepository.contarCentralPorNombre(nuevoNombre) > 0) {
            throw ProblemaException.validacion("Ya existe una base CENTRAL con ese nombre");
        }
        base.nombre = nuevoNombre;
        return base;
    }

    /**
     * Plan 05 — Archiva una base CENTRAL (N04 §D-12). Una vez archivada la
     * base deja de aparecer en el catálogo normal de usuarios
     * ({@code /bases-centrales}) pero sigue visible para SUPER_ADMIN
     * ({@code GET /admin/bases-centrales?incluirArchivadas=true}). El borrado
     * posterior NO se bloquea por referencias históricas: las copias PROYECTO
     * persisten sin cambios.
     */
    @Transactional
    public BaseInsumos archivarCentral(UUID publicId) {
        BaseInsumos base = baseInsumosRepository
                .findCentralByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base central no encontrada"));
        base.archivada = true;
        return base;
    }

    /**
     * Plan 05 — Elimina físicamente una base CENTRAL. Solo permitido tras el
     * archivo: si la base no está archivada devuelve 409 (conflicto de estado,
     * catálogo D-12). Los insumos asociados se eliminan en cascada por la FK
     * {@code insumo.base_id → base_insumos(id) ON DELETE CASCADE}.
     */
    @Transactional
    public void eliminarCentralArchivada(UUID publicId) {
        BaseInsumos base = baseInsumosRepository
                .findCentralByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base central no encontrada"));
        if (!base.archivada) {
            throw ProblemaException.conflicto("base-no-archivada", "La base debe estar archivada antes de eliminarse");
        }
        baseInsumosRepository.delete(base);
    }

    /** Plan 05 — Lookup interno (no scoped) para los handlers del admin resource. */
    public BaseInsumos obtenerCentralPorPublicId(UUID publicId) {
        return baseInsumosRepository
                .findCentralByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base central no encontrada"));
    }

    /** Lista insumos de una base con filtros y paginación (P-13/P-16). */
    public Page<InsumoResponse> listarInsumosBase(
            Long baseId, TipoInsumo tipo, String q, boolean desactualizadosOnly, int pageIndex, int pageSize) {
        long corte = System.currentTimeMillis() - DESACTUALIZADO_DAYS * 86400L * 1000;
        var items =
                insumoRepository
                        .listarDeBaseConFiltros(baseId, tipo, q, desactualizadosOnly, corte, pageIndex, pageSize)
                        .stream()
                        .map(InsumoMapper::toResponse)
                        .toList();
        long total = insumoRepository.contarDeBaseConFiltros(baseId, tipo, q, desactualizadosOnly, corte);
        return Page.of(items, total, pageIndex, pageSize);
    }
}
