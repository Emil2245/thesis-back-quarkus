package ec.uce.propuestas.presupuesto.dto;

import ec.uce.propuestas.apu.dto.ApuResponse;

/** Result of the atomic manual APU creation operation. */
public record ApuManualCompletoResponse(ApuResponse apu, PresupuestoResponse presupuesto) {}
