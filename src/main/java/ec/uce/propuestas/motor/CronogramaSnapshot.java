package ec.uce.propuestas.motor;

import java.util.List;
import java.util.Objects;

/** The scheduling data for a budget version. */
public record CronogramaSnapshot(
        int numeroPeriodos,
        List<ActividadSnapshot> actividades
) {
    public CronogramaSnapshot {
        Objects.requireNonNull(actividades, "actividades must not be null");
        actividades = List.copyOf(actividades);
    }
}
