package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "presupuesto")
public class Presupuesto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "proyecto_id", nullable = false)
    public Long proyectoId;

    @Column(nullable = false)
    public Short version;

    @Column(name = "es_vigente", nullable = false)
    public boolean esVigente;

    @Column(name = "origen_id")
    public Long origenId;

    @Column(columnDefinition = "TEXT")
    public String notas;

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onInsert() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
