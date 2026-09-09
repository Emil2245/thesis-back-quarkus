package ec.uce.propuestas.motor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * RED — Plan 015 N04 retirement (P-24 / S-24 withdrawn).
 *
 * <p>The descuento seam MUST NOT survive in the motor classes after Plan 015
 * is implemented. This is the {@code T1} motor half of
 * {@code plans/015-retirar-descuento-apu.md}.
 *
 * <p>Uses the Java Record component API ({@code Class.getRecordComponents()})
 * — the compile-time type-checked surface for record fields — instead of
 * generic reflection. Each test asserts the absence of a specific record
 * component by string match against {@code RecordComponent.getName()}.
 *
 * <p>RED today: every assertion fails because the seam is still present
 * ({@code porcentajeDescuento} on {@link ParametrosCalculo},
 * {@code costoDirectoAjustado} on {@link ApuCalculado}).
 * GREEN once Plan 015 retires the seam.
 */
class DescuentoRetiradoMotorTest {

    @Test
    void ParametrosCalculo_no_expone_porcentajeDescuento() {
        assertNoRecordComponent(ParametrosCalculo.class, "porcentajeDescuento");
    }

    @Test
    void ApuCalculado_no_expone_costoDirectoAjustado() {
        assertNoRecordComponent(ApuCalculado.class, "costoDirectoAjustado");
    }

    /**
     * Triangulation — the seam removal must NOT delete the surviving fields.
     * {@code porcentajeHerramientaMenor} (HM) and {@code porcentajeIndirectoDefault}
     * are still part of the motor contract after Plan 015.
     */
    @Test
    void ParametrosCalculo_conserva_porcentajeHerramientaMenor_y_porcentajeIndirectoDefault() {
        assertHasRecordComponent(ParametrosCalculo.class, "porcentajeHerramientaMenor");
        assertHasRecordComponent(ParametrosCalculo.class, "porcentajeIndirectoDefault");
    }

    @Test
    void ApuCalculado_conserva_costoDirecto_costoIndirecto_costoTotal() {
        assertHasRecordComponent(ApuCalculado.class, "costoDirecto");
        assertHasRecordComponent(ApuCalculado.class, "costoIndirecto");
        assertHasRecordComponent(ApuCalculado.class, "costoTotal");
    }

    private static void assertNoRecordComponent(Class<?> type, String name) {
        boolean found = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name::equals);
        assertFalse(found, "Plan 015: " + type.getSimpleName() + " must not expose record component '" + name + "'");
    }

    private static void assertHasRecordComponent(Class<?> type, String name) {
        boolean found = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name::equals);
        assertTrue(found, "Plan 015: " + type.getSimpleName() + " must keep record component '" + name + "'");
    }
}
