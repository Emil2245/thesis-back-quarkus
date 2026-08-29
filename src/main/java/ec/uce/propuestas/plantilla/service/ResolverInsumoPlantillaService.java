package ec.uce.propuestas.plantilla.service;

import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.ResolverInsumoProyectoService;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plan 04 (P-26, N04 §B.4) — Resolución de un {@code insumoCodigo} del
 * snapshot contra las bases visibles para el proyecto:
 *
 * <ol>
 *   <li>PROYECTO del proyecto del nuevo APU → reusar (D-07).</li>
 *   <li>CENTRAL → copiar a PROYECTO vía
 *       {@link ResolverInsumoProyectoService#materializarOReusar}.</li>
 *   <li>PERSONAL del dueño del proyecto → copiar a PROYECTO.</li>
 * </ol>
 *
 * <p><b>Filtros obligatorios (Plan 04 §N04 §B.4):</b>
 * <ul>
 *   <li>Todas las fuentes (PROYECTO/CENTRAL/PERSONAL) deben coincidir con
 *       el {@code SeccionTipo} del bloque destino — un insumo EQUIPO nunca
 *       se resuelve dentro de MANO_OBRA, etc.</li>
 *   <li>Las bases CENTRAL y PERSONAL consultadas deben estar
 *       {@code archivada = false} (una base archivada no se considera
 *       visible).</li>
 * </ul>
 *
 * <p>Si el código no existe en ninguna base visible con el tipo
 * compatible, devuelve {@link Optional#empty()} y el caller debe
 * materializar una fila "pendiente" con {@code insumoId=null} (N04 §B.4
 * paso 2). En ese caso se añade una {@link AdvertenciaPlantillaResponse}
 * con motivo {@code no-existe-en-base-proyecto}.
 *
 * <p>Esta clase delega en {@link ResolverInsumoProyectoService} para la
 * copia, evitando duplicar la lógica D-07. La firma acepta el
 * {@code insumoCodigo}, la sección destino (filtro de tipo) y el proyecto
 * destino.
 */
@ApplicationScoped
public class ResolverInsumoPlantillaService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    ResolverInsumoProyectoService resolverInsumoProyecto;

    /**
     * Resultado de una resolución: el insumo PROYECTO a usar, o vacío si el
     * código no se encontró en ninguna base visible (el caller debe crear
     * fila pendiente + advertencia).
     */
    public record Resultado(Optional<Insumo> insumoProyecto, AdvertenciaPlantillaResponse advertencia) {

        public static Resultado encontrado(Insumo insumo) {
            return new Resultado(Optional.of(insumo), null);
        }

        public static Resultado pendiente(String codigo) {
            return new Resultado(Optional.empty(), AdvertenciaPlantillaResponse.noExiste(codigo));
        }

        public boolean tieneAdvertencia() {
            return advertencia != null;
        }
    }

    /**
     * Resuelve el código a un insumo de la base PROYECTO del proyecto,
     * compatible con la sección destino. La búsqueda sigue el orden:
     * PROYECTO propio → CENTRAL (no archivada) → PERSONAL del dueño del
     * proyecto (no archivada). Las bases CENTRAL/PERSONAL consultadas se
     * filtran también por tipo de insumo compatible con la sección.
     *
     * @param codigo      código del snapshot (puede ser null/blanco → pendiente vacío)
     * @param seccionTipo sección destino del APU; filtra por tipo compatible
     * @param proyectoId  BIGINT del proyecto dueño del APU destino
     */
    @Transactional
    public Resultado resolver(String codigo, SeccionTipo seccionTipo, Long proyectoId) {
        if (codigo == null || codigo.isBlank()) {
            return Resultado.pendiente("");
        }
        if (seccionTipo == null || proyectoId == null) {
            return Resultado.pendiente(codigo);
        }
        TipoInsumo tipoInsumo = TipoInsumo.valueOf(seccionTipo.name());

        // 1) PROYECTO del proyecto destino (filtrado por tipo de sección)
        Optional<BaseInsumos> baseProyecto = baseInsumosRepository.findByProyecto(proyectoId);
        if (baseProyecto.isPresent()) {
            Optional<Insumo> directo = insumoRepository.findByBaseYcodigoYTipo(
                    baseProyecto.get().id, codigo, tipoInsumo);
            if (directo.isPresent()) {
                return Resultado.encontrado(directo.get());
            }
        }

        Proyecto proyecto = proyectoRepository.findById(proyectoId);
        if (proyecto == null) {
            return Resultado.pendiente(codigo);
        }

        // 2) CENTRAL (no archivada, tipo compatible) → copiar a PROYECTO
        Long candidatoCentral = buscarCodigoEnBase(codigo, TipoBase.CENTRAL, null, tipoInsumo);
        if (candidatoCentral != null) {
            Insumo insumoCentral = insumoRepository.findById(candidatoCentral);
            if (insumoCentral != null) {
                return Resultado.encontrado(
                        resolverInsumoProyecto.materializarOReusar(candidatoCentral, proyectoId));
            }
        }

        // 3) PERSONAL del dueño del proyecto (no archivada, tipo compatible) → copiar a PROYECTO
        Long candidatoPersonal = buscarCodigoEnBase(codigo, TipoBase.PERSONAL, proyecto.usuarioId, tipoInsumo);
        if (candidatoPersonal != null) {
            Insumo insumoPersonal = insumoRepository.findById(candidatoPersonal);
            if (insumoPersonal != null) {
                return Resultado.encontrado(
                        resolverInsumoProyecto.materializarOReusar(candidatoPersonal, proyectoId));
            }
        }

        return Resultado.pendiente(codigo);
    }

    /**
     * Busca el BIGINT interno de un insumo cuyo {@code codigo} esté en una
     * base del tipo indicado, compatible con la sección destino y no
     * archivada. Devuelve {@code null} si no existe.
     */
    private Long buscarCodigoEnBase(
            String codigo, TipoBase tipo, Long usuarioId, TipoInsumo tipoInsumo) {
        StringBuilder ql = new StringBuilder(
                "select i.id from Insumo i, BaseInsumos b "
                        + "where i.baseId = b.id and b.tipo = :tipo and b.archivada = false "
                        + "and i.codigo = :codigo and i.tipo = :tipoInsumo");
        if (usuarioId != null) {
            ql.append(" and b.usuarioId = :uid");
        }
        ql.append(" order by i.id");
        var query = insumoRepository.getEntityManager().createQuery(ql.toString(), Long.class)
                .setParameter("tipo", tipo)
                .setParameter("codigo", codigo)
                .setParameter("tipoInsumo", tipoInsumo)
                .setMaxResults(1);
        if (usuarioId != null) {
            query.setParameter("uid", usuarioId);
        }
        List<Long> rs = query.getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    /**
     * Variante conveniente para procesar varios códigos del mismo bloque de
     * sección en un solo APU. Devuelve la lista de resoluciones en el mismo
     * orden de entrada.
     */
    public List<Resultado> resolverTodos(List<String> codigos, SeccionTipo seccionTipo, Long proyectoId) {
        List<Resultado> out = new ArrayList<>();
        for (String codigo : codigos) {
            out.add(resolver(codigo, seccionTipo, proyectoId));
        }
        return out;
    }
}
