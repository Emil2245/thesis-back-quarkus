package ec.uce.propuestas.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * TDD T2 — Reflexión sobre los records públicos: ningún campo auxiliar
 * obsoleto (no-links); {@code ApuSnapshot.porcentajeIndirecto} presente
 * (nullable) como override per-APU.
 *
 * <p>Plan 014: el motor opera con APUs ordinarios e independientes (N04 §2);
 * los campos {@code esAuxiliar}, {@code cdAuxiliar} y
 * {@code ParametrosCalculo.porcentajeIndirectoApu} se retiran.
 */
class SnapshotSinAuxiliaresTest {

    @Test
    void ApuSnapshot_no_expone_esAuxiliar() {
        assertThrows(NoSuchFieldException.class, () -> ApuSnapshot.class.getDeclaredField("esAuxiliar"));
    }

    @Test
    void ApuSnapshot_expone_porcentajeIndirecto_nullable() throws Exception {
        assertEquals(
                BigDecimal.class,
                ApuSnapshot.class.getDeclaredField("porcentajeIndirecto").getType(),
                "ApuSnapshot.porcentajeIndirecto must be BigDecimal");
    }

    @Test
    void ApuCalculado_no_expone_esAuxiliar() {
        assertThrows(NoSuchFieldException.class, () -> ApuCalculado.class.getDeclaredField("esAuxiliar"));
    }

    @Test
    void FilaSnapshot_no_expone_cdAuxiliar() {
        assertThrows(NoSuchFieldException.class, () -> FilaSnapshot.class.getDeclaredField("cdAuxiliar"));
    }

    @Test
    void ParametrosCalculo_no_expone_porcentajeIndirectoApu() {
        assertThrows(
                NoSuchFieldException.class, () -> ParametrosCalculo.class.getDeclaredField("porcentajeIndirectoApu"));
    }

    @Test
    void ParametrosCalculo_conserva_porcentajeIndirectoDefault() throws Exception {
        assertTrue(
                BigDecimal.class.equals(ParametrosCalculo.class
                        .getDeclaredField("porcentajeIndirectoDefault")
                        .getType()),
                "ParametrosCalculo must retain porcentajeIndirectoDefault (project-level default)");
    }
}
