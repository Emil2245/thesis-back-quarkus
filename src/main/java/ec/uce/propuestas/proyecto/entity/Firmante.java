package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "firmante")
public class Firmante extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "proyecto_id", nullable = false)
    public Long proyectoId;

    @Column(nullable = false, length = 200)
    public String nombre;

    @Column(nullable = false, length = 300)
    public String cargo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public RolFirmante rol;

    @Column(nullable = false)
    public Short orden;
}
