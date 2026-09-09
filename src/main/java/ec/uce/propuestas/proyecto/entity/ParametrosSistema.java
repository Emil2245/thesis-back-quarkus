package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Parámetros globales de cálculo (una sola fila, id=1). Los rangos configurables de
 * HM/CI/IVA/descuento son la fuente autoritativa para validar
 * {@code PUT /proyectos/{id}/parametros}; la escritura está restringida a
 * SUPER_ADMIN.
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

    @Column(name = "rango_hm_min", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoHmMin = new BigDecimal("0.0000");

    @Column(name = "rango_hm_max", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoHmMax = new BigDecimal("0.2000");

    @Column(name = "rango_ci_min", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoCiMin = new BigDecimal("0.0000");

    @Column(name = "rango_ci_max", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoCiMax = new BigDecimal("1.0000");

    @Column(name = "rango_descuento_min", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoDescuentoMin = new BigDecimal("0.0000");

    @Column(name = "rango_descuento_max", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoDescuentoMax = new BigDecimal("0.5000");

    @Column(name = "rango_iva_min", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoIvaMin = new BigDecimal("0.0000");

    @Column(name = "rango_iva_max", nullable = false, precision = 5, scale = 4)
    public BigDecimal rangoIvaMax = new BigDecimal("0.3000");

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
