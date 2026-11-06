package ec.uce.propuestas.proyecto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Valor informativo administrado por P-41; nunca participa en el motor. */
@Entity
@Table(name = "valor_referencia")
public class ValorReferencia extends PanacheEntityBase {

    @Id
    @Column(nullable = false, length = 50)
    public String clave;

    @Column(nullable = false, length = 100)
    public String valor;

    @Column(nullable = false, columnDefinition = "text")
    public String descripcion;

    @Column(nullable = false, length = 200)
    public String fuente;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
