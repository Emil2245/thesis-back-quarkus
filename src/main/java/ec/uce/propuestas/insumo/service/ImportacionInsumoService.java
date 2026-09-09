package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.insumo.dto.ErrorFila;
import ec.uce.propuestas.insumo.dto.ImportResultadoResponse;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.importacion.CsvInsumoParser;
import ec.uce.propuestas.insumo.service.importacion.FilaInsumo;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Importación masiva de insumos desde CSV (P-15). El parser es puro
 * ({@link CsvInsumoParser}); aquí se aplican las reglas de negocio (D-06) y
 * se upsertea contra la base destino.
 */
@ApplicationScoped
public class ImportacionInsumoService {

    @Inject
    InsumoCrudService crud;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    LogActividadService logActividadService;

    @Transactional
    public ImportResultadoResponse importarCsv(Long baseId, byte[] contenido) {
        List<FilaInsumo> filas = CsvInsumoParser.parse(contenido, TipoInsumo.MATERIAL);
        int creados = 0;
        int actualizados = 0;
        List<ErrorFila> errores = new ArrayList<>();
        int num = 1;
        for (FilaInsumo f : filas) {
            num++;
            if (!f.valida()) {
                errores.add(new ErrorFila(num, f.errorCampo(), f.errorMensaje()));
                continue;
            }
            InsumoCrearRequest req = new InsumoCrearRequest(
                    f.codigo(), TipoInsumo.MATERIAL, f.descripcion(), f.unidad(), f.precioUnitario());
            try {
                if (insumoRepository.findByBaseYcodigo(baseId, f.codigo()).isEmpty()) {
                    crud.crearDesdeImportacion(baseId, req);
                    creados++;
                } else {
                    actualizados++;
                }
            } catch (RuntimeException e) {
                errores.add(new ErrorFila(num, "general", mensaje(e)));
            }
        }
        ImportResultadoResponse resultado = new ImportResultadoResponse(creados, actualizados, errores);
        emitirSiBaseProyecto(baseId, resultado);
        return resultado;
    }

    private void emitirSiBaseProyecto(Long baseId, ImportResultadoResponse resultado) {
        BaseInsumos base = baseInsumosRepository.findById(baseId);
        if (base == null || base.tipo != TipoBase.PROYECTO || base.proyectoId == null) {
            return;
        }
        Proyecto proyecto = proyectoRepository.findById(base.proyectoId);
        Long usuarioId = proyecto == null ? null : proyecto.usuarioId;
        logActividadService.emitir(
                usuarioId,
                EventoLogActividad.INSUMOS_IMPORT_CSV,
                "base_insumos",
                base.publicId,
                Map.of(
                        "creados", resultado.creados(),
                        "actualizados", resultado.actualizados(),
                        "errores", resultado.errores().size()));
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
