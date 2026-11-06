package ec.uce.propuestas.cronograma.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.cronograma.dto.ActividadCronogramaResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.dto.SegmentoResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el read model canónico sin persistir ninguno de sus campos derivados. */
@ApplicationScoped
public class CronogramaMapper {

    private static final BigDecimal CIEN = new BigDecimal("100.0000");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Par interno con el item del capítulo necesario para orden y fingerprint. */
    public record ActividadConRubro(Actividad actividad, Rubro rubro, String capituloItem) {}

    public CronogramaResponse toResponse(
            Cronograma cronograma,
            Presupuesto presupuesto,
            List<ActividadConRubro> actividades,
            String fingerprintActual) {
        int numeroPeriodos = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();
        List<BigDecimal> avancePeriodos = new ArrayList<>(numeroPeriodos);
        for (int i = 0; i < numeroPeriodos; i++) {
            avancePeriodos.add(cero4());
        }

        List<ActividadCronogramaResponse> proyeccion = new ArrayList<>(actividades.size());
        BigDecimal avanceFinal = cero4();
        boolean completo = !actividades.isEmpty();

        for (ActividadConRubro par : actividades) {
            Map<String, String> mapa = leerMapa(par.actividad().avancePorPeriodo);
            BigDecimal peso = escala4(par.actividad().pesoPonderado);
            BigDecimal suma = cero4();
            for (Map.Entry<String, String> entrada : mapa.entrySet()) {
                BigDecimal valor = new BigDecimal(entrada.getValue());
                suma = suma.add(valor);
                int periodo = Integer.parseInt(entrada.getKey());
                if (periodo >= 1 && periodo <= numeroPeriodos) {
                    avancePeriodos.set(
                            periodo - 1, avancePeriodos.get(periodo - 1).add(valor));
                }
            }
            suma = escala4(suma);
            avanceFinal = avanceFinal.add(suma);
            BigDecimal desviacion = escala4(peso.subtract(suma));

            boolean precioPositivo =
                    par.rubro().precioTotal != null && par.rubro().precioTotal.compareTo(BigDecimal.ZERO) > 0;
            if (desviacion.compareTo(BigDecimal.ZERO) != 0 || (precioPositivo && mapa.isEmpty())) {
                completo = false;
            }

            proyeccion.add(new ActividadCronogramaResponse(
                    par.actividad().publicId,
                    par.rubro().publicId,
                    par.rubro().item,
                    par.rubro().codigo,
                    par.rubro().descripcion,
                    par.rubro().unidad,
                    escala6(par.rubro().cantidad).toPlainString(),
                    escala6(par.rubro().precioUnitario).toPlainString(),
                    escala6(par.rubro().precioTotal).toPlainString(),
                    peso.toPlainString(),
                    mapa,
                    segmentos(mapa),
                    desviacion.toPlainString()));
        }

        avanceFinal = escala4(avanceFinal);
        if (avanceFinal.compareTo(CIEN) != 0) {
            completo = false;
        }

        List<String> avancePorPeriodo = avancePeriodos.stream()
                .map(CronogramaMapper::escala4)
                .map(BigDecimal::toPlainString)
                .toList();
        List<String> avanceAcumulado = new ArrayList<>(numeroPeriodos);
        BigDecimal acumulado = cero4();
        for (BigDecimal avance : avancePeriodos) {
            acumulado = escala4(acumulado.add(avance));
            avanceAcumulado.add(acumulado.toPlainString());
        }

        boolean desactualizado = cronograma.totalGeneralRevisado == null
                || cronograma.presupuestoFingerprintRevisado == null
                || escala6(cronograma.totalGeneralRevisado).compareTo(escala6(presupuesto.total)) != 0
                || !cronograma.presupuestoFingerprintRevisado.trim().equals(fingerprintActual);

        return new CronogramaResponse(
                cronograma.publicId,
                presupuesto.publicId,
                cronograma.unidadTiempo,
                numeroPeriodos,
                escala6(presupuesto.total).toPlainString(),
                cronograma.totalGeneralRevisado == null
                        ? null
                        : escala6(cronograma.totalGeneralRevisado).toPlainString(),
                cronograma.fechaRevision,
                completo ? "COMPLETO" : "BORRADOR",
                desactualizado,
                avanceFinal.toPlainString(),
                List.copyOf(proyeccion),
                avancePorPeriodo,
                List.copyOf(avanceAcumulado));
    }

    /** Claves presentes —también las de valor cero— forman runs máximos consecutivos. */
    private static List<SegmentoResponse> segmentos(Map<String, String> mapa) {
        List<Integer> periodos =
                mapa.keySet().stream().map(Integer::parseInt).sorted().toList();
        List<SegmentoResponse> resultado = new ArrayList<>();
        if (periodos.isEmpty()) {
            return resultado;
        }
        int inicio = periodos.get(0);
        int fin = inicio;
        for (int i = 1; i < periodos.size(); i++) {
            int actual = periodos.get(i);
            if (actual == fin + 1) {
                fin = actual;
            } else {
                resultado.add(new SegmentoResponse(inicio, fin));
                inicio = actual;
                fin = actual;
            }
        }
        resultado.add(new SegmentoResponse(inicio, fin));
        return List.copyOf(resultado);
    }

    /** Lee JSONB como mapa ordenado numéricamente y normaliza valores a escala 4. */
    public static Map<String, String> leerMapa(String jsonb) {
        Map<String, String> ordenado = new LinkedHashMap<>();
        if (jsonb == null || jsonb.isBlank()) {
            return ordenado;
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(jsonb);
        } catch (Exception e) {
            throw new IllegalStateException("avance_por_periodo no es JSON válido", e);
        }
        if (!raiz.isObject()) {
            return ordenado;
        }
        List<String> claves = new ArrayList<>();
        raiz.fieldNames().forEachRemaining(claves::add);
        claves.sort(Comparator.comparingInt(Integer::parseInt));
        for (String clave : claves) {
            ordenado.put(
                    clave, escala4(new BigDecimal(raiz.get(clave).asText())).toPlainString());
        }
        return ordenado;
    }

    public static String escribirMapa(Map<String, String> mapa) {
        try {
            return JSON.writeValueAsString(mapa);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar avance_por_periodo", e);
        }
    }

    private static BigDecimal cero4() {
        return BigDecimal.ZERO.setScale(4);
    }

    private static BigDecimal escala4(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal escala6(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(6, RoundingMode.HALF_UP);
    }
}
