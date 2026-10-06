package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DocumentoSnapshotConcurrencyIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    CapturaDocumentoService captura;

    @Test
    void capturaAnteriorCompletaNuncaHibridaYProximaPosterior() throws Exception {
        var f = DocumentoSnapshotIT.fixture(ds, mailbox);
        var primeraLectura = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(
                    () -> captura.capturar(f.id(), f.caller(), "apus", DocumentoSnapshotIT.opciones(), () -> {
                        primeraLectura.countDown();
                        try {
                            if (!continuar.await(15, TimeUnit.SECONDS)) throw new AssertionError("Writer no terminó");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }));
            try {
                assertTrue(primeraLectura.await(15, TimeUnit.SECONDS), "Captura no llegó a ownership");
                DocumentoSnapshotIT.cambiar(ds, f); // COMMIT antes de continuar captura real.
            } finally {
                continuar.countDown();
            }
            var anterior = pending.get(20, TimeUnit.SECONDS);
            assertEstado(anterior, "anterior", "11", "10");
            var posterior = captura.capturar(f.id(), f.caller(), "apus", DocumentoSnapshotIT.opciones());
            assertEstado(posterior, "posterior", "24", "20");
        }
    }

    private static void assertEstado(SnapshotDocumento s, String texto, String total, String precio) {
        assertEquals(0, s.total().compareTo(new BigDecimal(total)), "Total de ownership");
        assertEquals(texto, s.parametros().mensajeFooter(), "Parámetros de misma generación");
        assertEquals(texto, s.firmantes().getFirst().nombre(), "Firmante de misma generación");
        var a = s.apus().getFirst();
        assertEquals(0, a.costoTotal().compareTo(new BigDecimal(total)));
        assertEquals(0, a.calculado().costoTotal().compareTo(new BigDecimal(total)));
        var d = a.secciones().getFirst().detalles().getFirst();
        assertEquals(texto, d.descripcion());
        assertEquals(texto, d.insumo().descripcion());
        assertEquals(0, d.insumo().precioUnitario().compareTo(new BigDecimal(precio)));
        assertEquals(0, d.costo().compareTo(new BigDecimal(precio)));
        assertEquals(
                0, s.capitulos().getFirst().rubros().getFirst().precioTotal().compareTo(new BigDecimal(total)));
    }
}
