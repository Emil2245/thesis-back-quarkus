package ec.uce.propuestas.apu.entity;

import ec.uce.propuestas.motor.SeccionTipo;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "apu_seccion")
public class ApuSeccion extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "apu_id", nullable = false)
    public Long apuId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public SeccionTipo tipo;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    public Short orden;
}