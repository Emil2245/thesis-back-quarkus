package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rubro")
public class Rubro extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "capitulo_id", nullable = false)
    public Long capituloId;

    @Column(name = "apu_id", nullable = false, unique = true)
    public Long apuId;

    @Column(nullable = false, length = 20)
    public String item;

    @Column(nullable = false, length = 20)
    public String codigo;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String descripcion;

    @Column(nullable = false, length = 10)
    public String unidad;

    @Column(nullable = false, precision = 12, scale = 6)
    public BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 6)
    public BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(name = "precio_total", nullable = false, precision = 14, scale = 6)
    public BigDecimal precioTotal = BigDecimal.ZERO;
}
