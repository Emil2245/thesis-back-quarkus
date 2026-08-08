package ec.uce.propuestas.insumo.dto;

public record BaseInsumosResponse(
        Long id,
        String nombre,
        String tipo,
        boolean archivada,
        long totalInsumos
) {}