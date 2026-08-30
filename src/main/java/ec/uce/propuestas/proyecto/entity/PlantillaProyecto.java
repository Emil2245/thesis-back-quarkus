package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plantilla_proyecto")
public class PlantillaProyecto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_id", nullable = false)
    public Long usuarioId;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String nombre;

    @Column(columnDefinition = "TEXT")
    public String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public Map<String, Object> snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onInsert() {
        createdAt = Instant.now();
    }
}
