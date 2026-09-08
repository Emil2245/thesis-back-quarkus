package ec.uce.propuestas.usuario.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Closed D-13 activity-event catalog signed by the Plan 032 reconciliation act. */
public enum EventoLogActividad {
    AUTH_LOGIN("auth.login", "Inicio de sesión", "resultado"),
    AUTH_LOGOUT("auth.logout", "Cierre de sesión", "resultado"),
    AUTH_REGISTRO("auth.registro", "Registro de usuario", "usuarioId"),
    AUTH_PASSWORD_CAMBIADA("auth.password_cambiada", "Contraseña cambiada", "origen"),
    USUARIO_INVITADO("usuario.invitado", "Usuario invitado", "tokenExpiraEn"),
    USUARIO_ACTIVADO("usuario.activado", "Usuario activado", "origen"),
    USUARIO_DESACTIVADO("usuario.desactivado", "Usuario desactivado", "origen"),
    PROYECTO_CREADO("proyecto.creado", "Proyecto creado"),
    PROYECTO_EDITADO("proyecto.editado", "Proyecto editado"),
    PROYECTO_ELIMINADO("proyecto.eliminado", "Proyecto eliminado"),
    PROYECTO_DUPLICADO("proyecto.duplicado", "Proyecto duplicado", "proyectoOrigenId", "proyectoDuplicadoId"),
    INSUMO_CREADO("insumo.creado", "Insumo creado", "codigoInsumo", "tipo"),
    INSUMO_EDITADO("insumo.editado", "Insumo editado"),
    INSUMO_ELIMINADO("insumo.eliminado", "Insumo eliminado"),
    INSUMOS_IMPORT_CSV("insumos.import_csv", "Insumos importados desde CSV", "creados", "actualizados", "errores"),
    BASE_COPIADA_A_PROYECTO(
            "base.copiada_a_proyecto",
            "Base copiada a proyecto",
            "baseOrigenId",
            "proyectoDestinoId",
            "cantidadInsumos",
            "cantidadOmitidos"),
    APU_CREADO("apu.creado", "APU creado"),
    APU_EDITADO("apu.editado", "APU editado"),
    APU_ELIMINADO("apu.eliminado", "APU eliminado"),
    PRESUPUESTO_VERSION_CREADA(
            "presupuesto.version_creada", "Versión de presupuesto creada", "presupuestoOrigenId", "versionNueva"),
    PRESUPUESTO_VERSION_ACTIVADA(
            "presupuesto.version_activada",
            "Versión de presupuesto activada",
            "version",
            "presupuestoPreviamenteVigenteId"),
    CRONOGRAMA_EDITADO("cronograma.editado", "Cronograma editado", "operacion"),
    DOCUMENTO_EXPORTADO("documento.exportado", "Documento exportado", "formato", "bytes", "stale"),
    ADMIN_BASE_EDITADA("admin.base_editada", "Base central editada", "operacion", "cantidadInsumos"),
    ADMIN_PLANTILLA_EDITADA("admin.plantilla_editada", "Plantilla de sistema editada", "operacion", "tipo"),
    ADMIN_PARAMETROS_EDITADOS(
            "admin.parametros_editados",
            "Parámetros administrativos editados",
            "operacion",
            "camposModificados",
            "clave");

    private static final Map<String, EventoLogActividad> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EventoLogActividad::value, Function.identity()));
    private static final Map<EventoLogActividad, Set<String>> DETALLES_ESPERADOS = construirDetallesEsperados();

    private final String value;
    private final String descripcionEs;
    private final Set<String> clavesDetalle;

    EventoLogActividad(String value, String descripcionEs, String... clavesDetalle) {
        this.value = value;
        this.descripcionEs = descripcionEs;
        this.clavesDetalle = Set.of(clavesDetalle);
    }

    public String value() {
        return value;
    }

    public String descripcionEs() {
        return descripcionEs;
    }

    /** Parses only canonical runtime names; historical V004 aliases are intentionally rejected. */
    public static EventoLogActividad parse(String value) {
        EventoLogActividad evento = BY_VALUE.get(value);
        if (evento == null) {
            throw new IllegalArgumentException("Evento de log desconocido: " + value);
        }
        return evento;
    }

    /** Complete, immutable signed matrix of event to allowed detail keys. */
    public static Map<EventoLogActividad, Set<String>> detallesEsperados() {
        return DETALLES_ESPERADOS;
    }

    private static Map<EventoLogActividad, Set<String>> construirDetallesEsperados() {
        Map<EventoLogActividad, Set<String>> matriz = new LinkedHashMap<>();
        for (EventoLogActividad evento : values()) {
            matriz.put(evento, evento.clavesDetalle);
        }
        return Collections.unmodifiableMap(matriz);
    }
}
