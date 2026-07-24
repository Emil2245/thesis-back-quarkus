package ec.uce.propuestas.motor;

import java.util.List;
import java.util.Objects;

/** A chapter (chapter or sub-chapter) in the budget hierarchy. May contain sub-chapters and/or rubros. */
public record CapituloSnapshot(
        String item,
        String descripcion,
        int depth,                             // 1 = root chapter
        List<CapituloSnapshot> subcapitulos,   // recursive sub-chapters
        List<RubroSnapshot> rubros             // leaf rubros at this level
) {
    public CapituloSnapshot {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(subcapitulos, "subcapitulos must not be null");
        Objects.requireNonNull(rubros, "rubros must not be null");
        subcapitulos = List.copyOf(subcapitulos);
        rubros = List.copyOf(rubros);
    }
}
