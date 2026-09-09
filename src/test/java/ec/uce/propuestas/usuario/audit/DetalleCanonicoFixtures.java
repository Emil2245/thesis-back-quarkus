package ec.uce.propuestas.usuario.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DetalleCanonicoFixtures {

    private static final UUID ID_A = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000101");
    private static final UUID ID_B = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000102");

    private DetalleCanonicoFixtures() {}

    static Map<String, Object> para(EventoLogActividad evento) {
        return switch (evento) {
            case AUTH_LOGIN, AUTH_LOGOUT -> Map.of("resultado", "ok");
            case AUTH_REGISTRO -> Map.of("usuarioId", ID_A);
            case AUTH_PASSWORD_CAMBIADA -> Map.of("origen", "perfil");
            case USUARIO_INVITADO -> Map.of("tokenExpiraEn", "2026-09-10T00:00:00Z");
            case USUARIO_ACTIVADO -> Map.of("origen", "invitacion");
            case USUARIO_DESACTIVADO -> Map.of("origen", "admin");
            case PROYECTO_CREADO,
                    PROYECTO_EDITADO,
                    PROYECTO_ELIMINADO,
                    INSUMO_EDITADO,
                    INSUMO_ELIMINADO,
                    APU_CREADO,
                    APU_EDITADO,
                    APU_ELIMINADO -> Map.of();
            case PROYECTO_DUPLICADO -> Map.of("proyectoOrigenId", ID_A, "proyectoDuplicadoId", ID_B);
            case INSUMO_CREADO -> Map.of("codigoInsumo", "MAT-001", "tipo", "MATERIAL");
            case INSUMOS_IMPORT_CSV -> Map.of("creados", 1, "actualizados", 0, "errores", 0);
            case BASE_COPIADA_A_PROYECTO ->
                Map.of("baseOrigenId", ID_A, "proyectoDestinoId", ID_B, "cantidadInsumos", 1, "cantidadOmitidos", 0);
            case PRESUPUESTO_VERSION_CREADA -> Map.of("presupuestoOrigenId", ID_A, "versionNueva", 2);
            case PRESUPUESTO_VERSION_ACTIVADA -> {
                Map<String, Object> detalle = new LinkedHashMap<>();
                detalle.put("version", 2);
                detalle.put("presupuestoPreviamenteVigenteId", null);
                yield detalle;
            }
            case CRONOGRAMA_EDITADO -> Map.of("operacion", "configurar");
            case DOCUMENTO_EXPORTADO -> Map.of("formato", "PDF", "bytes", 128L, "stale", false);
            case ADMIN_BASE_EDITADA -> Map.of("operacion", "crear", "cantidadInsumos", 0);
            case ADMIN_PLANTILLA_EDITADA -> Map.of("operacion", "crear", "tipo", "SISTEMA");
            case ADMIN_PARAMETROS_EDITADOS ->
                Map.of("operacion", "defaults.update", "camposModificados", List.of("iva"));
        };
    }
}
