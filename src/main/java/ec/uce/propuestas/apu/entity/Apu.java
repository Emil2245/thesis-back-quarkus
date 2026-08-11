package ec.uce.propuestas.apu.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "apu")
public class Apu extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(nullable = false, length = 20)
    public String codigo;

    @Column(nullable = false)
    public String descripcion;

    @Column(nullable = false, length = 10)
    public String unidad;

    @Column(name = "es_auxiliar", nullable = false)
    public boolean esAuxiliar;

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 4)
    public BigDecimal porcentajeDescuento = BigDecimal.ZERO;

    @Column(name = "costo_directo", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoDirecto = BigDecimal.ZERO;

    @Column(name = "costo_indirecto", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoIndirecto = BigDecimal.ZERO;

    @Column(name = "costo_total", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoTotal = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onInsert() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}