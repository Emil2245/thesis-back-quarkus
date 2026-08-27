package ec.uce.propuestas.common;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Canonical UUIDv7 parser/validator for the public contract (OpenSpec WU-03 boundary).
 *
 * <p>UUIDv7 layout (RFC 9562 §5.7):
 * <ul>
 *   <li>48-bit Unix-time-ms prefix (sortable).</li>
 *   <li>4-bit version nibble at bits 48-51 — must be {@code 7}.</li>
 *   <li>2-bit RFC 4122 variant bits at bits 64-65 — top two bits must be {@code 10}
 *       (the first hex nibble of the 4th group must be {@code 8}, {@code 9},
 *       {@code a}, or {@code b}).</li>
 * </ul>
 *
 * <p>This validator lives at the resource boundary so that malformed or non-v7
 * strings reject with the existing {@code validacion} 400 contract (RNF-05) before
 * any repository access. The DB column still permits arbitrary UUIDs (the immutability
 * trigger is on {@code public_id} in general), but the public API narrows the surface
 * to v7 so the response shape is consistent and the time-ordered prefix is meaningful
 * for clients (sortable, opaque, de-tabbable from internal {@code BIGINT}).
 */
public final class UuidV7 {

    /** RFC 4122 variant 8/9/a/b + version nibble 7. Case-insensitive. */
    private static final Pattern V7_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private UuidV7() {}

    /**
     * Parses {@code raw} as a UUIDv7. Returns the parsed {@link UUID} on success.
     *
     * @throws ProblemaException with status 400 {@code validacion} when {@code raw}
     *         is {@code null}, malformed, not a UUID, or not version 7.
     */
    public static UUID parse(String raw) {
        if (raw == null || !V7_PATTERN.matcher(raw).matches()) {
            throw ProblemaException.validacion("Identificador público inválido: se requiere UUIDv7");
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            // Defensa de cinturón-y-tirantes: la regex ya cubre los formatos canónicos,
            // pero un input con espacios o caracteres extra podría colarse.
            throw ProblemaException.validacion("Identificador público inválido: se requiere UUIDv7");
        }
        if (parsed.version() != 7) {
            throw ProblemaException.validacion("Identificador público inválido: se requiere UUIDv7");
        }
        if (parsed.variant() != 2) {
            throw ProblemaException.validacion("Identificador público inválido: se requiere UUIDv7");
        }
        return parsed;
    }
}
