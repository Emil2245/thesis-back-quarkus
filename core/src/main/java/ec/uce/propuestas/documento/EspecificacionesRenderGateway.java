package ec.uce.propuestas.documento;

import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;

/** Render boundary for the immutable, already authorized technical projection. */
public interface EspecificacionesRenderGateway {
    byte[] render(SpecificationProjectionV1 projection);
}
