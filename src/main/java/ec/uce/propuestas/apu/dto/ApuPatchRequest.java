package ec.uce.propuestas.apu.dto;

import org.openapitools.jackson.nullable.JsonNullable;

public record ApuPatchRequest(
        JsonNullable<String> codigo, JsonNullable<String> descripcion, JsonNullable<String> unidad) {}
