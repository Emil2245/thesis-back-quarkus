package ec.uce.propuestas.proyecto.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ValorReferenciaNoEntraAlMotorTest {

    @Test
    void TC_P41_02_motor_no_depende_de_valor_referencia() throws Exception {
        Path motor = Path.of("src/main/java/ec/uce/propuestas/motor");
        assertTrue(Files.isDirectory(motor));
        try (Stream<Path> archivos = Files.walk(motor)) {
            boolean referencia =
                    archivos.filter(p -> p.toString().endsWith(".java")).anyMatch(this::mencionaValorReferencia);
            assertFalse(referencia, "valor_referencia es informativo y nunca puede entrar al motor");
        }
    }

    private boolean mencionaValorReferencia(Path archivo) {
        try {
            String fuente = Files.readString(archivo).toLowerCase();
            return fuente.contains("valorreferencia") || fuente.contains("valor_referencia");
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo inspeccionar " + archivo, ex);
        }
    }
}
