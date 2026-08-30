package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.insumo.dto.CopiaBaseResultadoResponse;
import ec.uce.propuestas.insumo.dto.CopiarBaseRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Copia/duplica una base de insumos hacia otra base (P-14). Regla: un insumo
 * ya presente en la base destino por clave (base, codigo) se omite, no se pisa.
 *
 * <p>Plan 07 — el {@code baseId} y el {@code proyectoId} del request son
 * identidades externas UUIDv7. La validación UUIDv7 se aplica en la frontera
 * (v7 obligatorio antes de cualquier acceso al repositorio; no-v7 o mal
 * formado → 400 {@code validacion}). El seam resuelve UUID → BIGINT interno
 * aplicando el scope de owner: una fila ajena o inexistente devuelve 404
 * {@code no-encontrado} (RNF-05) sin filtrar existencia. La fuente CENTRAL
 * permanece legible globalmente, pero su tipo debe ser estrictamente
 * {@code CENTRAL} para evitar que un caller use un {@code baseId} que
 * apunte a una fila ajena de otro tipo.</p>
 */
@ApplicationScoped
public class CopiaBaseService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    ProyectoRepository proyectoRepository;

    /**
     * Copia una base de insumos (CENTRAL o PROYECTO) hacia la base PROYECTO del
     * proyecto destino del caller. Aplica scope de owner tanto al destino como
     * a la fuente PROYECTO: una fila ajena o de tipo incorrecto devuelve 404
     * (nunca 403 — RNF-05). La fuente CENTRAL es legible globalmente pero debe
     * ser estrictamente CENTRAL (el seam ya filtra por tipo en
     * {@code findCentralByPublicId}).
     *
     * @param req              request con {@code fuenteTipo}, {@code baseId} UUIDv7
     *                         de la fuente, y {@code proyectoId} UUIDv7 del
     *                         destino (el resource lo fija desde el path, ya
     *                         validado como UUIDv7).
     * @param callerUsuarioId  BIGINT interno del caller; se usa para el scope de
     *                         owner en destino y fuente PROYECTO.
     */
    @Transactional
    public CopiaBaseResultadoResponse copiar(CopiarBaseRequest req, Long callerUsuarioId) {
        if (callerUsuarioId == null) {
            throw ProblemaException.noEncontrado("Usuario autenticado no encontrado");
        }
        if (req.proyectoId() == null) {
            throw ProblemaException.validacion("proyectoId requerido");
        }
        if (req.baseId() == null) {
            throw ProblemaException.validacion("baseId requerido");
        }
        if (req.fuenteTipo() == null
                || (!"CENTRAL".equalsIgnoreCase(req.fuenteTipo()) && !"PROYECTO".equalsIgnoreCase(req.fuenteTipo()))) {
            throw ProblemaException.validacion("fuenteTipo debe ser CENTRAL o PROYECTO");
        }

        // Plan 07 — frontera JSON: ambos ids llegan como UUID; Jackson acepta
        // UUIDv4 bien formado, por lo que validamos v7 antes del lookup. UUID
        // mal formado o no-v7 → 400 validacion (RNF-05).
        UUID destinoProyectoPublicId = UuidV7.parse(req.proyectoId().toString());
        UUID origenPublicId = UuidV7.parse(req.baseId().toString());

        // Destino: scope de owner — un proyecto ajeno devuelve 404 (nunca 403).
        Proyecto destino = proyectoRepository
                .findByPublicIdAndOwnerScope(destinoProyectoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto destino no encontrado"));

        Long origenBaseId;
        if ("CENTRAL".equalsIgnoreCase(req.fuenteTipo())) {
            // Fuente CENTRAL — globalmente legible pero debe ser estrictamente CENTRAL.
            // El seam del repositorio ya filtra por tipo en findCentralByPublicId.
            BaseInsumos origen = baseInsumosRepository
                    .findCentralByPublicId(origenPublicId)
                    .orElseThrow(() -> ProblemaException.noEncontrado("Base origen no encontrada"));
            origenBaseId = origen.id;
        } else {
            // Fuente PROYECTO — scope de owner: la fila debe pertenecer al caller.
            Proyecto origenProyecto = proyectoRepository
                    .findByPublicIdAndOwnerScope(origenPublicId, callerUsuarioId)
                    .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto origen no encontrado"));
            BaseInsumos baseProyecto = baseInsumosService.obtenerBaseProyecto(origenProyecto.id);
            origenBaseId = baseProyecto.id;
        }
        BaseInsumos destinoBase = baseInsumosService.asegurarBaseProyecto(destino.id);
        if (origenBaseId.equals(destinoBase.id)) {
            throw ProblemaException.validacion("No se puede copiar una base sobre sí misma");
        }

        List<String> omitidos = new ArrayList<>();
        int copiados = 0;
        for (Insumo ins : insumoRepository.listarDeBase(origenBaseId)) {
            if (insumoRepository.findByBaseYcodigo(destinoBase.id, ins.codigo).isPresent()) {
                omitidos.add(ins.codigo);
                continue;
            }
            Insumo nuevo = new Insumo();
            nuevo.baseId = destinoBase.id;
            nuevo.codigo = ins.codigo;
            nuevo.tipo = ins.tipo;
            nuevo.descripcion = ins.descripcion;
            nuevo.unidad = ins.unidad;
            nuevo.precioUnitario = ins.precioUnitario;
            insumoRepository.persist(nuevo);
            copiados++;
        }
        return new CopiaBaseResultadoResponse(copiados, omitidos);
    }
}
