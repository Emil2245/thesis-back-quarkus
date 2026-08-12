package ec.uce.propuestas.insumo.dto;

import java.util.List;

public record ImportResultadoResponse(int creados, int actualizados, List<ErrorFila> errores) {}
