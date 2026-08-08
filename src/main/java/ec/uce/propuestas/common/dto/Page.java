package ec.uce.propuestas.common.dto;

import java.util.List;

/**
 * Contenedor de paginación del contrato (07-api-contract.md usa {@code Page<T>}
 * en todos los listados). Tamaño de paquete por el nivel de la page de Panache.
 */
public record Page<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPaginas
) {

    public static <T> Page<T> of(List<T> items, long total, int page, int size) {
        int totalPaginas = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new Page<>(items, total, page, size, totalPaginas);
    }
}