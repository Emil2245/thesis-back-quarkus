package ec.uce.propuestas.documentos.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Loads {@link DocumentProjectionV1} fixtures from {@code src/test/resources/projections}. They were produced in
 * stage 05 by running Core's own document fixtures ({@code PresupuestoDocumentoFixture}, {@code ApuDocumentoFixture},
 * {@code ApuConciliadoFixture}) through the real {@code DocumentoContratoMapper}, i.e. exactly what Core sends
 * Documentos. The retired Core writer tests rendered those same fixtures, so their expected values carry over
 * unchanged.
 */
final class Projections {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    /** Same constants as Core's {@code PresupuestoDocumentoFixture}. */
    static final String EXACTO = "123.123456789012345678901234567890";

    static final String HOSTIL = "=SUM(A1:A2) +@ ñ m² m³";

    private Projections() {}

    static UUID id(int n) {
        return UUID.fromString("01900000-0000-7000-8000-%012d".formatted(n));
    }

    static DocumentProjectionV1 load(String name) {
        try (var in = Projections.class.getResourceAsStream("/projections/" + name + ".json")) {
            if (in == null) throw new IllegalStateException("Missing projection fixture " + name);
            return JSON.readValue(in, DocumentProjectionV1.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
