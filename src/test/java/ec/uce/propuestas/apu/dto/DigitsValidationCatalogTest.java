package ec.uce.propuestas.apu.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import jakarta.validation.constraints.Digits;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TDD T4 — Catálogo cerrado de {@code @Digits(integer=8, fraction=2)}.
 *
 * <p>Verifica que cada campo del catálogo cerrado lleva la anotación y
 * que campos no monetarios (cantidad, rendimiento, porcentajes) NO la llevan.
 *
 * <p>El número entero 8 proviene de la restricción de la BD:
 * {@code NUMERIC(14,6)} ⇒ 8 dígitos enteros como máximo. Aplicar
 * {@code integer=12} aceptaría valores que la BD rechazaría; usamos la
 * excepción explícita del plan ("unless existing numeric constraints require
 * smaller integer").
 */
class DigitsValidationCatalogTest {

    /**
     * Catálogo cerrado. Cada tupla: (Clase DTO record, nombre del campo
     * anotado, descripción textual).
     */
    private record CatalogEntry(Class<?> recordType, String fieldName, String descripcion) {}

    private static final List<CatalogEntry> CATALOG = List.of(
            new CatalogEntry(ApuDetallePatchRequest.class, "precioOverride", "ApuDetallePatchRequest.precioOverride"),
            new CatalogEntry(InsumoCrearRequest.class, "precioUnitario", "InsumoCrearRequest.precioUnitario"),
            new CatalogEntry(InsumoEditarRequest.class, "precioUnitario", "InsumoEditarRequest.precioUnitario"));

    @Test
    void catalog_fields_have_digits_integer_8_fraction_2() throws Exception {
        for (CatalogEntry entry : CATALOG) {
            Digits d = findDigitsOnRecordComponent(entry.recordType(), entry.fieldName());
            assertEquals(8, d.integer(), entry.descripcion() + " @Digits.integer");
            assertEquals(2, d.fraction(), entry.descripcion() + " @Digits.fraction");
        }
    }

    @Test
    void no_other_dto_field_has_digits() {
        // Walk every record component we list and confirm no other component carries @Digits.
        Class<?>[] watched = {ApuDetallePatchRequest.class, InsumoCrearRequest.class, InsumoEditarRequest.class};
        for (Class<?> cls : watched) {
            for (RecordComponent rc : cls.getRecordComponents()) {
                boolean isCatalog = CATALOG.stream()
                        .anyMatch(
                                e -> e.recordType().equals(cls) && e.fieldName().equals(rc.getName()));
                if (isCatalog) continue;
                assertTrue(
                        rc.getAnnotation(Digits.class) == null,
                        cls.getSimpleName() + "." + rc.getName() + " must NOT carry @Digits");
            }
        }
    }

    @Test
    void non_monetary_fields_have_no_digits() throws Exception {
        // Spot-checks de campos no monetarios de DTOs del dominio APU.
        assertNoDigits(ApuDetalleCrearRequest.class, "cantidad");
        assertNoDigits(ApuDetalleCrearRequest.class, "rendimiento");
    }

    /**
     * Localiza la anotación {@link Digits} en un componente de record. Como
     * {@code JsonNullable<BigDecimal>} y {@code BigDecimal} son anotados en el
     * parámetro de tipo del componente, basta con leer la anotación del
     * {@link RecordComponent} (los records preservan anotaciones declaradas
     * en el header).
     */
    private static Digits findDigitsOnRecordComponent(Class<?> recordType, String componentName) {
        try {
            for (RecordComponent rc : recordType.getRecordComponents()) {
                if (rc.getName().equals(componentName)) {
                    Digits d = rc.getAnnotation(Digits.class);
                    if (d == null) {
                        // Fallback: reflect on the underlying field. Some annotation
                        // configurations attach the constraint to the field rather
                        // than the record component.
                        Field f = recordType.getDeclaredField(componentName);
                        d = f.getAnnotation(Digits.class);
                    }
                    if (d == null) {
                        throw new AssertionError(
                                recordType.getSimpleName() + "." + componentName + " has no @Digits annotation");
                    }
                    return d;
                }
            }
            throw new AssertionError(recordType.getSimpleName() + "." + componentName + " record component not found");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertNoDigits(Class<?> recordType, String componentName) throws Exception {
        for (RecordComponent rc : recordType.getRecordComponents()) {
            if (rc.getName().equals(componentName)) {
                assertTrue(
                        rc.getAnnotation(Digits.class) == null,
                        recordType.getSimpleName() + "." + componentName + " must NOT carry @Digits");
                return;
            }
        }
        throw new AssertionError(recordType.getSimpleName() + "." + componentName + " record component not found");
    }
}
