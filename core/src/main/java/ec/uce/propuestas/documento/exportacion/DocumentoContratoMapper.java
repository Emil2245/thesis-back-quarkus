package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.util.Objects;

/** Converts a completed Core capture to an immutable, public-ID-only render contract. */
public final class DocumentoContratoMapper {
    private DocumentoContratoMapper() {}

    public static DocumentProjectionV1 proyectar(
            SnapshotDocumento captured, String documento, OpcionesDocumento opciones) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(opciones, "opciones");
        var preflight = captured.preflight();
        if (preflight == null
                || !preflight.exportable()
                || !documento.equals(preflight.documento())
                || !opciones.formato().equals(preflight.formato())
                || !opciones.opciones().equals(preflight.opciones())) {
            throw new IllegalArgumentException(
                    "Only an exportable, matching Core capture may cross the service boundary");
        }
        var p = captured.proyecto();
        var params = captured.parametros();
        return new DocumentProjectionV1(
                DocumentProjectionV1.VERSION,
                documento,
                opciones.formato(),
                opciones.opciones(),
                new Snapshot(
                        captured.presupuestoId(),
                        captured.version(),
                        captured.vigente(),
                        captured.notas(),
                        captured.total(),
                        new Proyecto(
                                p.id(),
                                p.nombre(),
                                p.codigo(),
                                p.descripcion(),
                                p.anio(),
                                p.fechaInicio(),
                                p.plazoEjecucion(),
                                p.plazoUnidad(),
                                p.estado(),
                                p.direccionInstitucional(),
                                p.subdireccionInstitucional()),
                        new Parametros(
                                params.porcentajeHerramientaMenor(),
                                params.porcentajeIndirecto(),
                                params.ciIndividualHabilitado(),
                                params.iva(),
                                params.moneda(),
                                params.mostrarSeccionesVacias(),
                                params.sufijosSeccionActivos(),
                                params.mostrarSubtotalesSeccion(),
                                params.mostrarSubtotalesPie(),
                                params.mostrarNombreProyectoHeader(),
                                params.enumerarApus(),
                                params.mensajeFooter(),
                                params.modoCodigoRubro()),
                        new Display(
                                captured.display().precision(),
                                captured.display().precisionPorcentaje()),
                        captured.firmantes().stream()
                                .map(DocumentoContratoMapper::firmante)
                                .toList(),
                        captured.capitulos().stream()
                                .map(DocumentoContratoMapper::capitulo)
                                .toList(),
                        captured.apus().stream()
                                .map(DocumentoContratoMapper::apu)
                                .toList(),
                        "apus".equals(documento)
                                ? ApuDocumentoProyeccion.proyectar(captured).apus().stream()
                                        .map(DocumentoContratoMapper::apuView)
                                        .toList()
                                : java.util.List.of()));
    }

    private static Firmante firmante(SnapshotDocumento.Firmante source) {
        return new Firmante(source.id(), source.nombre(), source.cargo(), source.rol(), source.orden());
    }

    private static Capitulo capitulo(SnapshotDocumento.Capitulo source) {
        return new Capitulo(
                source.id(),
                source.item(),
                source.descripcion(),
                source.orden(),
                source.total(),
                source.hijos().stream().map(DocumentoContratoMapper::capitulo).toList(),
                source.rubros().stream().map(DocumentoContratoMapper::rubro).toList());
    }

    private static Rubro rubro(SnapshotDocumento.Rubro source) {
        return new Rubro(
                source.id(),
                source.apuId(),
                source.item(),
                source.codigo(),
                source.descripcion(),
                source.unidad(),
                source.cantidad(),
                source.precioUnitario(),
                source.precioTotal());
    }

    private static Apu apu(SnapshotDocumento.Apu source) {
        var calculated = source.calculado();
        return new Apu(
                source.id(),
                source.codigo(),
                source.descripcion(),
                source.unidad(),
                source.especificacionTecnica(),
                source.porcentajeIndirecto(),
                source.costoDirecto(),
                source.costoIndirecto(),
                source.costoTotal(),
                new CalculatedApu(
                        calculated.codigo(),
                        calculated.filas().stream()
                                .map(DocumentoContratoMapper::calculatedRow)
                                .toList(),
                        calculated.subtotalM(),
                        calculated.subtotalN(),
                        calculated.subtotalO(),
                        calculated.subtotalP(),
                        calculated.costoHm(),
                        calculated.costoDirecto(),
                        calculated.costoIndirecto(),
                        calculated.costoTotal()),
                source.secciones().stream()
                        .map(DocumentoContratoMapper::seccion)
                        .toList());
    }

    private static ApuView apuView(ApuDocumentoProyeccion.Analisis source) {
        var pie = source.pie();
        return new ApuView(
                source.id(),
                source.codigo(),
                source.etiquetaCodigo(),
                source.descripcion(),
                source.unidad(),
                source.nombreProyectoHeader(),
                source.bloques().stream().map(DocumentoContratoMapper::apuBlock).toList(),
                new ApuFooter(
                        pie.subtotales().stream()
                                .map(s -> new ApuSubtotal(
                                        SeccionTipo.valueOf(s.tipo().name()), s.valor()))
                                .toList(),
                        pie.porcentajeIndirecto(),
                        pie.costoDirecto(),
                        pie.costoIndirecto(),
                        pie.costoTotal(),
                        pie.valorOfertado(),
                        pie.mensaje()));
    }

    private static ApuBlock apuBlock(ApuDocumentoProyeccion.Bloque source) {
        return new ApuBlock(
                SeccionTipo.valueOf(source.tipo().name()),
                source.etiqueta(),
                source.ordenCapturado(),
                source.filas().stream()
                        .map(f -> new ApuRow(
                                detalle(f.detalle()),
                                f.descripcion(),
                                f.porcentajeHerramientaMenor(),
                                f.costo(),
                                f.costoHora()))
                        .toList(),
                source.subtotal(),
                source.mostrarSubtotal());
    }

    private static CalculatedRow calculatedRow(ec.uce.propuestas.motor.FilaCalculada source) {
        return new CalculatedRow(
                SeccionTipo.valueOf(source.seccion().name()),
                source.esHerramientaMenor(),
                source.cantidad(),
                source.rendimiento(),
                source.precioUnitarioEfectivo(),
                source.costoHora(),
                source.costoFila());
    }

    private static Seccion seccion(SnapshotDocumento.Seccion source) {
        return new Seccion(
                SeccionTipo.valueOf(source.tipo().name()),
                source.orden(),
                source.subtotal(),
                source.detalles().stream().map(DocumentoContratoMapper::detalle).toList());
    }

    private static Detalle detalle(SnapshotDocumento.Detalle source) {
        return new Detalle(
                source.id(),
                source.orden(),
                source.herramientaMenor(),
                source.descripcion(),
                source.unidad(),
                source.cantidad(),
                source.rendimiento(),
                source.tarifaJornal(),
                source.precioUnitarioTarifa(),
                source.precioEfectivo(),
                source.costoHora(),
                source.costo(),
                source.insumo() == null ? null : insumo(source.insumo()));
    }

    private static Insumo insumo(SnapshotDocumento.Insumo source) {
        return new Insumo(
                source.id(),
                source.codigo(),
                source.tipo(),
                source.descripcion(),
                source.unidad(),
                source.precioUnitario());
    }
}
