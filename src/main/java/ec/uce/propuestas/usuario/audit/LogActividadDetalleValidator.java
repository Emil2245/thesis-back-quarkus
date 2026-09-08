package ec.uce.propuestas.usuario.audit;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Enforces the closed, server-authored detail shapes from the signed Plan 032 act. */
public final class LogActividadDetalleValidator {

    private static final Set<String> TIPOS_INSUMO = Set.of("EQUIPO", "MANO_OBRA", "MATERIAL", "TRANSPORTE");
    private static final Set<String> FORMATOS = Set.of("XLSX", "PDF", "MSPDI", "DOCX");
    private static final Set<String> OPERACIONES_CRONOGRAMA = Set.of(
            "configurar",
            "programar.reemplazar_avances",
            "programar.distribuir_uniforme",
            "programar.mover_segmento",
            "programar.redimensionar_segmento",
            "revisar");
    private static final Set<String> OPERACIONES_BASE = Set.of(
            "crear", "renombrar", "archivar", "borrar", "importar", "crearInsumo", "editarInsumo", "borrarInsumo");
    private static final Set<String> OPERACIONES_PLANTILLA = Set.of("crear", "editar", "borrar");
    private static final Set<String> OPERACIONES_PARAMETROS =
            Set.of("defaults.update", "valor_referencia.insert", "valor_referencia.update", "valor_referencia.delete");
    private static final Set<String> CAMPOS_PARAMETROS = Set.of(
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
    private static final Pattern EMAIL = Pattern.compile("(?i).*@\\S+\\.\\S+.*");
    private static final Pattern SECRET =
            Pattern.compile("(?i).*(password|token|jwt|bearer|hash|iporigen|email|correo).*");

    private LogActividadDetalleValidator() {}

    public static void validar(String evento, Map<String, Object> detalle) {
        validar(EventoLogActividad.parse(evento), detalle);
    }

    public static void validar(EventoLogActividad evento, Map<String, Object> detalle) {
        if (evento == null) {
            throw new IllegalArgumentException("Evento de log requerido");
        }
        Map<String, Object> seguro = detalle == null ? Map.of() : detalle;
        validarSinPiiNiSecretos(seguro);

        if (evento == EventoLogActividad.ADMIN_PARAMETROS_EDITADOS) {
            validarParametros(seguro);
            return;
        }

        Set<String> esperadas = EventoLogActividad.detallesEsperados().get(evento);
        if (!seguro.keySet().equals(esperadas)) {
            throw new IllegalStateException("Forma de detalle inválida para " + evento.value());
        }

        switch (evento) {
            case AUTH_LOGIN, AUTH_LOGOUT -> exigirValor(seguro, "resultado", String.class, Set.of("ok"));
            case AUTH_REGISTRO -> exigirTipo(seguro, "usuarioId", UUID.class, false);
            case AUTH_PASSWORD_CAMBIADA -> exigirValor(seguro, "origen", String.class, Set.of("perfil", "reset"));
            case USUARIO_INVITADO -> exigirInstant(seguro, "tokenExpiraEn");
            case USUARIO_ACTIVADO -> exigirValor(seguro, "origen", String.class, Set.of("admin", "invitacion"));
            case USUARIO_DESACTIVADO -> exigirValor(seguro, "origen", String.class, Set.of("admin"));
            case PROYECTO_DUPLICADO -> exigirUuid(seguro, "proyectoOrigenId", "proyectoDuplicadoId");
            case INSUMO_CREADO -> {
                exigirTexto(seguro, "codigoInsumo");
                exigirValor(seguro, "tipo", String.class, TIPOS_INSUMO);
            }
            case INSUMOS_IMPORT_CSV -> exigirEnterosNoNegativos(seguro, "creados", "actualizados", "errores");
            case BASE_COPIADA_A_PROYECTO -> {
                exigirUuid(seguro, "baseOrigenId", "proyectoDestinoId");
                exigirEnterosNoNegativos(seguro, "cantidadInsumos", "cantidadOmitidos");
            }
            case PRESUPUESTO_VERSION_CREADA -> {
                exigirTipo(seguro, "presupuestoOrigenId", UUID.class, false);
                exigirEnterosPositivos(seguro, "versionNueva");
            }
            case PRESUPUESTO_VERSION_ACTIVADA -> {
                exigirEnterosPositivos(seguro, "version");
                exigirTipo(seguro, "presupuestoPreviamenteVigenteId", UUID.class, true);
            }
            case CRONOGRAMA_EDITADO -> exigirValor(seguro, "operacion", String.class, OPERACIONES_CRONOGRAMA);
            case DOCUMENTO_EXPORTADO -> {
                exigirValor(seguro, "formato", String.class, FORMATOS);
                exigirLongNoNegativo(seguro, "bytes");
                exigirTipo(seguro, "stale", Boolean.class, false);
            }
            case ADMIN_BASE_EDITADA -> {
                exigirValor(seguro, "operacion", String.class, OPERACIONES_BASE);
                exigirEnterosNoNegativos(seguro, "cantidadInsumos");
            }
            case ADMIN_PLANTILLA_EDITADA -> {
                exigirValor(seguro, "operacion", String.class, OPERACIONES_PLANTILLA);
                exigirValor(seguro, "tipo", String.class, Set.of("SISTEMA"));
            }
            default -> {
                // Empty-detail events are fully validated by the exact key-set check.
            }
        }
    }

    private static void validarParametros(Map<String, Object> detalle) {
        Object operacionRaw = detalle.get("operacion");
        if (!(operacionRaw instanceof String operacion) || !OPERACIONES_PARAMETROS.contains(operacion)) {
            throw new IllegalStateException("Operación de parámetros inválida");
        }
        if (operacion.equals("defaults.update")) {
            if (!detalle.keySet().equals(Set.of("operacion", "camposModificados"))) {
                throw new IllegalStateException("Forma defaults.update inválida");
            }
            Object camposRaw = detalle.get("camposModificados");
            if (!(camposRaw instanceof List<?> campos)
                    || campos.isEmpty()
                    || campos.stream().anyMatch(c -> !(c instanceof String) || !CAMPOS_PARAMETROS.contains(c))) {
                throw new IllegalStateException("camposModificados inválido");
            }
            return;
        }
        if (!detalle.keySet().equals(Set.of("operacion", "clave"))) {
            throw new IllegalStateException("Forma valor_referencia inválida");
        }
        exigirTexto(detalle, "clave");
    }

    private static void validarSinPiiNiSecretos(Map<String, Object> detalle) {
        for (Map.Entry<String, Object> entry : detalle.entrySet()) {
            if (!entry.getKey().equals("tokenExpiraEn")
                    && SECRET.matcher(entry.getKey()).matches()) {
                throw new IllegalStateException("Clave sensible no permitida en detalle");
            }
            validarValorSinPii(entry.getValue());
        }
    }

    private static void validarValorSinPii(Object value) {
        if (value instanceof String text
                && (text.contains("@")
                        || EMAIL.matcher(text).matches()
                        || SECRET.matcher(text).matches())) {
            throw new IllegalStateException("PII o secreto no permitido en detalle");
        }
        if (value instanceof Iterable<?> values) {
            values.forEach(LogActividadDetalleValidator::validarValorSinPii);
        }
        if (value instanceof Map<?, ?> values) {
            values.forEach((key, nested) -> {
                validarValorSinPii(key);
                validarValorSinPii(nested);
            });
        }
    }

    private static void exigirInstant(Map<String, Object> detalle, String clave) {
        Object value = detalle.get(clave);
        if (!(value instanceof String text)) {
            throw new IllegalStateException(clave + " debe ser ISO-8601 String");
        }
        try {
            Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException(clave + " debe ser ISO-8601 Instant", ex);
        }
    }

    private static void exigirUuid(Map<String, Object> detalle, String... claves) {
        for (String clave : claves) {
            exigirTipo(detalle, clave, UUID.class, false);
        }
    }

    private static void exigirTexto(Map<String, Object> detalle, String clave) {
        Object value = detalle.get(clave);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(clave + " debe ser texto no vacío");
        }
    }

    private static void exigirEnterosNoNegativos(Map<String, Object> detalle, String... claves) {
        for (String clave : claves) {
            Object value = detalle.get(clave);
            if (!(value instanceof Integer number) || number < 0) {
                throw new IllegalStateException(clave + " debe ser int no negativo");
            }
        }
    }

    private static void exigirEnterosPositivos(Map<String, Object> detalle, String... claves) {
        for (String clave : claves) {
            Object value = detalle.get(clave);
            if (!(value instanceof Integer number) || number < 1) {
                throw new IllegalStateException(clave + " debe ser int positivo");
            }
        }
    }

    private static void exigirLongNoNegativo(Map<String, Object> detalle, String clave) {
        Object value = detalle.get(clave);
        if (!(value instanceof Long number) || number < 0L) {
            throw new IllegalStateException(clave + " debe ser long no negativo");
        }
    }

    private static <T> void exigirValor(Map<String, Object> detalle, String clave, Class<T> tipo, Set<T> permitidos) {
        Object value = detalle.get(clave);
        if (!tipo.isInstance(value) || !permitidos.contains(tipo.cast(value))) {
            throw new IllegalStateException(clave + " fuera del dominio permitido");
        }
    }

    private static void exigirTipo(Map<String, Object> detalle, String clave, Class<?> tipo, boolean nullable) {
        Object value = detalle.get(clave);
        if (value == null && nullable) {
            return;
        }
        if (!tipo.isInstance(value)) {
            throw new IllegalStateException(clave + " tiene tipo inválido");
        }
        if (tipo == UUID.class) {
            UUID uuid = (UUID) value;
            if (uuid.version() != 7 || uuid.variant() != 2) {
                throw new IllegalStateException(clave + " debe ser UUIDv7");
            }
        }
    }
}
