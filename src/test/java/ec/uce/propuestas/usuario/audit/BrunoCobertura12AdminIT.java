package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BrunoCobertura12AdminIT {
    private static final List<String> REQUESTS = List.of(
            "TC-12-00a-login-super-admin-helper.bru", "TC-12-00b-login-john-helper.bru",
            "TC-12-00c-login-ana-helper.bru", "TC-12-00d-crear-proyecto-helper.bru",
            "TC-12-00e-capturar-presupuesto-helper.bru", "TC-12-P38-01-invitar.bru",
            "TC-12-P38-02-desactivar-reactivar.bru", "TC-12-P38-03-delete-con-proyectos.bru",
            "TC-12-P39-01-crud-base-central.bru", "TC-12-P39-02-archivar-base.bru",
            "TC-12-P39-03-edicion-no-afecta-proyecto.bru", "TC-12-P40-01-crear-plantilla-sistema-y-ver-usuario.bru",
            "TC-12-P41-01-cambiar-defaults-y-verificar.bru", "TC-12-P41-02-upsert-sbu-y-verificar-motor.bru",
            "TC-12-P42-01-cobertura-categorias.bru", "TC-12-P42-02-filtro-evento-sin-pii.bru");

    @Test
    void coleccion_contiene_inventario_y_contrato_operativo() throws Exception {
        Path root = Path.of("api/bruno/12-admin");
        assertTrue(Files.exists(root.resolve("folder.bru")));
        assertEquals(
                16,
                Files.list(root)
                        .filter(p -> p.toString().endsWith(".bru") && !p.endsWith("folder.bru"))
                        .count());
        for (int i = 0; i < REQUESTS.size(); i++) {
            String source = Files.readString(root.resolve(REQUESTS.get(i)));
            assertTrue(source.contains("seq: " + (i + 1)), REQUESTS.get(i));
            assertTrue(source.matches("(?s).*(get|post|put|patch|delete)\\s*\\{.*"), REQUESTS.get(i));
            assertTrue(
                    source.contains("url:")
                            && source.contains("tests {")
                            && source.contains("expect(res.status).to.equal("),
                    REQUESTS.get(i));
            assertFalse(source.contains("oneOf"), REQUESTS.get(i));
            assertFalse(source.contains("not.equal"), REQUESTS.get(i));
            if (source.contains("sendRequest")) {
                assertFalse(source.contains("bru.getVar(\"baseUrl\")"), REQUESTS.get(i));
                assertFalse(source.replace("res.body", "").contains(".body"), REQUESTS.get(i));
                assertTrue(source.contains("bru.getEnvVar(\"baseUrl\")"), REQUESTS.get(i));
            }
            if (source.contains("body:json")) {
                assertTrue(
                        Pattern.compile("(?s)(?:post|put) \\{.*?body: json.*?\\}")
                                .matcher(source)
                                .find(),
                        REQUESTS.get(i));
                assertTrue(
                        Pattern.compile("(?s)(?:post|put) \\{.*?headers \\{.*?Content-Type: application/json.*?\\}")
                                .matcher(source)
                                .find(),
                        REQUESTS.get(i));
            }
        }
        String login = Files.readString(root.resolve(REQUESTS.get(0)));
        assertTrue(login.contains("auth: none") && login.contains("bru.setVar(\"admin_access_token\""));
        assertTrue(Files.readString(root.resolve(REQUESTS.get(1))).contains("auth: none"));
        assertTrue(Files.readString(root.resolve(REQUESTS.get(3))).contains("bru.setVar(\"p40ProyectoId\""));
        assertTrue(Files.readString(root.resolve(REQUESTS.get(4))).contains("bru.setVar(\"p40PresupuestoId\""));
        String p40 = Files.readString(root.resolve(REQUESTS.get(11)));
        assertTrue(p40.contains("desdeApuId")
                && p40.contains("nombre")
                && p40.contains("descripcionRubro")
                && p40.contains("await bru.sendRequest")
                && p40.contains("u1_access_token"));
        String p41 = Files.readString(root.resolve(REQUESTS.get(12)));
        assertTrue(p41.contains("porcentajeHerramientaMenor")
                && p41.contains("rangoHmMax")
                && p41.contains("p41ProyectoNuevoId"));
        String p42 =
                Files.readString(root.resolve(REQUESTS.get(14))) + Files.readString(root.resolve(REQUESTS.get(15)));
        for (String category : List.of(
                "auth.",
                "usuario.",
                "proyecto.",
                "insumo.",
                "base.",
                "apu.",
                "presupuesto.",
                "cronograma.",
                "documento.",
                "admin.")) assertTrue(p42.contains(category));
        for (String fragment : List.of("email", "password", "contrase", "token", "jwt", "hash"))
            assertTrue(p42.contains(fragment));
        assertTrue(p42.contains("await bru.sendRequest") && p42.contains("size=200"));
        assertTrue(p40.contains("Array.isArray(user.data)"));
        String p41Calc = Files.readString(root.resolve(REQUESTS.get(13)));
        assertTrue(p41Calc.contains("resumen.ct"));
        assertTrue(Files.readString(root.resolve(REQUESTS.get(10))).contains(".data.items"));
    }

    @Test
    void environment_preserva_baseline_y_declara_variables_nuevas_dentro_de_vars() throws Exception {
        String env = Files.readString(Path.of("api/bruno/environments/dev.bru"));
        assertEquals(1, Pattern.compile("(?m)^vars \\{").matcher(env).results().count());
        assertEquals(1, Pattern.compile("(?m)^\\}").matcher(env).results().count());
        for (String variable : Set.of(
                "baseUrl", "u1Email", "u2Email", "p40ProyectoId", "p40PresupuestoId", "p40ApuId", "p41ProyectoViejoId"))
            assertTrue(env.contains("  " + variable + ":"), variable);
        assertTrue(env.contains("u1Email: john.doe@uce.edu.ec") && env.contains("u2Email: ana.armas@gmail.com"));
        assertFalse(env.matches("(?s).*\\}\\s+\\w+\\s*:.*"));
    }
}
