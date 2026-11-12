package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ParametrosProyectoRepository implements PanacheRepositoryBase<ParametrosProyecto, Long> {

    /** Materializa en una sola sentencia la copia de los 12 defaults vigentes. */
    public int crearDesdeSistema(Long proyectoId) {
        return getEntityManager()
                .createNativeQuery("""
                        INSERT INTO parametros_proyecto (
                            proyecto_id, porcentaje_herramienta_menor, porcentaje_indirecto, iva, moneda,
                            mostrar_secciones_vacias, sufijos_seccion_activos, mostrar_subtotales_seccion,
                            mostrar_subtotales_pie, mostrar_nombre_proyecto_header, enumerar_apus,
                            mensaje_footer, modo_codigo_rubro)
                        SELECT :proyectoId, porcentaje_herramienta_menor, porcentaje_indirecto, iva, moneda,
                            mostrar_secciones_vacias, sufijos_seccion_activos, mostrar_subtotales_seccion,
                            mostrar_subtotales_pie, mostrar_nombre_proyecto_header, enumerar_apus,
                            mensaje_footer, modo_codigo_rubro
                        FROM parametros_sistema WHERE id = 1
                        """)
                .setParameter("proyectoId", proyectoId)
                .executeUpdate();
    }
}
