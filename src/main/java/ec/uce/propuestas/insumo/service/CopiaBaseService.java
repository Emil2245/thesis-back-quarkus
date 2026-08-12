package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.CopiaBaseResultadoResponse;
import ec.uce.propuestas.insumo.dto.CopiarBaseRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

/**
 * Copia/duplica una base de insumos hacia otra base (P-14). Regla: un insumo
 * ya presente en la base destino por clave (base, codigo) se omite, no se pisa.
 */
@ApplicationScoped
public class CopiaBaseService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    BaseInsumosService baseInsumosService;

    @Transactional
    public CopiaBaseResultadoResponse copiar(CopiarBaseRequest req) {
        if (req.proyectoId() == null) {
            throw ProblemaException.validacion("proyectoId requerido");
        }
        if (req.baseId() == null) {
            throw ProblemaException.validacion("baseId requerido");
        }
        BaseInsumos origen = baseInsumosRepository.findById(req.baseId());
        if (origen == null) {
            throw ProblemaException.noEncontrado("Base origen no encontrada");
        }
        BaseInsumos destino = baseInsumosService.asegurarBaseProyecto(req.proyectoId());
        if (origen.id.equals(destino.id)) {
            throw ProblemaException.validacion("No se puede copiar una base sobre sí misma");
        }
        List<String> omitidos = new ArrayList<>();
        int copiados = 0;
        for (Insumo ins : insumoRepository.listarDeBase(origen.id)) {
            if (insumoRepository.findByBaseYcodigo(destino.id, ins.codigo).isPresent()) {
                omitidos.add(ins.codigo);
                continue;
            }
            Insumo nuevo = new Insumo();
            nuevo.baseId = destino.id;
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
