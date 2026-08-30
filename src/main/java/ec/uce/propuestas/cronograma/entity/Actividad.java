package ec.uce.propuestas.cronograma.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "actividad")
public class Actividad extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "cronograma_id", nullable = false)
    public Long cronogramaId;

    @Column(name = "rubro_id", nullable = false, unique = true)
    public Long rubroId;

    @Column(name = "peso_ponderado", nullable = false, precision = 7, scale = 4)
    public BigDecimal pesoPonderado = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avance_por_periodo", nullable = false, columnDefinition = "jsonb")
    public Map<String, BigDecimal> avancePorPeriodo = new HashMap<>();
}
