package ec.uce.propuestas.motor;

import java.util.List;
import java.util.Objects;

/** Snapshot of a full budget version as input to Motor.consolidar. */
public record VersionSnapshot(
        ParametrosCalculo parametrosProyecto,
        List<CapituloSnapshot> capitulosRaiz,
        CronogramaSnapshot cronograma    // nullable if version has no schedule yet
) {
    public VersionSnapshot {
        Objects.requireNonNull(parametrosProyecto, "parametrosProyecto must not be null");
        Objects.requireNonNull(capitulosRaiz, "capitulosRaiz must not be null");
        capitulosRaiz = List.copyOf(capitulosRaiz);
    }
}
