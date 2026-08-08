package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Parámetros globales de cálculo (una sola fila, id=1). Solo lectura desde la API
 * (LECTURA); su escritura es tarea de administración/super-admin, no expuesta aquí.
 */
@Entity
@Table(name = "parametros_sistema")
public class ParametrosSistema extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    public Short id;

    @Column(name = "porcentaje_herramienta_menor", nullable = false, precision = 5, scale = 4)
    public BigDecimal porcentajeHerramientaMenor;

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(nullable = false, precision = 5, scale = 4)
    public BigDecimal iva;

    @Column(nullable = false, length = 10)
    public String moneda;

    @Column(name = "mostrar_secciones_vacias", nullable = false)
    public boolean mostrarSeccionesVacias;

    @Column(name = "sufijos_seccion_activos", nullable = false)
    public boolean sufijosSeccionActivos;

    @Column(name = "mostrar_subtotales_seccion", nullable = false)
    public boolean mostrarSubtotalesSeccion;

    @Column(name = "mostrar_subtotales_pie", nullable = false)
    public boolean mostrarSubtotalesPie;

    @Column(name = "mostrar_nombre_proyecto_header", nullable = false)
    public boolean mostrarNombreProyectoHeader;

    @Column(name = "enumerar_apus", nullable = false)
    public boolean enumerarApus;

    @Column(name = "mensaje_footer", nullable = false)
    public String mensajeFooter;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_codigo_rubro", nullable = false, length = 12)
    public ModoCodigoRubro modoCodigoRubro;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}