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

    long apus(int count) throws Exception {
        long presupuesto = presupuesto();
        long foreign = presupuesto();
        ejecutar(
                "UPDATE parametros_proyecto SET porcentaje_herramienta_menor=0.07 "
                        + "WHERE proyecto_id=(SELECT proyecto_id FROM presupuesto WHERE id=?)",
                foreign);
        long capitulo = capitulo(presupuesto, null, "1", 1);
        long base = insertar("INSERT INTO base_insumos (nombre,tipo) VALUES ('Batch','CENTRAL') RETURNING id");
        ejecutar(
                "INSERT INTO insumo (base_id,codigo,tipo,descripcion,unidad,precio_unitario) "
                        + "SELECT ?,tipo,tipo,tipo,CASE WHEN tipo IN ('EQUIPO','MANO_OBRA') THEN 'h' ELSE 'u' END,"
                        + "10+n FROM (VALUES ('EQUIPO',1),('MANO_OBRA',2),('MATERIAL',3),('TRANSPORTE',4)) t(tipo,n)",
                base);
        ejecutar(
                "INSERT INTO apu (presupuesto_id,codigo,descripcion,unidad,porcentaje_indirecto) "
                        + "SELECT CASE WHEN n=? THEN ? ELSE ? END,'A'||n,'APU '||n,'u',"
                        + "CASE WHEN n%2=0 THEN 0.12 ELSE NULL END FROM generate_series(1,?) n ORDER BY n DESC",
                count, foreign, presupuesto, count);
        ejecutar(
                "INSERT INTO rubro (capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad) "
                        + "SELECT ?,id,'1.'||substring(codigo FROM 2),codigo,descripcion,'u',2 FROM apu "
                        + "WHERE presupuesto_id IN (?,?)",
                capitulo,
                presupuesto,
                foreign);
        ejecutar(
                "INSERT INTO apu_seccion (apu_id,tipo,orden) SELECT a.id,t.tipo,t.n "
                        + "FROM apu a CROSS JOIN (VALUES ('TRANSPORTE',1),('MATERIAL',2),('MANO_OBRA',3),('EQUIPO',4)) t(tipo,n) "
                        + "WHERE a.presupuesto_id IN (?,?)",
                presupuesto,
                foreign);
        ejecutar(
                "INSERT INTO apu_detalle (seccion_id,insumo_id,descripcion,orden,cantidad,rendimiento,"
                        + "tarifa_jornal,precio_unitario_tarifa) SELECT s.id,CASE WHEN n<3 THEN i.id ELSE NULL END,"
                        + "'Row '||n,n,n+1,0.5,CASE WHEN n=2 AND s.tipo IN ('EQUIPO','MANO_OBRA') THEN 20 END,"
                        + "CASE WHEN n=2 AND s.tipo IN ('MATERIAL','TRANSPORTE') THEN 30 END "
                        + "FROM apu_seccion s JOIN apu a ON a.id=s.apu_id JOIN insumo i ON i.tipo=s.tipo AND i.base_id=? "
                        + "CROSS JOIN generate_series(1,3) n WHERE a.presupuesto_id IN (?,?) ORDER BY n DESC",
                base,
                presupuesto,
                foreign);
        ejecutar(
                "INSERT INTO apu_detalle (seccion_id,descripcion,orden,es_herramienta_menor) "
                        + "SELECT s.id,'HM',4,TRUE FROM apu_seccion s JOIN apu a ON a.id=s.apu_id "
                        + "WHERE s.tipo='EQUIPO' AND a.presupuesto_id IN (?,?)",
                presupuesto,
                foreign);
        // An unlinked APU in the same budget must not enter the version snapshot.
        ejecutar(
                "INSERT INTO apu (presupuesto_id,codigo,descripcion,unidad) VALUES (?,'excluded','excluded','u')",
                presupuesto);
        return presupuesto;
    }

    /** codigo de rubro -> id, para construir la clave unica del snapshot (plan 033). */
    java.util.Map<String, Long> rubroIds(long presupuesto) throws Exception {
        java.util.Map<String, Long> ids = new java.util.HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.codigo, r.id FROM rubro r JOIN capitulo c ON c.id=r.capitulo_id WHERE c.presupuesto_id=?")) {
            statement.setLong(1, presupuesto);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.put(result.getString(1), result.getLong(2));
                }
            }
        }
        return ids;
    }

    private void ejecutar(String sql, Object... valores) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                statement.setObject(i + 1, valores[i]);
            }
            statement.executeUpdate();
        }
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
