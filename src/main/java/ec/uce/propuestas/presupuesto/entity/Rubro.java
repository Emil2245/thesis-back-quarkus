package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Rubro del árbol presupuestario (V001 §2.11). Vínculo 1:1 con un APU por
 * versión (D-09, restricción única en {@code apu_id}). Sin
 * {@code created_at} / {@code updated_at} ni callbacks de ciclo de vida — el
 * DDL canónico no los declara. La identidad externa inmutable
 * {@code publicId} UUIDv7 se mapea con
 * {@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 * updatable=false}; los FK {@code capituloId} y {@code apuId} permanecen
 * BIGINT (internos).
 *
 * <p>La columna {@code cantidad} admite {@code >= 0} (V007 estructural) para
 * soportar rubros pendientes reconstruidos desde plantilla de proyecto
 * (Plan 06). La API REST (Plan 023) sigue exigiendo {@code cantidad > 0}
 * vía los DTOs.
 */
@Entity
@Table(name = "rubro")
public class Rubro extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

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
    public BigDecimal cantidad = BigDecimal.ZERO;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 6)
    public BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(name = "precio_total", nullable = false, precision = 14, scale = 6)
    public BigDecimal precioTotal = BigDecimal.ZERO;
}
