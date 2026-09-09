package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.dto.ApuPatchRequest;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.insumo.dto.CopiarBaseRequest;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.service.BaseInsumosService;
import ec.uce.propuestas.insumo.service.CopiaBaseService;
import ec.uce.propuestas.insumo.service.ImportacionInsumoService;
import ec.uce.propuestas.insumo.service.InsumoCrudService;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.proyecto.dto.ProyectoCrearRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoEditarRequest;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.admin.UsuarioAdminService;
import ec.uce.propuestas.usuario.audit.entity.LogActividad;
import ec.uce.propuestas.usuario.audit.repository.LogActividadRepository;
import ec.uce.propuestas.usuario.auth.AuthService;
import ec.uce.propuestas.usuario.auth.PasswordService;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import ec.uce.propuestas.usuario.auth.dto.AceptarInvitacionRequest;
import ec.uce.propuestas.usuario.auth.dto.LoginRequest;
import ec.uce.propuestas.usuario.auth.dto.PasswordCambiarRequest;
import ec.uce.propuestas.usuario.auth.dto.RegistroRequest;
import ec.uce.propuestas.usuario.auth.dto.RestablecerPasswordRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/** Runtime coverage for the Plan 038 D-13 identity and catalog emitters. */
@QuarkusTest
class Plan038LogActividadIT {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    AuthService authService;

    @Inject
    UsuarioAdminService usuarioAdminService;

    @Inject
    ProyectoService proyectoService;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    BaseInsumosService baseInsumosService;

    @Inject
    InsumoCrudService insumoCrudService;

    @Inject
    ImportacionInsumoService importacionInsumoService;

    @Inject
    CopiaBaseService copiaBaseService;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ApuCrudService apuCrudService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    LogActividadRepository logRepository;

    @BeforeEach
    @AfterEach
    void reset() throws Exception {
        mailbox.clear();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "actividad, cronograma, insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, log_actividad, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void auth_emite_registro_login_logout_password_y_activacion_invitacion() {
        authService.registrar(new RegistroRequest("Audit", "audit@ex.com", "Pass1234", "Pass1234"));
        String verification = tokenCorreo("verificacion", "audit@ex.com");
        authService.verificarEmail(verification);

        var tokens = authService.login(new LoginRequest("audit@ex.com", "Pass1234", true));
        authService.logout(tokens.refreshToken());
        authService.cambiarPassword("audit@ex.com", new PasswordCambiarRequest("Pass1234", "NewPass99", "NewPass99"));

        mailbox.clear();
        authService.iniciarRecuperacion("audit@ex.com");
        authService.restablecerPassword(
                new RestablecerPasswordRequest(tokenCorreo("reset", "audit@ex.com"), "ResetPass9", "ResetPass9"));

        mailbox.clear();
        Usuario invitado = usuarioAdminService.invitar("Invitado", "inv@ex.com", Rol.USUARIO);
        authService.aceptarInvitacion(
                new AceptarInvitacionRequest(tokenCorreo("invitacion", "inv@ex.com"), "InvitePass9", "InvitePass9"));

        assertEquals(1, contar(EventoLogActividad.AUTH_REGISTRO));
        assertEquals(1, contar(EventoLogActividad.AUTH_LOGIN));
        assertEquals(1, contar(EventoLogActividad.AUTH_LOGOUT));
        assertEquals(2, contar(EventoLogActividad.AUTH_PASSWORD_CAMBIADA));
        assertEquals(1, contar(EventoLogActividad.USUARIO_ACTIVADO));
        assertEquals(invitado.publicId, primero(EventoLogActividad.USUARIO_ACTIVADO).entidadPublicId);
        assertEquals(
                Set.of("perfil", "reset"),
                detalles(EventoLogActividad.AUTH_PASSWORD_CAMBIADA).stream()
                        .map(detalle -> detalle.get("origen").toString())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                "invitacion",
                detalle(primero(EventoLogActividad.USUARIO_ACTIVADO)).get("origen"));
    }

    @Test
    void proyecto_emite_crear_editar_eliminar_y_duplicado_sigue_sin_productor() {
        Usuario usuario = insertarUsuario("proyecto@ex.com");
        var creado = proyectoService.crear(usuario.id, proyectoRequest("Auditoría"));
        proyectoService.actualizar(usuario.id, creado.id(), proyectoEditarRequest("Auditoría editada"));
        var eliminable = proyectoService.crear(usuario.id, proyectoRequest("Eliminar"));
        proyectoService.eliminar(usuario.id, eliminable.id());

        assertEquals(2, contar(EventoLogActividad.PROYECTO_CREADO));
        assertEquals(1, contar(EventoLogActividad.PROYECTO_EDITADO));
        assertEquals(1, contar(EventoLogActividad.PROYECTO_ELIMINADO));
        assertEquals(0, contar(EventoLogActividad.PROYECTO_DUPLICADO));
        assertEquals(creado.id(), primero(EventoLogActividad.PROYECTO_EDITADO).entidadPublicId);
    }

    @Test
    void insumo_crud_e_importacion_emiten_sin_duplicar_creados_del_csv() {
        Contexto contexto = crearContexto("insumo@ex.com");
        var creado = insumoCrudService.crear(
                contexto.base.id,
                new InsumoCrearRequest("MAT-1", TipoInsumo.MATERIAL, "Material", "kg", new BigDecimal("1.25")));
        insumoCrudService.actualizar(
                contexto.base.id,
                creado.id(),
                new InsumoEditarRequest("Material editado", "kg", new BigDecimal("1.50")));
        insumoCrudService.eliminar(contexto.base.id, creado.id());

        String csv = "codigo,descripcion,unidad,precio\nIMP-1,Importado,kg,2.00\n";
        importacionInsumoService.importarCsv(contexto.base.id, csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, contar(EventoLogActividad.INSUMO_CREADO));
        assertEquals(1, contar(EventoLogActividad.INSUMO_EDITADO));
        assertEquals(1, contar(EventoLogActividad.INSUMO_ELIMINADO));
        assertEquals(1, contar(EventoLogActividad.INSUMOS_IMPORT_CSV));
        Map<String, Object> importDetail = detalle(primero(EventoLogActividad.INSUMOS_IMPORT_CSV));
        assertEquals(1, ((Number) importDetail.get("creados")).intValue());
        assertEquals(0, ((Number) importDetail.get("errores")).intValue());
    }

    @Test
    void copiar_base_emite_ids_publicos_y_conteos_canonicos() {
        Contexto contexto = crearContexto("copy@ex.com");
        BaseInsumos central = baseInsumosService.crearCentral("Central audit");
        insumoCrudService.crear(
                central.id,
                new InsumoCrearRequest("CENT-1", TipoInsumo.MATERIAL, "Central", "kg", new BigDecimal("3.00")));

        copiaBaseService.copiar(
                new CopiarBaseRequest("CENTRAL", central.publicId, contexto.proyecto.publicId), contexto.usuario.id);

        assertEquals(1, contar(EventoLogActividad.BASE_COPIADA_A_PROYECTO));
        LogActividad row = primero(EventoLogActividad.BASE_COPIADA_A_PROYECTO);
        assertEquals(contexto.base.publicId, row.entidadPublicId);
        Map<String, Object> detail = detalle(row);
        assertEquals(central.publicId.toString(), detail.get("baseOrigenId"));
        assertEquals(contexto.proyecto.publicId.toString(), detail.get("proyectoDestinoId"));
        assertEquals(1, ((Number) detail.get("cantidadInsumos")).intValue());
    }

    @Test
    void apu_emite_crear_tres_ediciones_de_agregado_y_eliminar() {
        Contexto contexto = crearContexto("apu@ex.com");
        Presupuesto presupuesto = presupuestoRepository
                .findVigenteDeProyecto(contexto.proyecto.id)
                .orElseThrow();
        var creado = apuCrudService
                .crear(presupuesto.id, new ApuCrearRequest("APU-AUD", "APU auditoría", "u"), contexto.usuario.id)
                .apu();
        Apu apu = apuRepository.findByPublicId(creado.id()).orElseThrow();

        apuCrudService.editarCabecera(
                apu.id,
                new ApuPatchRequest(
                        JsonNullable.undefined(), JsonNullable.of("APU editado"), JsonNullable.undefined()));
        apuCrudService.actualizarPorcentajeIndirecto(apu.id, new BigDecimal("0.1200"));
        apuCrudService.guardarEspecificacionTecnica(apu.id, "ET");
        apuCrudService.eliminar(apu.id);

        assertEquals(1, contar(EventoLogActividad.APU_CREADO));
        assertEquals(3, contar(EventoLogActividad.APU_EDITADO));
        assertEquals(1, contar(EventoLogActividad.APU_ELIMINADO));
        assertEquals(creado.id(), primero(EventoLogActividad.APU_ELIMINADO).entidadPublicId);
    }

    @Test
    void detalles_plan038_no_contienen_pii_ni_secretos() {
        authService.registrar(new RegistroRequest("Safe", "safe@ex.com", "Pass1234", "Pass1234"));
        String serialized = logRepository.listAll().stream()
                .map(row -> row.detalle)
                .reduce("", String::concat)
                .toLowerCase();
        assertFalse(serialized.contains("safe@ex.com"));
        assertFalse(serialized.contains("password"));
        assertFalse(serialized.contains("token"));
        assertFalse(serialized.contains("jwt"));
        assertFalse(serialized.contains("hash"));
    }

    private Contexto crearContexto(String email) {
        Usuario usuario = insertarUsuario(email);
        var response = proyectoService.crear(usuario.id, proyectoRequest("Contexto"));
        Proyecto proyecto = proyectoRepository
                .findByPublicIdAndOwnerScope(response.id(), usuario.id)
                .orElseThrow();
        BaseInsumos base = baseInsumosService.asegurarBaseProyecto(proyecto.id);
        return new Contexto(usuario, proyecto, base);
    }

    @Transactional
    Usuario insertarUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.nombre = "Audit";
        usuario.email = email;
        usuario.passwordHash = passwordService.hash("Pass1234");
        usuario.rol = Rol.USUARIO;
        usuario.activo = true;
        usuario.emailVerificado = true;
        usuarioRepository.persist(usuario);
        usuarioRepository.flush();
        return usuario;
    }

    private ProyectoCrearRequest proyectoRequest(String nombre) {
        return new ProyectoCrearRequest(
                nombre,
                "AUD",
                "Auditoría",
                (short) 2026,
                LocalDate.of(2026, 1, 1),
                (short) 30,
                "MES",
                "Dirección",
                null);
    }

    private ProyectoEditarRequest proyectoEditarRequest(String nombre) {
        return new ProyectoEditarRequest(
                nombre,
                "AUD-E",
                "Editado",
                (short) 2026,
                LocalDate.of(2026, 1, 2),
                (short) 31,
                "MES",
                "Dirección",
                null);
    }

    private String tokenCorreo(String tipo, String destinatario) {
        return mailbox.entregas().stream()
                .filter(entrega ->
                        entrega.tipo().equals(tipo) && entrega.destinatario().equals(destinatario))
                .reduce((primero, segundo) -> segundo)
                .orElseThrow()
                .tokenRaw();
    }

    private long contar(EventoLogActividad evento) {
        return logRepository.count("evento", evento.value());
    }

    private LogActividad primero(EventoLogActividad evento) {
        LogActividad row = logRepository.find("evento", evento.value()).firstResult();
        assertNotNull(row);
        return row;
    }

    private List<Map<String, Object>> detalles(EventoLogActividad evento) {
        return logRepository.<LogActividad>find("evento", evento.value()).list().stream()
                .map(this::detalle)
                .toList();
    }

    private Map<String, Object> detalle(LogActividad row) {
        try {
            return objectMapper.readValue(row.detalle, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Contexto(Usuario usuario, Proyecto proyecto, BaseInsumos base) {}
}
