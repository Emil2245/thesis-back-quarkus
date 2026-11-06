package ec.uce.propuestas.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import org.openapitools.jackson.nullable.JsonNullableModule;

/**
 * Registra {@link JsonNullableModule} para poder distinguir «campo omitido» de
 * «null explícito» en los PATCH (contrato PATCH policy, módulo apu). El
 * artefacto es {@code org.openapitools:jackson-databind-nullable}.
 */
@Singleton
public class JacksonConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.registerModule(new JsonNullableModule());
    }
}
