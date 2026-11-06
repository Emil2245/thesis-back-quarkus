package ec.uce.propuestas.common;

import java.util.Comparator;

/**
 * Plan 042 — orden natural de los {@code item} jerárquicos del presupuesto
 * ("1", "1.2", "1.12", "2.10"): segmento a segmento, numéricamente.
 *
 * <p>El orden lexicográfico NO sirve, aunque lo parezca con datos pequeños:
 * {@code "1.12" < "1.2"} como cadenas, porque compara {@code '1'} contra
 * {@code '2'} en la segunda posición. Con más de nueve subcapítulos el árbol
 * sale barajado. Ese era el defecto que este tipo retira, y estaba escrito como
 * verdad en el Javadoc de {@code PresupuestoMapper}.</p>
 *
 * <p>Un segmento no numérico se compara como texto contra el otro, para que un
 * item con letras no rompa el orden ni lance excepción.</p>
 */
public final class ItemJerarquico {

    /** Orden natural; {@code null} va al final. */
    public static final Comparator<String> ORDEN = Comparator.nullsLast(ItemJerarquico::comparar);

    private ItemJerarquico() {}

    public static int comparar(String a, String b) {
        String[] sa = a.split("\\.", -1);
        String[] sb = b.split("\\.", -1);
        int n = Math.max(sa.length, sb.length);
        for (int i = 0; i < n; i++) {
            // El más corto va primero: "1" antes que "1.1".
            if (i >= sa.length) return -1;
            if (i >= sb.length) return 1;
            Long na = enteroONull(sa[i]);
            Long nb = enteroONull(sb[i]);
            if (na != null && nb != null) {
                int c = Long.compare(na, nb);
                if (c != 0) return c;
            } else {
                int c = sa[i].compareTo(sb[i]);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private static Long enteroONull(String s) {
        if (s.isEmpty()) return null;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null; // segmento de más de 19 dígitos
        }
    }
}
