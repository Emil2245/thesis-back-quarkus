package ec.uce.propuestas.usuario;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

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
    void onInsert() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
