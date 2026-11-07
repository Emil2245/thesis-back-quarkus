package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;

/** Render boundary after Core has completed authorization, preflight and capture. */
@FunctionalInterface
public interface DocumentosRenderGateway {
    byte[] render(SnapshotDocumento snapshot, String documento, OpcionesDocumento opciones);
}
