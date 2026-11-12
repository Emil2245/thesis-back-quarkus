package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogActividadDetalleValidatorTest {

    @Test
    void acepta_la_forma_minima_de_cada_evento_canonico() {
        for (EventoLogActividad evento : EventoLogActividad.values()) {
            assertDoesNotThrow(
                    () -> LogActividadDetalleValidator.validar(evento, DetalleCanonicoFixtures.para(evento)),
                    evento.value());
        }
    }

    @Test
    void rechaza_evento_runtime_desconocido() {
        assertThrows(
                IllegalArgumentException.class, () -> LogActividadDetalleValidator.validar("rubro.creado", Map.of()));
    }

    @Test
    void rechaza_claves_ajenas_faltantes_y_tipos_incorrectos() {
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.AUTH_LOGIN, Map.of("resultado", "ok", "correo", "dato")));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(EventoLogActividad.AUTH_LOGIN, Map.of()));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.INSUMOS_IMPORT_CSV, Map.of("creados", 1L, "actualizados", 0, "errores", 0)));
    }

    @Test
    void rechaza_valores_fuera_de_los_dominios_firmados() {
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.USUARIO_ACTIVADO, Map.of("origen", "perfil")));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.INSUMO_CREADO, Map.of("codigoInsumo", "EQ-1", "tipo", "SERVICIO")));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.DOCUMENTO_EXPORTADO, Map.of("formato", "CSV", "bytes", 1L, "stale", false)));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.ADMIN_BASE_EDITADA, Map.of("operacion", "fusionar", "cantidadInsumos", 1)));
    }

    @Test
    void acepta_cada_campo_canonico_de_parametros() {
        List<String> camposCanonicos = List.of(
                "porcentajeHerramientaMenor",
                "porcentajeIndirecto",
                "iva",
                "rangoHmMin",
                "rangoHmMax",
                "rangoCiMin",
                "rangoCiMax",
                "rangoDescuentoMin",
                "rangoDescuentoMax",
                "rangoIvaMin",
                "rangoIvaMax",
                "moneda",
                "mostrarSeccionesVacias",
                "sufijosSeccionActivos",
                "mostrarSubtotalesSeccion",
                "mostrarSubtotalesPie",
                "mostrarNombreProyectoHeader",
                "enumerarApus",
                "mensajeFooter",
                "modoCodigoRubro");

        camposCanonicos.forEach(campo -> assertDoesNotThrow(
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.ADMIN_PARAMETROS_EDITADOS,
                        Map.of("operacion", "defaults.update", "camposModificados", List.of(campo))),
                campo));
    }

    @Test
    void rechaza_campo_de_parametros_desconocido() {
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.ADMIN_PARAMETROS_EDITADOS,
                        Map.of("operacion", "defaults.update", "camposModificados", List.of("campoDesconocido"))));
    }

    @Test
    void respeta_forma_de_valor_referencia_y_dos_origenes_de_activacion() {
        assertDoesNotThrow(() -> LogActividadDetalleValidator.validar(
                EventoLogActividad.ADMIN_PARAMETROS_EDITADOS,
                Map.of("operacion", "valor_referencia.update", "clave", "SBU")));
        assertDoesNotThrow(() ->
                LogActividadDetalleValidator.validar(EventoLogActividad.USUARIO_ACTIVADO, Map.of("origen", "admin")));
        assertDoesNotThrow(() -> LogActividadDetalleValidator.validar(
                EventoLogActividad.USUARIO_ACTIVADO, Map.of("origen", "invitacion")));
    }

    @Test
    void rechaza_pii_y_secret_values() {
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.INSUMO_CREADO,
                        Map.of("codigoInsumo", "persona@example.com", "tipo", "EQUIPO")));
        assertThrows(
                IllegalStateException.class,
                () -> LogActividadDetalleValidator.validar(
                        EventoLogActividad.INSUMO_CREADO, Map.of("codigoInsumo", "Bearer secreto", "tipo", "EQUIPO")));
    }
}
