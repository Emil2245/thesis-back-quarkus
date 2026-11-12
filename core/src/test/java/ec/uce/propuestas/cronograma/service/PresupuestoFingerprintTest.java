package ec.uce.propuestas.cronograma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.service.PresupuestoFingerprint.RubroSnapshot;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresupuestoFingerprintTest {

    @Test
    void fingerprint_es_determinista_independiente_del_orden_y_sensible_a_campos() {
        RubroSnapshot a = new RubroSnapshot("1", "1.1", "A|B", new BigDecimal("2"));
        RubroSnapshot b = new RubroSnapshot("2", "1.1", "C", new BigDecimal("3.000000"));

        String primero = PresupuestoFingerprint.calcular(List.of(a, b));
        String invertido = PresupuestoFingerprint.calcular(List.of(b, a));
        String cambiado = PresupuestoFingerprint.calcular(
                List.of(a, new RubroSnapshot("2", "1.1", "C", new BigDecimal("3.000001"))));

        assertEquals(64, primero.length());
        assertEquals(primero, invertido);
        assertNotEquals(primero, cambiado);
    }

    @Test
    void serializacion_con_longitudes_no_colisiona_por_delimitadores() {
        String uno = PresupuestoFingerprint.calcular(List.of(new RubroSnapshot("a|b", "c", "d", new BigDecimal("1"))));
        String otro = PresupuestoFingerprint.calcular(List.of(new RubroSnapshot("a", "b|c", "d", new BigDecimal("1"))));
        assertNotEquals(uno, otro);
    }

    @Test
    void solo_23505_de_la_constraint_del_presupuesto_se_traduce_a_conflicto() {
        assertTrue(CronogramaService.esUniqueCronogramaPresupuesto(
                new PersistenceException(new SQLException("duplicate key cronograma_presupuesto_id_key", "23505"))));
        assertFalse(CronogramaService.esUniqueCronogramaPresupuesto(
                new PersistenceException(new SQLException("duplicate key actividad_rubro_id_key", "23505"))));
        assertFalse(CronogramaService.esUniqueCronogramaPresupuesto(
                new PersistenceException(new SQLException("cronograma_presupuesto_id_key", "23503"))));
        assertFalse(CronogramaService.esUniqueCronogramaPresupuesto(new PersistenceException("fallo no SQL")));
    }
}
