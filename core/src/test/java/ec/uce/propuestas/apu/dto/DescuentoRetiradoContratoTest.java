package ec.uce.propuestas.apu.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * RED — Plan 015 N04 retirement (P-24 / S-24 withdrawn).
 *
 * <p>DTO contract surface: the public JSON contracts of {@code apu/} must not
 * expose the {@code porcentajeDescuento} seam after Plan 015 closes. This is
 * the {@code T2} contract half of {@code plans/015-retirar-descuento-apu.md}.
 *
 * <p>Compiles against the {@code Class<Record>} signature (the type itself is
 * referenced, not specific fields), so the test stays compilable both before
 * and after the field retirement. The assertion runs against
 * {@code getRecordComponents()} — the official Record component API.
 *
 * <p>RED today: each assertion fails because the field is present.
 * GREEN once Plan 015 removes the field from the record.
 */
class DescuentoRetiradoContratoTest {

    @Test
    void ApuResponse_no_expone_porcentajeDescuento() {
        assertNoRecordComponent(ApuResponse.class, "porcentajeDescuento");
    }

    @Test
    void ApuCalculoParametros_no_expone_descuento() {
        assertNoRecordComponent(ApuCalculoParametros.class, "descuento");
    }

    @Test
    void ApuCalculoResumen_no_expone_cdAjustado() {
        assertNoRecordComponent(ApuCalculoResumen.class, "cdAjustado");
    }

    @Test
    void ApuCalculoResumen_no_expone_operacionCdAjustado() {
        assertNoRecordComponent(ApuCalculoResumen.class, "operacionCdAjustado");
    }

    private static void assertNoRecordComponent(Class<?> type, String name) {
        boolean found = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name::equals);
        assertFalse(found, "Plan 015: " + type.getSimpleName() + " must not expose record component '" + name + "'");
    }
}
