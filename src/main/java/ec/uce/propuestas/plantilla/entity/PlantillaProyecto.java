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
 * Plantilla de proyecto (estructural; sin referencias APU↔APU). WU-03 —
 * identidad externa inmutable {@code publicId} UUIDv7 generada por la
 * columna {@code public_id}. El {@code snapshot_estructura} se persiste como
 * JSONB con la forma canónica de capítulos + estructura.
 */
@Entity
@Table(name = "plantilla_proyecto")
public class PlantillaProyecto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "usuario_id", nullable = false)
    public Long usuarioId;

    @Column(nullable = false)
    public String nombre;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    public Instant fechaCreacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_estructura", nullable = false, columnDefinition = "jsonb")
    public String snapshotEstructura;

    @PrePersist
    void onInsert() {
        if (fechaCreacion == null) {
            fechaCreacion = Instant.now();
        }
    }
}
