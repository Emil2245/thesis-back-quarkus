package ec.uce.propuestas.insumo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "base_insumos")
public class BaseInsumos extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(nullable = false, length = 200)
    public String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public TipoBase tipo;

    @Column(name = "proyecto_id")
    public Long proyectoId;

    /**
     * WU-03/WU-05 — FK interno BIGINT hacia {@code usuario(id)}. Lo exigen las bases
     * {@code tipo=PERSONAL}; el CHECK del esquema garantiza la coherencia:
     * {@code PERSONAL ⇒ usuario_id NOT NULL}, {@code CENTRAL ⇒ usuario_id NULL}.
     */
    @Column(name = "usuario_id")
    public Long usuarioId;

    @Column(nullable = false)
    public boolean archivada = false;

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
