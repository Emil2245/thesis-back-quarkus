package ec.uce.propuestas.documento;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class RemoteRenderProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("documentos.test-local-render", "false");
    }
}
