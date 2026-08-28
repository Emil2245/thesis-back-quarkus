package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Resolver de "copia al usar" (N04 §A9, plan 02 §2): garantiza que toda fila de
 * {@code apu_detalle.insumo_id} apunte a un insumo de la base PROYECTO del APU.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Si el insumo fuente ya es PROYECTO y pertenece al mismo proyecto, se reusa
 *       tal cual — no se duplica.</li>
 *   <li>Si el insumo fuente es PROYECTO pero de OTRO proyecto, no es visible:
 *       {@code 404 no-encontrado} (sin filtrar existencia ajena, RNF-05).</li>
 *   <li>Si el insumo fuente es CENTRAL o PERSONAL (del dueño del proyecto), se
 *       materializa una copia independiente en la base PROYECTO del proyecto.
 *       El {@code publicId} lo genera la BD (UUIDv7) — N04 §A9 garantiza
 *       identidad externa fresca por copia.</li>
 *   <li>Si el insumo fuente es PERSONAL de un dueño distinto al del proyecto,
 *       no es visible: {@code 404 no-encontrado}.</li>
 *   <li>La deduplicación es estricta por {@code (base_id, codigo)}: si ya existe
 *       una fila con el mismo {@code codigo} en la base destino, se reusa y NO
 *       se crea una nueva. Ediciones posteriores del insumo fuente NO se
 *       propagan al destino — la copia es independiente.</li>
 * </ul>
 *
 * <p>El método opera con BIGINTs internos: el seam de identidad externa
 * (UUIDv7 + scope de owner) ya se cerró en la capa de resource. Una entrada
 * ajena nunca llega aquí.
 */
@ApplicationScoped
public class ResolverInsumoProyectoService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    BaseInsumosService baseInsumosService;

    /**
     * Devuelve el insumo PROYECTO que el APU debe referenciar. Materializa una
     * copia si el origen es CENTRAL o PERSONAL (del dueño del proyecto); reusa
     * el existente si el origen ya es PROYECTO del mismo proyecto o si ya hay
     * una fila con el mismo {@code codigo} en la base destino.
     *
     * @param insumoId BIGINT interno del insumo fuente (ya validado por owner)
     * @param proyectoId BIGINT interno del proyecto dueño del APU
     * @return insumo PROYECTO usable para {@code apu_detalle.insumo_id}
     */
    @Transactional
    public Insumo materializarOReusar(Long insumoId, Long proyectoId) {
        Insumo origen = insumoRepository.findById(insumoId);
        if (origen == null) {
            throw ProblemaException.noEncontrado("Insumo no encontrado");
        }
        BaseInsumos baseOrigen = baseInsumosRepository.findById(origen.baseId);
        if (baseOrigen == null) {
            throw ProblemaException.noEncontrado("Base de insumo no encontrada");
        }

        switch (baseOrigen.tipo) {
            case PROYECTO -> {
                if (proyectoId.equals(baseOrigen.proyectoId)) {
                    return origen;
                }
                throw ProblemaException.noEncontrado("Insumo no encontrado");
            }
            case PERSONAL -> {
                Proyecto proyecto = proyectoRepository.findById(proyectoId);
                if (proyecto == null) {
                    throw ProblemaException.noEncontrado("Proyecto no encontrado");
                }
                if (!proyecto.usuarioId.equals(baseOrigen.usuarioId)) {
                    throw ProblemaException.noEncontrado("Insumo no encontrado");
                }
                return copiarOReusar(origen, proyectoId);
            }
            case CENTRAL -> {
                return copiarOReusar(origen, proyectoId);
            }
            default -> throw ProblemaException.noEncontrado("Insumo no encontrado");
        }
    }

    /**
     * Asegura la base PROYECTO del proyecto y devuelve la fila {@code (base_id,
     * codigo)} existente, o crea una copia independiente con los campos
     * snapshot del origen. La fila nunca pisa una existente (D-07).
     */
    private Insumo copiarOReusar(Insumo origen, Long proyectoId) {
        BaseInsumos baseDestino = baseInsumosService.asegurarBaseProyecto(proyectoId);
        return insumoRepository
                .findByBaseYcodigo(baseDestino.id, origen.codigo)
                .orElseGet(() -> {
                    Insumo nuevo = new Insumo();
                    nuevo.baseId = baseDestino.id;
                    nuevo.codigo = origen.codigo;
                    nuevo.tipo = origen.tipo;
                    nuevo.descripcion = origen.descripcion;
                    nuevo.unidad = origen.unidad;
                    nuevo.precioUnitario = origen.precioUnitario;
                    insumoRepository.persist(nuevo);
                    return nuevo;
                });
    }
}