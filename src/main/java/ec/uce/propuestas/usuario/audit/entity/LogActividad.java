package ec.uce.propuestas.usuario.audit.entity;

import ec.uce.propuestas.usuario.Usuario;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/** Append-only activity row. Internal BIGINT identities never cross the REST boundary. */
@Entity
@Table(name = "log_actividad")
public class LogActividad extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false, nullable = false)
    public UUID publicId;

    /** Sole writable mapping of the actor FK; assign this field when emitting. */
    @Column(name = "usuario_id")
    public Long usuarioId;

    /** Read-only association used only to hydrate the public actor projection. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false)
    public Usuario usuario;

    @Column(nullable = false, length = 60)
    public String evento;

    @Column(length = 30)
    public String entidad;

    /** V001 historical identity. It is persisted only for legacy reads and is never exposed. */
    @Column(name = "entidad_id", insertable = false, updatable = false)
    public Long entidadIdLegacy;

    @Column(name = "entidad_public_id")
    public UUID entidadPublicId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String detalle;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    public Instant createdAt;
}
