package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventoLogActividadTest {

    private static final List<String> CANON = List.of(
            "auth.login",
            "auth.logout",
            "auth.registro",
            "auth.password_cambiada",
            "usuario.invitado",
            "usuario.activado",
            "usuario.desactivado",
            "proyecto.creado",
            "proyecto.editado",
            "proyecto.eliminado",
            "proyecto.duplicado",
            "insumo.creado",
            "insumo.editado",
            "insumo.eliminado",
            "insumos.import_csv",
            "base.copiada_a_proyecto",
            "apu.creado",
            "apu.editado",
            "apu.eliminado",
            "presupuesto.version_creada",
            "presupuesto.version_activada",
            "cronograma.editado",
            "documento.exportado",
            "admin.base_editada",
            "admin.plantilla_editada",
            "admin.parametros_editados");

    @Test
    void catalogo_contiene_exactamente_los_26_eventos_firmados() {
        List<String> actuales = Arrays.stream(EventoLogActividad.values())
                .map(EventoLogActividad::value)
                .toList();
        assertEquals(26, actuales.size());
        assertEquals(CANON, actuales);
        assertEquals(26, new HashSet<>(actuales).size());
        assertTrue(Arrays.stream(EventoLogActividad.values())
                .allMatch(e -> !e.descripcionEs().isBlank()));
        assertEquals(26, EventoLogActividad.detallesEsperados().size());
    }

    @Test
    void parser_rechaza_desconocidos_y_los_cuatro_nombres_legacy() {
        assertThrows(IllegalArgumentException.class, () -> EventoLogActividad.parse("no.en.catalogo"));
        for (String legacy :
                List.of("base.insumos.copiada", "rubro.creado", "cronograma.creado", "presupuesto.vigente_marcado")) {
            assertThrows(IllegalArgumentException.class, () -> EventoLogActividad.parse(legacy));
            assertFalse(CANON.contains(legacy));
        }
    }

    @Test
    void matriz_de_claves_es_completa_y_exacta() {
        Map<EventoLogActividad, Set<String>> matriz = EventoLogActividad.detallesEsperados();
        assertEquals(Set.of("resultado"), matriz.get(EventoLogActividad.AUTH_LOGIN));
        assertEquals(Set.of("origen"), matriz.get(EventoLogActividad.USUARIO_ACTIVADO));
        assertEquals(
                Set.of("operacion", "camposModificados", "clave"),
                matriz.get(EventoLogActividad.ADMIN_PARAMETROS_EDITADOS));
        assertThrows(UnsupportedOperationException.class, () -> matriz.clear());
    }
}
