package ec.uce.propuestas.cronograma.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Plan 027 — entidad del agregado de cronograma (V001 §2.13 + V009). Sin
 * {@code created_at} (la tabla no lo declara); sólo conserva el
 * {@code updated_at} que refresca la BD al copiarse una versión.
 *
 * * La identidad externa inmutable {@code publicId} UUIDv7 se mapea con
 * {@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 * updatable=false}; el FK interno {@code presupuestoId} permanece
 * {@code Long} (BIGINT).
 * * {@code unidadTiempo} y {@code numeroPeriodos} están limitados por la
 *   CHECK canónica de V009 ({@code SEMANA 1..520 | MES 1..120}).
 * * {@code presupuestoFingerprintRevisado} es CHAR(64) nullable en BD; el
 *   setter Java acepta {@code String} normalizado a 64 chars lowercase
 *   hex. El {@code null} representa "sin revisión capturada".
 */
@Entity
@Table(name = "cronograma")
public class Cronograma extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(name = "unidad_tiempo", nullable = false, length = 10)
    public String unidadTiempo;

    @Column(name = "numero_periodos", nullable = false)
    public Short numeroPeriodos;

    @Column(name = "total_general_revisado", precision = 14, scale = 6)
    public BigDecimal totalGeneralRevisado;

    @Column(name = "fecha_revision")
    public Instant fechaRevision;

    /**
     * Fingerprint SHA-256 lowercase de 64 chars sobre la configuración
     * revisada del presupuesto. La BD lo acepta como {@code NULL} (sin
     * revisión capturada) o como una cadena de exactamente 64 chars
     * lowercase hex (CHECK de V009). El {@code @JdbcTypeCode(SqlTypes.CHAR)}
     * ancla el tipo físico a {@code CHAR(64)} para que el validador de Hibernate
     * no se queje del {@code bpchar} nativo de PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "presupuesto_fingerprint_revisado", length = 64, columnDefinition = "char(64)")
    public String presupuestoFingerprintRevisado;
}
