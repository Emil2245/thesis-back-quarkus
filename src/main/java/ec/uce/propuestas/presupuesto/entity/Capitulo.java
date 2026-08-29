package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "capitulo")
public class Capitulo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(name = "parent_id")
    public Long parentId;

    @Column(nullable = false, length = 20)
    public String item;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String descripcion;

    @Column(nullable = false)
    public Short orden;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal total = BigDecimal.ZERO;
}
