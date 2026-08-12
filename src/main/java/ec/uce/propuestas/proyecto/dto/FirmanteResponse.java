package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.RolFirmante;

public record FirmanteResponse(Long id, String nombre, String cargo, RolFirmante rol, Short orden) {}
