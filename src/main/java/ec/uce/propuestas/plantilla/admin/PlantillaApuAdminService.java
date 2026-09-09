package ec.uce.propuestas.plantilla.admin;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.plantilla.admin.dto.PlantillaSistemaCrearRequest;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.repository.PlantillaApuRepository;
import ec.uce.propuestas.plantilla.service.PlantillaApuService;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coordinación administrativa de plantillas APU de tipo SISTEMA (P-40). */
@ApplicationScoped
public class PlantillaApuAdminService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    PlantillaApuRepository plantillaRepository;

    @Inject
    PlantillaApuService plantillaService;

    @Inject
    LogActividadService logActividadService;

    public List<PlantillaApu> listar(String q, int page, int size) {
        return plantillaRepository.listarSistemaAdmin(q, page, size);
    }

    public long contar(String q) {
        return plantillaRepository.contarSistemaAdmin(q);
    }

    @Transactional
    public PlantillaApu crear(PlantillaSistemaCrearRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()) {
            throw ProblemaException.validacion("nombre-requerido");
        }
        if (request.nombre().length() > 200) {
            throw ProblemaException.validacion("nombre excede 200 caracteres");
        }
        Apu apu = apuRepository
                .findByPublicId(request.desdeApuId())
                .orElseThrow(() -> ProblemaException.validacion("apu-origen-no-encontrado"));

        PlantillaApu plantilla = new PlantillaApu();
        plantilla.nombre = request.nombre().trim();
        plantilla.tipo = PlantillaApu.Tipo.SISTEMA;
        plantilla.usuarioId = null;
        plantilla.descripcionRubro = request.descripcionRubro();
        plantilla.unidad = apu.unidad;
        plantilla.snapshotSecciones = plantillaService.serializarSnapshotDesdeApu(apu);
        plantillaRepository.persist(plantilla);
        plantillaRepository.getEntityManager().flush();
        emitir(plantilla, "crear");
        return plantilla;
    }

    @Transactional
    public PlantillaApu editar(
            UUID publicId, boolean nombrePresente, String nombre, boolean descripcionPresente, String descripcion) {
        PlantillaApu plantilla = buscarSistema(publicId);
        if (!nombrePresente && !descripcionPresente) {
            throw ProblemaException.validacion("Debe enviar al menos un campo a editar");
        }
        if (nombrePresente) {
            if (nombre == null || nombre.isBlank()) {
                throw ProblemaException.validacion("nombre-requerido");
            }
            if (nombre.length() > 200) {
                throw ProblemaException.validacion("nombre excede 200 caracteres");
            }
            plantilla.nombre = nombre.trim();
        }
        if (descripcionPresente) {
            plantilla.descripcionRubro = descripcion;
        }
        emitir(plantilla, "editar");
        return plantilla;
    }

    @Transactional
    public void eliminar(UUID publicId) {
        PlantillaApu plantilla = buscarSistema(publicId);
        plantillaRepository.delete(plantilla);
        emitir(plantilla, "borrar");
    }

    private PlantillaApu buscarSistema(UUID publicId) {
        return plantillaRepository
                .findSistemaByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Plantilla no encontrada"));
    }

    private void emitir(PlantillaApu plantilla, String operacion) {
        logActividadService.emitir(
                null,
                EventoLogActividad.ADMIN_PLANTILLA_EDITADA,
                "plantilla_apu",
                plantilla.publicId,
                Map.of("operacion", operacion, "tipo", "SISTEMA"));
    }
}
