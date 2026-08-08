package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.insumo.dto.ErrorFila;
import ec.uce.propuestas.insumo.dto.ImportResultadoResponse;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.importacion.CsvInsumoParser;
import ec.uce.propuestas.insumo.service.importacion.FilaInsumo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

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
                    crud.crear(baseId, req);
                    creados++;
                } else {
                    actualizados++;
                }
            } catch (RuntimeException e) {
                errores.add(new ErrorFila(num, "general", mensaje(e)));
            }
        }
        return new ImportResultadoResponse(creados, actualizados, errores);
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}