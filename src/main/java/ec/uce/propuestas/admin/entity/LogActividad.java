package ec.uce.propuestas.admin.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "log_actividad")
public class LogActividad extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_id")
    public Long usuarioId;

    @Column(nullable = false, length = 60)
    public String evento;

    @Column(length = 30)
    public String entidad;

    @Column(name = "entidad_id")
    public Long entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> detalle;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onInsert() {
        createdAt = Instant.now();
    }
}
