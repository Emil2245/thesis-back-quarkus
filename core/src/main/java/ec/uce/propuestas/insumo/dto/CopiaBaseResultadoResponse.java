package ec.uce.propuestas.insumo.dto;

import java.util.List;

public record CopiaBaseResultadoResponse(int copiados, List<String> omitidos) {}
