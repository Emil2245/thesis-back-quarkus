package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.BasePersonalCrearRequest;
import ec.uce.propuestas.insumo.dto.BasePersonalResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WU-05 — Bases PERSONALES del usuario (N04 §A9, decisión N04).
 *
 * <p>Una base PERSONAL es propiedad exclusiva del {@code usuarioId} interno
 * del caller; el cliente nunca elige dueño, tipo, proyecto padre ni
 * {@code publicId}. El listado siempre excluye filas {@code CENTRAL} y
 * {@code PROYECTO}, y una lookup por {@code publicId} ajeno (de cualquier
 * tipo) devuelve {@link Optional#empty()} para mapearse a 404 — nunca 403,
 * para no filtrar existencia.</p>
 *
 * <p>No implementa copia al usar ni materialización (decisión diferida al
 * siguiente bloque I-06). Esta unidad solo cubre creación, listado y la
 * seam de resolución por {@code publicId} para los próximos bloques.</p>
 */
@ApplicationScoped
public class BasesPersonalesService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    /**
     * Crea una base PERSONAL a nombre del {@code usuarioId} del caller. El nombre
     * se valida no-blanco y se exige unicidad por usuario dentro del segmento
     * PERSONAL (mismo nombre en CENTRAL/PROYECTO de otro dueño no choca).
     */
    @Transactional
    public BasePersonalResponse crear(Long usuarioId, BasePersonalCrearRequest req) {
        String nombre = req == null ? null : req.nombre();
        if (nombre == null || nombre.isBlank()) {
            throw ProblemaException.validacion("El nombre de la base es obligatorio");
        }
        nombre = nombre.trim();
        if (baseInsumosRepository.contarPorUsuarioYTipoYNombre(usuarioId, TipoBase.PERSONAL, nombre) > 0) {
            throw ProblemaException.validacion("Ya existe una base PERSONAL con ese nombre");
        }

        BaseInsumos base = new BaseInsumos();
        base.nombre = nombre;
        base.tipo = TipoBase.PERSONAL;
        base.usuarioId = usuarioId;
        base.proyectoId = null;
        base.archivada = false;
        baseInsumosRepository.persist(base);
        // Forzar el INSERT y la relectura del public_id por defecto uuidv7()
        // antes de mapear a la respuesta pública.
        baseInsumosRepository.getEntityManager().flush();
        return toResponse(base);
    }

    /** Lista bases PERSONALES del {@code usuarioId}. CENTRAL/PROYECTO nunca aparecen. */
    public List<BasePersonalResponse> listar(Long usuarioId) {
        return baseInsumosRepository.listarPersonalesDeUsuario(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Resolución por {@code publicId} (UUIDv7) restringida al {@code usuarioId}
     * del caller. Devuelve {@link Optional#empty()} para cualquier fila ajena,
     * independientemente del tipo (CENTRAL/PROYECTO de otro dueño también se
     * ocultan). Es la seam de servicio sobre la que el siguiente bloque de
     * copia-al-usar construirá sin reabrir el seam aquí.
     */
    public Optional<BaseInsumos> buscarPorPublicId(UUID publicId, Long usuarioId) {
        return baseInsumosRepository.findByPublicIdAndOwnerScope(publicId, usuarioId)
                .filter(b -> b.tipo == TipoBase.PERSONAL && usuarioId.equals(b.usuarioId));
    }

    private BasePersonalResponse toResponse(BaseInsumos b) {
        long total = insumoRepository.contarDeBase(b.id);
        return new BasePersonalResponse(b.publicId, b.nombre, b.archivada, total, b.createdAt, b.updatedAt);
    }
}
