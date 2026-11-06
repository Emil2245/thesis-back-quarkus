package ec.uce.propuestas.plantilla.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Plantilla de APU (SISTEMA o PERSONAL). WU-03 — identidad externa inmutable
 * {@code publicId} UUIDv7 generada por la columna {@code public_id}. El
 * {@code snapshot_secciones} se persiste como JSONB con forma canónica
 * (sin {@code apuAuxiliarId}); las claves heredadas {@code tarifaJornal} y
 * {@code costo} se toleran en lectura pero nunca se escriben.
 */
@Entity
@Table(name = "plantilla_apu")
public class PlantillaApu extends PanacheEntityBase {

    public enum Tipo {
        SISTEMA,
        PERSONAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(nullable = false)
    public String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Tipo tipo;

    @Column(name = "usuario_id")
    public Long usuarioId;

    @Column(name = "descripcion_rubro")
    public String descripcionRubro;

    @Column(length = 10)
    public String unidad;

    @Column(name = "especificacion_tecnica", columnDefinition = "TEXT")
    public String especificacionTecnica;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_secciones", nullable = false, columnDefinition = "jsonb")
    public String snapshotSecciones;

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
