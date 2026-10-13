package ec.uce.propuestas.recalculo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/** Small SQL fixtures shared by snapshot batch integration tests. */
final class VersionSnapshotBatchFixtures {
    private final Connection connection;

    VersionSnapshotBatchFixtures(Connection connection) {
        this.connection = connection;
    }

    long presupuesto() throws Exception {
        long usuario = insertar(
                "INSERT INTO usuario (nombre,email,password_hash,email_verificado,activo) "
                        + "VALUES ('batch',?,'x',TRUE,TRUE) RETURNING id",
                UUID.randomUUID() + "@batch.test");
        long proyecto = insertar(
                "INSERT INTO proyecto (usuario_id,nombre_proyecto,anio,plazo_ejecucion,"
                        + "plazo_unidad,estado,direccion_institucional) "
                        + "VALUES (?,'Batch',2026,4,'MES','BORRADOR','UCE') RETURNING id",
                usuario);
        insertar(
                "INSERT INTO parametros_proyecto (proyecto_id,porcentaje_herramienta_menor,"
                        + "porcentaje_indirecto,iva,moneda) VALUES (?,0.05,0.18,0.15,'USD') RETURNING proyecto_id",
                proyecto);
        return insertar(
                "INSERT INTO presupuesto (proyecto_id,version,es_vigente) " + "VALUES (?,1,TRUE) RETURNING id",
                proyecto);
    }

    long capitulo(long presupuesto, Long padre, String item, int orden) throws Exception {
        return insertar(
                "INSERT INTO capitulo (presupuesto_id,parent_id,item,descripcion,orden,total) "
                        + "VALUES (?,?,?, ?,?,0) RETURNING id",
                presupuesto,
                padre,
                item,
                item,
                orden);
    }

    void rubro(long presupuesto, long capitulo, String item, String codigo) throws Exception {
        long apu = insertar(
                "INSERT INTO apu (presupuesto_id,codigo,descripcion,unidad) " + "VALUES (?,?,?,'u') RETURNING id",
                presupuesto,
                codigo,
                codigo);
        insertar(
                "INSERT INTO rubro (capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,"
                        + "precio_unitario,precio_total) VALUES (?,?,?,?,?,'u',2,0,0) RETURNING id",
                capitulo,
                apu,
                item,
                codigo,
                codigo);
    }

    private long insertar(String sql, Object... valores) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                statement.setObject(i + 1, valores[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}
