package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.List;

public record DescuentoGlobalPreviewResponse(
        BigDecimal porcentaje,
        List<DescuentoApuPreview> porApu,
        BigDecimal totalGeneralActual,
        BigDecimal totalGeneralProyectado) {

    public record DescuentoApuPreview(
            Long apuId, String codigo, BigDecimal cd, BigDecimal cdAjustado, BigDecimal ci, BigDecimal ct) {}
}
