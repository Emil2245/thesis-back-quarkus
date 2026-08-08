package ec.uce.propuestas.insumo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unidad_catalogo")
public class UnidadCatalogo extends PanacheEntityBase {

    @Id
    @Column(nullable = false, length = 10)
    public String codigo;

    @Column(nullable = false, length = 100)
    public String descripcion;
}