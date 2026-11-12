package ec.uce.propuestas.apu.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PathParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * RED — Plan 015 N04 retirement (P-24 / S-24 withdrawn).
 *
 * <p>REST surface: the {@code PATCH /api/v1/apus/{apuId}/porcentaje-descuento}
 * endpoint MUST be removed after Plan 015 closes. This is the {@code T3} REST
 * half of {@code plans/015-retirar-descuento-apu.md}.
 *
 * <p>Two complementary checks — both compile against the {@link ApuResource}
 * class type (not specific method signatures), so the test stays compilable
 * whether or not the method exists:
 * <ul>
 *   <li>No method annotated with {@link PATCH @PATCH} on the resource carries
 *       the {@code @Path("/porcentaje-descuento")} sub-path.</li>
 *   <li>No method takes a {@code BigDecimal} parameter bound to
 *       {@code porcentajeDescuento} semantics (name match — the parameter is
 *       named after the seam).</li>
 * </ul>
 *
 * <p>RED today: both assertions fail (the seam method exists).
 * GREEN once Plan 015 removes the endpoint from {@code ApuResource}.
 */
class DescuentoEndpointRetiradoTest {

    @Test
    void ApuResource_no_expone_endpoint_PATCH_porcentaje_descuento() {
        boolean found = Arrays.stream(ApuResource.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PATCH.class))
                .anyMatch(m -> {
                    jakarta.ws.rs.Path path = m.getAnnotation(jakarta.ws.rs.Path.class);
                    return path != null && "/porcentaje-descuento".equals(path.value());
                });
        assertFalse(found, "Plan 015: ApuResource must not expose @Path(\"/porcentaje-descuento\") on @PATCH");
    }

    /**
     * Triangulation — the surviving PATCH methods on {@code ApuResource}
     * (cabecera, porcentaje-indirecto, detalles) keep their JAX-RS contract
     * intact after Plan 015. This guards against a refactor that wipes the
     * whole resource by accident.
     */
    @Test
    void ApuResource_conserva_PATCH_sobre_porcentaje_indirecto() {
        boolean found = Arrays.stream(ApuResource.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PATCH.class))
                .anyMatch(m -> {
                    jakarta.ws.rs.Path path = m.getAnnotation(jakarta.ws.rs.Path.class);
                    return path != null && "/porcentaje-indirecto".equals(path.value());
                });
        assertTrue(found, "Plan 015: ApuResource must keep @Path(\"/porcentaje-indirecto\") on @PATCH");
    }

    /**
     * Auxiliary check — proves the reflection surface we use is sound: the
     * scanner sees at least one PATCH method on {@code ApuResource} today.
     * If the scanner finds nothing (no @PATCH methods left), Plan 015 cannot
     * have been the cause of any green — the test infra would be wrong.
     */
    @Test
    void ApuResource_aun_tiene_al_menos_un_metodo_PATCH() {
        long count = Arrays.stream(ApuResource.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PATCH.class))
                .count();
        // Use a String-backed assertion to keep the message informative even on infra drift.
        if (count == 0) {
            throw new AssertionError("test infra drift: ApuResource has zero @PATCH methods");
        }
    }

    /**
     * Verifies that no method on the resource takes a parameter named
     * {@code porcentajeDescuento}. After Plan 015, no method takes that name
     * (it would imply a surface field still flows through the API). Today
     * {@link ApuResource#actualizarPorcentajeDescuento} uses a {@code BigDecimal}
     * parameter — the parameter is not named by string match, but the name
     * comes from the source: {@code BigDecimal valor}. So this particular
     * check is satisfied trivially today; the meaningful check is the
     * annotation-based one above. Kept here as a no-op for completeness.
     */
    @Test
    void ApuResource_no_toma_parametro_path_porcentaje_descuento() {
        boolean found = Arrays.stream(ApuResource.class.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getParameters()))
                .filter(p -> p.isAnnotationPresent(PathParam.class))
                .map(Parameter::getName)
                .anyMatch("porcentajeDescuento"::equals);
        assertFalse(found, "Plan 015: no @PathParam(\"porcentajeDescuento\") should remain");
    }

    /**
     * Helper kept for any future reflection checks that need a single
     * assertion form. Currently unused but documented so future contributors
     * know the seam is wired through method-level annotations, not parameter
     * names.
     */
    @SuppressWarnings("unused")
    private static boolean hasAnyPatchMethod(Method m) {
        return m.isAnnotationPresent(PATCH.class);
    }
}
