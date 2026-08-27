package ec.uce.propuestas.apu.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "apu_detalle")
public class ApuDetalle extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "seccion_id", nullable = false)
    public Long seccionId;

    @Column(name = "insumo_id")
    public Long insumoId;

    @Column(nullable = false)
    public String descripcion;

    @Column(nullable = false)
    public Short orden;

    @Column(name = "es_herramienta_menor", nullable = false)
    public boolean esHerramientaMenor;

    @Column(precision = 12, scale = 6)
    public BigDecimal cantidad;

    @Column(name = "tarifa_jornal", precision = 14, scale = 6)
    public BigDecimal tarifaJornal;

    @Column(name = "costo_hora", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoHora = BigDecimal.ZERO;

    @Column(precision = 10, scale = 6)
    public BigDecimal rendimiento;

    @Column(length = 10)
    public String unidad;

    @Column(name = "precio_unitario_tarifa", precision = 14, scale = 6)
    public BigDecimal precioUnitarioTarifa;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal costo = BigDecimal.ZERO;
}
