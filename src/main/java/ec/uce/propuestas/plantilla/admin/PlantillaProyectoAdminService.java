package ec.uce.propuestas.plantilla.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaProyectoSistemaCrearRequest;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import ec.uce.propuestas.plantilla.repository.PlantillaProyectoRepository;
import ec.uce.propuestas.plantilla.service.PlantillaProyectoService;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 044 — coordinación administrativa de plantillas de proyecto SISTEMA.
 * Espejo de {@link PlantillaApuAdminService}: alta desde un proyecto, edición de
 * metadatos y borrado. El borrado no toca proyectos ya creados desde ella (FK
 * {@code ON DELETE SET NULL}, V001).
 */
@ApplicationScoped
public class PlantillaProyectoAdminService {

    @Inject
    PlantillaProyectoRepository plantillaRepository;

    @Inject
    PlantillaProyectoService plantillaService;

    @Inject
    LogActividadService logActividadService;

    public List<PlantillaProyecto> listar(String q, int page, int size) {
        return plantillaRepository.listarSistemaAdmin(q, page, size);
    }

    public long contar(String q) {
        return plantillaRepository.contarSistemaAdmin(q);
    }

    @Transactional
    public PlantillaProyecto crear(PlantillaProyectoSistemaCrearRequest request) {
        PlantillaProyecto plantilla = plantillaService.crearSistemaDesdeProyecto(
                request.desdeProyectoId(), request.nombre(), request.descripcion());
        emitir(plantilla, "crear");
        return plantilla;
    }

    @Transactional
    public PlantillaProyecto editar(
            UUID publicId, boolean nombrePresente, String nombre, boolean descripcionPresente, String descripcion) {
        PlantillaProyecto plantilla = buscarSistema(publicId);
        if (!nombrePresente && !descripcionPresente) {
            throw ProblemaException.validacion("Debe enviar al menos un campo a editar");
        }
        if (nombrePresente) {
            if (nombre == null || nombre.isBlank()) {
                throw ProblemaException.validacion("nombre-requerido");
            }
            if (nombre.trim().length() > 200) {
                throw ProblemaException.validacion("nombre excede 200 caracteres");
            }
            plantilla.nombre = nombre.trim();
        }
        if (descripcionPresente) {
            plantilla.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        }
        emitir(plantilla, "editar");
        return plantilla;
    }

    @Transactional
    public void eliminar(UUID publicId) {
        PlantillaProyecto plantilla = buscarSistema(publicId);
        plantillaRepository.delete(plantilla);
        emitir(plantilla, "borrar");
    }

    private PlantillaProyecto buscarSistema(UUID publicId) {
        return plantillaRepository
                .findSistemaByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Plantilla no encontrada"));
    }

    private void emitir(PlantillaProyecto plantilla, String operacion) {
        logActividadService.emitir(
                null,
                EventoLogActividad.ADMIN_PLANTILLA_EDITADA,
                "plantilla_proyecto",
                plantilla.publicId,
                Map.of("operacion", operacion, "tipo", "SISTEMA"));
    }
}
