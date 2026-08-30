package ec.uce.propuestas.insumo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "insumo")
public class Insumo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "base_id", nullable = false)
    public Long baseId;

    @Column(nullable = false, length = 50)
    public String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public TipoInsumo tipo;

    @Column(nullable = false)
    public String descripcion;

    @Column(nullable = false, length = 10)
    public String unidad;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 6)
    public BigDecimal precioUnitario;

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
