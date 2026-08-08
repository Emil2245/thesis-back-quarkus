package ec.uce.propuestas.insumo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "insumo")
public class Insumo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

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
    void onInsert() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}