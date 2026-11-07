package ec.uce.propuestas.documento;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1.Specification;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.FlushMode;
import org.hibernate.Session;

/** Resolves ownership, titles and ordered technical content inside one read-only capture transaction. */
@ApplicationScoped
public class EspecificacionesCapturaService {
    @Inject
    PresupuestoRepository presupuestos;

    @Inject
    ProyectoService proyectos;

    @Inject
    UsuarioRepository usuarios;

    @Inject
    ApuRepository apus;

    public record Resultado(SpecificationProjectionV1 projection, Long proyectoInternoId, Long callerId) {}

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Resultado capturar(UUID presupuestoId, String email, String titulo1Override, String titulo2Override) {
        Session session = presupuestos.getEntityManager().unwrap(Session.class);
        session.setHibernateFlushMode(FlushMode.MANUAL);
        session.setDefaultReadOnly(true);
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            }
        });
        Long caller = usuarios.findByEmail(email)
                .map(usuario -> usuario.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
        var presupuesto = presupuestos
                .findByPublicIdAndOwnerScope(presupuestoId, caller)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        var proyecto = proyectos.validarPropietario(caller, presupuesto.proyectoId);
        var specifications = apus.listarConEspecificacionTecnica(presupuesto.id).stream()
                .filter(apu -> apu.especificacionTecnica != null && !apu.especificacionTecnica.isBlank())
                .map(apu -> new Specification(
                        String.valueOf(apu.codigo),
                        String.valueOf(apu.descripcion),
                        String.valueOf(apu.unidad),
                        apu.especificacionTecnica))
                .toList();
        if (specifications.isEmpty())
            throw ProblemaException.validacion(
                    "El presupuesto no contiene APUs con Especificaciones Técnicas para exportar");
        var projection = new SpecificationProjectionV1(
                SpecificationProjectionV1.VERSION,
                presupuestoId,
                proyecto.nombreProyecto,
                proyecto.anio,
                title(titulo1Override, proyecto.tituloEt1, "ESPECIFICACIONES TÉCNICAS"),
                title(titulo2Override, proyecto.tituloEt2, proyecto.nombreProyecto),
                LocalDate.now().toString(),
                specifications);
        return new Resultado(projection, proyecto.id, caller);
    }

    private static String title(String override, String projectTitle, String fallback) {
        if (override != null && !override.isBlank()) return override.trim();
        if (projectTitle != null && !projectTitle.isBlank()) return projectTitle.trim();
        return fallback;
    }
}
