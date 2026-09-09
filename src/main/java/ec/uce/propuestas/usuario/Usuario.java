package ec.uce.propuestas.usuario;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * Identidad externa inmutable (UUIDv7) generada por la columna
     * {@code public_id UUID DEFAULT uuidv7()} de PostgreSQL 18. Hibernate la
     * relee después del INSERT; {@code insertable=false, updatable=false}
     * garantiza que NUNCA se serializa en SQL de inserción ni actualización.
     * El trigger {@code trg_public_id_immutable} lo defiende como red de
     * seguridad a nivel de base para rutas JDBC/SQL directas.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(nullable = false, length = 200)
    public String nombre;

    @Column(nullable = false, unique = true, length = 320)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public Rol rol = Rol.USUARIO;

    @Column(name = "email_verificado", nullable = false)
    public boolean emailVerificado = false;

    @Column(nullable = false)
    public boolean activo = true;

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
