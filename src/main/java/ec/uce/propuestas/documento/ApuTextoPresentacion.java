package ec.uce.propuestas.documento;

import java.util.List;

/** Bounded, Unicode-safe presentation chunks; contains no financial arithmetic. */
public final class ApuTextoPresentacion {
    private ApuTextoPresentacion() {}

    public static List<String> fragmentos(String texto) {
        return PresupuestoXlsxWriter.fragmentos(PresupuestoXlsxWriter.seguro(texto));
    }
}
