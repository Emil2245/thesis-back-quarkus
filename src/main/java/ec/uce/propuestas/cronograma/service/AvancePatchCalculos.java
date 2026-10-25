package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.common.ProblemaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan 029 (P-34) — algoritmos deterministas para distribuir, mover y
 * redimensionar el mapa de avance de una actividad. Esta clase no toca la base;
 * opera exclusivamente sobre {@link Map} {@code String → String} y devuelve el
 * mapa canónico persistente (escala 4 HALF_UP).
 *
 * <p>Las invariantes que esta clase sostiene:
 * <ul>
 *   <li>La suma de los valores del mapa destino es EXACTAMENTE la suma de los
 *       valores que se le pide distribuir/conservar — la escala de trabajo es
 *       4 con HALF_UP y el residual firmado se asigna al último período
 *       numérico ascendente.</li>
 *   <li>Las claves presentes con valor {@code "0.0000"} cuentan como activas;
 *       un segmento formado por una sola clave cero es válido y participa en
 *       mover/redimensionar (D-10 + N05).</li>
 *   <li>No se introducen claves fuera del rango vigente {@code 1..n}; no se
 *       borram claves adyacentes ajenas al segmento en mover/redimensionar.
 *       Una colisión con claves externas al segmento produce
 *       {@code 409 segmento-solapado}.</li>
 * </ul>
 */
public final class AvancePatchCalculos {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(4);

    private AvancePatchCalculos() {}

    /**
     * {@link ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Distribuir} canónica entre los períodos listados. El
     * {@code peso} se reparte en N tramos iguales a escala 4 HALF_UP; el
     * residual firmado se asigna al último período numérico ascendente.
     *
     * <p>{@code periodos} debe estar libre de duplicados y ordenado
     * ascendentemente por construcción (ver {@link AvancePatchParser}).</p>
     */
    public static Map<String, String> distribucionUniforme(
            BigDecimal peso, List<Integer> periodos, int numeroPeriodos) {
        if (periodos.isEmpty()) {
            return Map.of();
        }
        List<Integer> ordenados = new ArrayList<>(periodos);
        ordenados.sort(Integer::compareTo);
        for (int p : ordenados) {
            if (p < 1 || p > numeroPeriodos) {
                throw ProblemaException.validacion("Período fuera del rango 1.." + numeroPeriodos);
            }
        }
        if (peso == null || peso.signum() == 0) {
            return mapaCeroEnPeriodos(ordenados);
        }
        if (peso.signum() < 0) {
            throw ProblemaException.validacion("peso negativo al distribuir uniforme");
        }
        long[] unidades = distribuirUnidades(aUnidades(peso), ordenados.size());
        Map<String, String> resultado = new LinkedHashMap<>();
        for (int i = 0; i < ordenados.size(); i++) {
            resultado.put(ordenados.get(i).toString(), deUnidades(unidades[i]));
        }
        return Map.copyOf(resultado);
    }

    /**
     * {@link ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Mover}: desplaza el segmento {@code [inicio, fin]} sumando
     * {@code delta} a cada clave; las claves no presentes en el segmento se
     * conservan. El segmento debe corresponder exactamente a uno de los
     * segmentos máximos derivados del mapa (inclusión de claves con valor
     * {@code 0.0000}). Si el destino colisiona con claves externas,
     * se devuelve {@code 409 segmento-solapado}.
     *
     * @return mapa completo post-traslado, listo para persistir.
     */
    public static ResultadoMap mover(
            Map<String, String> mapaActual, int inicio, int fin, int delta, int numeroPeriodos) {
        if (mapaActual == null) {
            throw ProblemaException.validacion("Mapa de avance nulo al mover");
        }
        if (inicio < 1 || fin > numeroPeriodos || fin < inicio) {
            throw ProblemaException.validacion("Segmento fuente fuera de rango 1.." + numeroPeriodos);
        }
        int nuevoInicio = inicio + delta;
        int nuevoFin = fin + delta;
        if (nuevoInicio < 1 || nuevoFin > numeroPeriodos) {
            throw ProblemaException.validacion("El segmento trasladado sale del rango 1.." + numeroPeriodos);
        }
        // 1) Asegurar que [inicio, fin] es exactamente un segmento máximo actual:
        //    todas las claves del rango existen, no hay claves adyacentes.
        Set<Integer> clavesActuales = claves(mapaActual.keySet());
        for (int p = inicio; p <= fin; p++) {
            if (!clavesActuales.contains(p)) {
                throw ProblemaException.validacion("El rango [" + inicio + "," + fin + "] no existe en el mapa actual");
            }
        }
        if (clavesActuales.contains(inicio - 1) || clavesActuales.contains(fin + 1)) {
            throw ProblemaException.validacion("El rango no es un segmento máximo actual (adyacentes activos)");
        }
        // 2) Tomar los valores del segmento fuente, conservados sin tocar.
        Map<Integer, String> valoresSegmento = new LinkedHashMap<>();
        for (int p = inicio; p <= fin; p++) {
            valoresSegmento.put(p, mapaActual.get(Integer.toString(p)));
        }
        // 3) Detectar colisión con claves externas al segmento en la zona destino.
        for (int p = nuevoInicio; p <= nuevoFin; p++) {
            if (clavesActuales.contains(p) && (p < inicio || p > fin)) {
                throw new AvanceSegmentoException("El desplazamiento colisiona con claves fuera del segmento fuente");
            }
        }
        // 4) Construir el mapa destino.
        Map<String, String> destino = new LinkedHashMap<>();
        for (Map.Entry<String, String> kv : mapaActual.entrySet()) {
            int p = Integer.parseInt(kv.getKey());
            if (p < inicio || p > fin) {
                destino.put(kv.getKey(), kv.getValue());
            }
        }
        for (Map.Entry<Integer, String> kv : valoresSegmento.entrySet()) {
            int destinoP = kv.getKey() + delta;
            destino.put(Integer.toString(destinoP), kv.getValue());
        }
        return new ResultadoMap(Map.copyOf(destino), false);
    }

    /**
     * {@link ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Redimensionar}: redimensiona el segmento {@code [inicio, fin]} a
     * {@code [nuevoInicio, nuevoFin]} redistribuyendo únicamente la SUMA DEL
     * SEGMENTO FUENTE (no el peso total de la actividad). La distribución es
     * uniforme a escala 4 con el residual determinista en el último período
     * numérico ascendente. Otros segmentos del mapa permanecen intactos.
     */
    public static ResultadoMap redimensionar(
            Map<String, String> mapaActual, int inicio, int fin, int nuevoInicio, int nuevoFin, int numeroPeriodos) {
        if (mapaActual == null) {
            throw ProblemaException.validacion("Mapa de avance nulo al redimensionar");
        }
        if (inicio < 1 || fin > numeroPeriodos || fin < inicio) {
            throw ProblemaException.validacion("Segmento fuente fuera de rango 1.." + numeroPeriodos);
        }
        if (nuevoInicio < 1 || nuevoFin > numeroPeriodos || nuevoFin < nuevoInicio) {
            throw ProblemaException.validacion("Rango destino fuera de 1.." + numeroPeriodos);
        }
        Set<Integer> clavesActuales = claves(mapaActual.keySet());
        for (int p = inicio; p <= fin; p++) {
            if (!clavesActuales.contains(p)) {
                throw ProblemaException.validacion("El rango fuente no existe en el mapa actual");
            }
        }
        if (clavesActuales.contains(inicio - 1) || clavesActuales.contains(fin + 1)) {
            throw ProblemaException.validacion("El rango fuente no es un segmento máximo actual");
        }
        // Suma del segmento fuente
        long sumaSegmento = 0L;
        for (int p = inicio; p <= fin; p++) {
            sumaSegmento += aUnidades(new BigDecimal(mapaActual.get(Integer.toString(p))));
        }
        // Tamaño destino y posibles colisiones fuera del rango fuente:
        // el destino del segmento puede colisionar con claves externas a ambos
        // segmentos (fuente y destino distintos), pero no con claves que NO
        // pertenezcan al rango fuente, salvo que el destino NO los solape.
        for (int p = nuevoInicio; p <= nuevoFin; p++) {
            if (clavesActuales.contains(p) && (p < inicio || p > fin)) {
                throw new AvanceSegmentoException("El destino colisiona con claves fuera del segmento fuente");
            }
        }
        int n = nuevoFin - nuevoInicio + 1;
        long[] unidades = distribuirUnidades(sumaSegmento, n);
        // Construir el mapa destino: conservar claves fuera de [inicio,fin] y
        // fuera de [nuevoInicio,nuevoFin], sustituir el rango destino por la
        // distribución uniforme.
        Map<String, String> destino = new LinkedHashMap<>();
        for (Map.Entry<String, String> kv : mapaActual.entrySet()) {
            int p = Integer.parseInt(kv.getKey());
            if (p < inicio || p > fin) {
                destino.put(kv.getKey(), kv.getValue());
            }
        }
        for (int p = nuevoInicio; p <= nuevoFin; p++) {
            destino.put(Integer.toString(p), deUnidades(unidades[p - nuevoInicio]));
        }
        // Ordenar por período ascendente para cumplir la semántica de mapa ordenado.
        Map<String, String> ordenado = new LinkedHashMap<>();
        List<Integer> clavesOrdenadas = new ArrayList<>();
        for (String c : destino.keySet()) {
            clavesOrdenadas.add(Integer.parseInt(c));
        }
        clavesOrdenadas.sort(Integer::compareTo);
        for (int p : clavesOrdenadas) {
            ordenado.put(Integer.toString(p), destino.get(Integer.toString(p)));
        }
        return new ResultadoMap(Map.copyOf(ordenado), false);
    }

    /**
     * Plan 021 — re-escala proporcionalmente el mapa cuando el peso de la
     * actividad cambia de {@code w} a {@code wNuevo}: {@code v' = v · w'/w} a
     * escala 4 HALF_UP; la suma destino es {@code round4(Σv · w'/w)} (un mapa
     * parcial sigue siendo parcial) y el residual firmado va al último período
     * con valor no cero. Si {@code w == 0} (o todos los valores son 0) y el mapa no está
     * vacío, redistribuye uniforme entre las claves existentes. Mapa vacío →
     * mapa vacío.
     */
    public static Map<String, String> reescalar(Map<String, String> mapa, BigDecimal w, BigDecimal wNuevo) {
        if (mapa == null || mapa.isEmpty()) {
            return new LinkedHashMap<>();
        }
        BigDecimal objetivo = (wNuevo == null ? BigDecimal.ZERO : wNuevo).setScale(4, RoundingMode.HALF_UP);
        java.util.TreeMap<Integer, BigDecimal> ordenado = new java.util.TreeMap<>();
        for (Map.Entry<String, String> kv : mapa.entrySet()) {
            ordenado.put(Integer.parseInt(kv.getKey()), new BigDecimal(kv.getValue()));
        }
        boolean todoCero = ordenado.values().stream().allMatch(v -> v.signum() == 0);
        if (w == null || w.signum() == 0 || todoCero) {
            List<Integer> periodos = new ArrayList<>(ordenado.keySet());
            int max = periodos.get(periodos.size() - 1);
            return new LinkedHashMap<>(distribucionUniforme(objetivo, periodos, Math.max(max, 1)));
        }
        Map<Integer, Long> nuevos = new java.util.TreeMap<>();
        BigDecimal sumaAntigua = ordenado.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Suma destino proporcional: un mapa parcial sigue parcial (no se inventa programación).
        long sumaDestino = aUnidades(sumaAntigua.multiply(objetivo).divide(w, 4, RoundingMode.HALF_UP));
        long suma = 0L;
        Integer ultimoNoCero = null;
        for (Map.Entry<Integer, BigDecimal> e : ordenado.entrySet()) {
            BigDecimal escalado = e.getValue().multiply(objetivo).divide(w, 4, RoundingMode.HALF_UP);
            long u = aUnidades(escalado);
            nuevos.put(e.getKey(), u);
            suma += u;
            if (e.getValue().signum() != 0) {
                ultimoNoCero = e.getKey();
            }
        }
        nuevos.put(ultimoNoCero, nuevos.get(ultimoNoCero) + sumaDestino - suma);
        Map<String, String> resultado = new LinkedHashMap<>();
        for (Map.Entry<Integer, Long> e : nuevos.entrySet()) {
            resultado.put(e.getKey().toString(), deUnidades(e.getValue()));
        }
        return resultado;
    }

    /** Plan 021 — distribución uniforme por defecto sobre los períodos {@code 1..n}. */
    public static Map<String, String> distribucionPorDefecto(BigDecimal peso, int numeroPeriodos) {
        if (numeroPeriodos < 1) {
            return new LinkedHashMap<>();
        }
        List<Integer> periodos = new ArrayList<>();
        for (int p = 1; p <= numeroPeriodos; p++) {
            periodos.add(p);
        }
        return new LinkedHashMap<>(
                distribucionUniforme(peso == null ? BigDecimal.ZERO : peso, periodos, numeroPeriodos));
    }

    // ──────────────────────────────────────────────────────────────────────
    // utilidades
    // ──────────────────────────────────────────────────────────────────────

    private static Set<Integer> claves(Collection<String> clavesString) {
        Set<Integer> set = new LinkedHashSet<>();
        for (String c : clavesString) {
            set.add(Integer.parseInt(c));
        }
        return set;
    }

    private static Map<String, String> mapaCeroEnPeriodos(List<Integer> periodos) {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (int p : periodos) {
            resultado.put(Integer.toString(p), CERO.toPlainString());
        }
        return Map.copyOf(resultado);
    }

    /**
     * Divide el total en unidades de escala 4 usando HALF_UP para la base y
     * aplica el residual firmado al último período.
     */
    private static long[] distribuirUnidades(long unidadesTotales, int cantidad) {
        long base = BigDecimal.valueOf(unidadesTotales, 4)
                .divide(BigDecimal.valueOf(cantidad), 4, RoundingMode.HALF_UP)
                .movePointRight(4)
                .longValueExact();
        long[] unidades = new long[cantidad];
        java.util.Arrays.fill(unidades, base);
        unidades[cantidad - 1] += unidadesTotales - base * cantidad;
        return unidades;
    }

    /** Convierte un decimal de escala ≤ 4 a unidades enteras de {@code 0.0001}. */
    static long aUnidades(BigDecimal valor) {
        BigDecimal cuantizado = valor.setScale(4, RoundingMode.HALF_UP);
        return cuantizado.movePointRight(4).longValueExact();
    }

    /** Inverso de {@link #aUnidades}: devuelve string decimal a escala 4 HALF_UP. */
    static String deUnidades(long unidades) {
        return BigDecimal.valueOf(unidades, 4).toPlainString();
    }

    /** Resultado de un cálculo que produce un mapa completo. */
    public record ResultadoMap(Map<String, String> mapa, boolean colisiona) {}
}
