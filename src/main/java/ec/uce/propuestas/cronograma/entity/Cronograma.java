package ec.uce.propuestas.cronograma.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cronograma")
public class Cronograma extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "presupuesto_id", nullable = false, unique = true)
    public Long presupuestoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_tiempo", nullable = false, length = 10)
    public UnidadTiempo unidadTiempo;

    @Column(name = "numero_periodos", nullable = false)
    public short numeroPeriodos;

    @Column(name = "total_general_revisado", precision = 14, scale = 6)
    public BigDecimal totalGeneralRevisado;

    @Column(name = "fecha_revision")
    public Instant fechaRevision;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onInsert() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
