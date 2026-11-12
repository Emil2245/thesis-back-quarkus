package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.DocumentoContratoMapper;
import ec.uce.propuestas.documento.exportacion.PreflightDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentoContratoMapperTest {
    @Test
    void transfersOnlyPublicImmutableRenderValuesAcrossJson() throws Exception {
        var original = PresupuestoDocumentoFixture.snapshot(3, 0);
        var request = DocumentoContratoMapper.proyectar(
                original, "presupuesto", PresupuestoDocumentoFixture.opciones("xlsx", false));
        var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(request);
        var decoded = new ObjectMapper().findAndRegisterModules().readValue(json, DocumentProjectionV1.class);

        assertEquals(DocumentProjectionV1.VERSION, decoded.schemaVersion());
        assertEquals(original.presupuestoId(), decoded.snapshot().presupuestoId());
        assertEquals(original.total(), decoded.snapshot().total());
        assertEquals(
                original.capitulos().getFirst().hijos().getFirst().id(),
                decoded.snapshot().capitulos().getFirst().hijos().getFirst().id());
        assertEquals(
                original.apus().getFirst().calculado().costoTotal(),
                decoded.snapshot().apus().getFirst().calculado().costoTotal());
        assertFalse(json.contains("preflight"));
        assertFalse(json.contains("usuarioId"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> decoded.snapshot().capitulos().clear());
    }

    @Test
    void sendsCoreReconciledPrintableApuRowsRatherThanStalePersistedAmounts() {
        var apu = ApuConciliadoFixture.apu(20, ApuConciliadoFixture.entradasConHm(), true);
        var original = ApuConciliadoFixture.snapshot(List.of(apu), false);
        var preflight = new PreflightDocumento(
                original.presupuestoId(),
                original.version(),
                "apus",
                "xlsx",
                Map.of("layout", "pestanas"),
                true,
                List.of(),
                List.of());
        var captured = new SnapshotDocumento(
                original.presupuestoId(),
                original.version(),
                original.vigente(),
                original.notas(),
                original.total(),
                original.proyecto(),
                original.parametros(),
                original.display(),
                original.firmantes(),
                original.capitulos(),
                original.apus(),
                preflight);
        var expected = ApuDocumentoProyeccion.proyectar(captured)
                .apus()
                .getFirst()
                .bloques()
                .getFirst()
                .filas()
                .getFirst();
        var projected = DocumentoContratoMapper.proyectar(
                captured,
                "apus",
                new ec.uce.propuestas.documento.exportacion.OpcionesDocumento("xlsx", Map.of("layout", "pestanas")));
        var actual = projected
                .snapshot()
                .apuRender()
                .getFirst()
                .bloques()
                .getFirst()
                .filas()
                .getFirst();
        assertEquals(expected.detalle().id(), actual.detalle().id());
        assertEquals(expected.costo(), actual.costo());
        assertEquals(expected.costoHora(), actual.costoHora());
        assertNotEquals(expected.detalle().costo(), actual.costo());
    }

    @Test
    void refusesBlockedPreflightBeforeTransport() {
        var original = PresupuestoDocumentoFixture.snapshot(2, 0);
        var blocked = new SnapshotDocumento(
                original.presupuestoId(),
                original.version(),
                original.vigente(),
                original.notas(),
                original.total(),
                original.proyecto(),
                original.parametros(),
                original.display(),
                original.firmantes(),
                original.capitulos(),
                original.apus(),
                new PreflightDocumento(
                        original.presupuestoId(),
                        original.version(),
                        "presupuesto",
                        "xlsx",
                        Map.of(),
                        false,
                        List.of(),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> DocumentoContratoMapper.proyectar(
                        blocked, "presupuesto", PresupuestoDocumentoFixture.opciones("xlsx", false)));
    }
}
