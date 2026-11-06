package ec.uce.propuestas.cronograma.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Plan 027 — entidad de la actividad 1:1 que cuelga de un cronograma
 * (V001 §2.13 + V009).
 *
 * * La identidad externa inmutable {@code publicId} UUIDv7 se mapea con
 *   {@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 *   updatable=false}; los FK internos {@code cronogramaId} y
 *   {@code rubroId} permanecen {@code Long} (BIGINT).
 * * {@code pesoPonderado} y {@code avancePorPeriodo} se persisten con la
 *   escala del DDL: peso NUMERIC(7,4) y JSONB. Se conserva la forma
 *   {@code String} para serialización (no se introduce {@code double} ni
 *   {@code float} en la ruta de persistencia).
 * * La BD impone la UNIQUE {@code (rubro_id)} (D-09 — 1 actividad por
 *   rubro) y la FK {@code cronograma_id → cronograma.id}.
 */
@Entity
@Table(name = "actividad")
public class Actividad extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "cronograma_id", nullable = false)
    public Long cronogramaId;

    @Column(name = "rubro_id", nullable = false, unique = true)
    public Long rubroId;

    @Column(name = "peso_ponderado", nullable = false, precision = 7, scale = 4)
    public BigDecimal pesoPonderado = BigDecimal.ZERO;

    /**
     * Mapa JSONB persistido como {@code String}. La forma canónica usa
     * claves string de enteros 1..n y valores decimales a escala 4.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avance_por_periodo", nullable = false, columnDefinition = "jsonb")
    public String avancePorPeriodo;
}
