package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "proyecto")
public class Proyecto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "usuario_id", nullable = false)
    public Long usuarioId;

    @Column(name = "nombre_proyecto", nullable = false)
    public String nombreProyecto;

    @Column(length = 50)
    public String codigo;

    public String descripcion;

    @Column(nullable = false)
    public Short anio;

    @Column(name = "fecha_inicio")
    public LocalDate fechaInicio;

    @Column(name = "plazo_ejecucion")
    public Short plazoEjecucion;

    @Enumerated(EnumType.STRING)
    @Column(name = "plazo_unidad", length = 10)
    public PlazoUnidad plazoUnidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public EstadoProyecto estado = EstadoProyecto.BORRADOR;

    @Column(name = "direccion_institucional", nullable = false, length = 200)
    public String direccionInstitucional;

    @Column(name = "subdireccion_institucional", length = 200)
    public String subdireccionInstitucional;

    @Column(name = "logo")
    public byte[] logo;

    /** N04-bis P-45: cabecera del documento Word de ETs; default `null` → export usa "ESPECIFICACIONES TÉCNICAS". */
    @Column(name = "titulo_et_1")
    public String tituloEt1;

    /** N04-bis P-45: subcabecera del documento Word de ETs; default `null` → export usa `<nombre_proyecto>`. */
    @Column(name = "titulo_et_2")
    public String tituloEt2;

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
