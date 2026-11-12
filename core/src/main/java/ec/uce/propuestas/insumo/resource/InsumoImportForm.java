package ec.uce.propuestas.insumo.resource;

import jakarta.ws.rs.FormParam;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** Cuerpo multipart de la importación CSV (P-15). */
public class InsumoImportForm {

    @FormParam("archivo")
    public FileUpload archivo;

    @FormParam("tipo")
    public String tipo;
}
