package ec.uce.propuestas.cronograma.service;

import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.CronogramaConfigurarRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaCrearRequest;
import java.util.Set;

/**
 * Plan 028 — única costura de validación de los bodies de cronograma.
 *
 * <p>Se parsea el body como {@link JsonNode} porque el contrato exige rechazar
 * propiedades desconocidas (actividades, {@code rubroId}, pesos o identidades
 * enviadas por el cliente) con 400 {@code validacion}; el {@code ObjectMapper}
 * de la aplicación no falla por defecto ante campos extra y este plan no
 * cambia esa configuración global.</p>
 *
 * <p>Límites canónicos (026 → 07-api-contract §7 y CHECK de V009):
 * {@code SEMANA} admite {@code 1..520} y {@code MES} admite {@code 1..120}. La
 * validación ocurre ANTES de cualquier mutación; la CHECK de la base es
 * defensa en profundidad, no la validación primaria.</p>
 */
public final class CronogramaRequestParser {

    public static final String SEMANA = "SEMANA";
    public static final String MES = "MES";

    private static final int MAX_SEMANA = 520;
    private static final int MAX_MES = 120;

    private static final Set<String> CAMPOS_CREAR = Set.of("unidadTiempo", "numeroPeriodos");
    private static final Set<String> CAMPOS_CONFIGURAR = Set.of("unidadTiempo", "numeroPeriodos", "confirmarPerdida");

    private CronogramaRequestParser() {}

    public static CronogramaCrearRequest parsearCrear(JsonNode body) {
        exigirObjeto(body, CAMPOS_CREAR);
        return new CronogramaCrearRequest(unidad(body), periodos(body, unidad(body)));
    }

    public static CronogramaConfigurarRequest parsearConfigurar(JsonNode body) {
        exigirObjeto(body, CAMPOS_CONFIGURAR);
        String unidad = unidad(body);
        int periodos = periodos(body, unidad);
        JsonNode confirmar = body.get("confirmarPerdida");
        if (confirmar != null && !confirmar.isNull() && !confirmar.isBoolean()) {
            throw ProblemaException.validacion("confirmarPerdida debe ser booleano");
        }
        return new CronogramaConfigurarRequest(
                unidad, periodos, confirmar != null && !confirmar.isNull() && confirmar.asBoolean());
    }

    private static void exigirObjeto(JsonNode body, Set<String> permitidos) {
        if (body == null || !body.isObject()) {
            throw ProblemaException.validacion("El cuerpo de la petición debe ser un objeto JSON");
        }
        var nombres = body.fieldNames();
        while (nombres.hasNext()) {
            String nombre = nombres.next();
            if (!permitidos.contains(nombre)) {
                throw ProblemaException.validacion("Propiedad no admitida en el cuerpo: " + nombre);
            }
        }
    }

    private static String unidad(JsonNode body) {
        JsonNode nodo = body.get("unidadTiempo");
        if (nodo == null || !nodo.isTextual()) {
            throw ProblemaException.validacion("unidadTiempo es obligatorio y debe ser SEMANA o MES");
        }
        String valor = nodo.asText();
        if (!SEMANA.equals(valor) && !MES.equals(valor)) {
            throw ProblemaException.validacion("unidadTiempo solo admite SEMANA o MES");
        }
        return valor;
    }

    private static int periodos(JsonNode body, String unidad) {
        JsonNode nodo = body.get("numeroPeriodos");
        if (nodo == null || !nodo.isIntegralNumber() || !nodo.canConvertToInt()) {
            throw ProblemaException.validacion("numeroPeriodos es obligatorio y debe ser un entero");
        }
        int valor = nodo.asInt();
        int maximo = SEMANA.equals(unidad) ? MAX_SEMANA : MAX_MES;
        if (valor < 1 || valor > maximo) {
            throw ProblemaException.validacion(
                    "numeroPeriodos fuera del límite canónico para " + unidad + ": 1.." + maximo);
        }
        return valor;
    }
}
