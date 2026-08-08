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

@Entity
@Table(name = "parametros_proyecto")
public class ParametrosProyecto extends PanacheEntityBase {

    @Id
    @Column(name = "proyecto_id", nullable = false)
    public Long proyectoId;

    @Column(name = "porcentaje_herramienta_menor", nullable = false, precision = 5, scale = 4)
    public BigDecimal porcentajeHerramientaMenor = new BigDecimal("0.0500");

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(nullable = false, precision = 5, scale = 4)
    public BigDecimal iva = new BigDecimal("0.1500");

    @Column(nullable = false, length = 10)
    public String moneda = "USD";

    @Column(name = "mostrar_secciones_vacias", nullable = false)
    public boolean mostrarSeccionesVacias = true;

    @Column(name = "sufijos_seccion_activos", nullable = false)
    public boolean sufijosSeccionActivos = true;

    @Column(name = "mostrar_subtotales_seccion", nullable = false)
    public boolean mostrarSubtotalesSeccion = true;

    @Column(name = "mostrar_subtotales_pie", nullable = false)
    public boolean mostrarSubtotalesPie = false;

    @Column(name = "mostrar_nombre_proyecto_header", nullable = false)
    public boolean mostrarNombreProyectoHeader = false;

    @Column(name = "enumerar_apus", nullable = false)
    public boolean enumerarApus = false;

    @Column(name = "mensaje_footer", nullable = false)
    public String mensajeFooter = "Este precio no incluye IVA";

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_codigo_rubro", nullable = false, length = 12)
    public ModoCodigoRubro modoCodigoRubro = ModoCodigoRubro.AUTOGENERADO;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void onWrite() { updatedAt = Instant.now(); }
}