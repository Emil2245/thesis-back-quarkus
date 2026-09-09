package ec.uce.propuestas.usuario.audit;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/** Runtime proof that every implemented D-13 event is emitted by its canonical producer. */
@QuarkusTest
class LogActividadCoberturaD13CompletaTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> LEGACY =
            Set.of("base.insumos.copiada", "rubro.creado", "cronograma.creado", "presupuesto.vigente_marcado");

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
    void todos_los_productores_implementados_emiten_el_catalogo_exacto_sin_legacy() {
        producirAuthYUsuario();
        Contexto contexto = producirProyectoInsumoBaseYApu();
        producirAdministracion(contexto);
        producirPresupuestoCronogramaYDocumento(contexto);

        Set<String> esperados = Arrays.stream(EventoLogActividad.values())
                .map(EventoLogActividad::value)
                .filter(value -> !value.equals(EventoLogActividad.PROYECTO_DUPLICADO.value()))
                .collect(Collectors.toSet());
        Set<String> emitidos = logRepository.<LogActividad>listAll().stream()
                .map(row -> row.evento)
                .collect(Collectors.toSet());

        assertEquals(25, esperados.size());
        assertEquals(esperados, emitidos);
        assertEquals(0, logRepository.count("evento", EventoLogActividad.PROYECTO_DUPLICADO.value()));
        LEGACY.forEach(nombre -> assertEquals(0, logRepository.count("evento", nombre)));
        logRepository.<LogActividad>listAll().forEach(this::assertDetalleSinPiiNiSecretos);
    }

    @Test
    void cobertura_runtime_de_eventos_de_planes_034_a_039_incrementa_cada_evento_faltante() {
        producirAuthYUsuario();
        Contexto contexto = producirProyectoInsumoBaseYApu();
        producirAdministracion(contexto);
        producirPresupuestoCronogramaYDocumento(contexto);

        assertEventoEmitido(EventoLogActividad.USUARIO_INVITADO);
        assertEventoEmitido(EventoLogActividad.USUARIO_DESACTIVADO);
        assertEventoEmitido(EventoLogActividad.USUARIO_ACTIVADO);
        assertEventoEmitido(EventoLogActividad.ADMIN_BASE_EDITADA);
        assertEventoEmitido(EventoLogActividad.ADMIN_PLANTILLA_EDITADA);
        assertEventoEmitido(EventoLogActividad.ADMIN_PARAMETROS_EDITADOS);
        assertEventoEmitido(EventoLogActividad.PRESUPUESTO_VERSION_CREADA);
        assertEventoEmitido(EventoLogActividad.PRESUPUESTO_VERSION_ACTIVADA);
        assertEventoEmitido(EventoLogActividad.CRONOGRAMA_EDITADO);
        assertEventoEmitido(EventoLogActividad.DOCUMENTO_EXPORTADO);
    }

    private void assertEventoEmitido(EventoLogActividad evento) {
        assertFalse(logRepository.count("evento", evento.value()) == 0, "No se emitió " + evento.value());
    }

    private void producirAuthYUsuario() {
        authService.registrar(new RegistroRequest("Audit", "audit@ex.com", "Pass1234", "Pass1234"));
        authService.verificarEmail(tokenCorreo("verificacion", "audit@ex.com"));
        var tokens = authService.login(new LoginRequest("audit@ex.com", "Pass1234", true));
        authService.logout(tokens.refreshToken());
        authService.cambiarPassword("audit@ex.com", new PasswordCambiarRequest("Pass1234", "NewPass99", "NewPass99"));
        mailbox.clear();
        authService.iniciarRecuperacion("audit@ex.com");
        authService.restablecerPassword(
                new RestablecerPasswordRequest(tokenCorreo("reset", "audit@ex.com"), "ResetPass9", "ResetPass9"));

        mailbox.clear();
        Usuario invitado = usuarioAdminService.invitar("Invitado", "inv@ex.com", Rol.USUARIO);
        usuarioAdminService.desactivar(invitado.publicId);
        usuarioAdminService.reactivar(invitado.publicId);
        usuarioAdminService.desactivar(invitado.publicId);
        authService.aceptarInvitacion(
                new AceptarInvitacionRequest(tokenCorreo("invitacion", "inv@ex.com"), "InvitePass9", "InvitePass9"));
    }

    private Contexto producirProyectoInsumoBaseYApu() {
        Contexto contexto = crearContexto("owner@ex.com");
        proyectoService.actualizar(contexto.usuario.id, contexto.proyecto.publicId, proyectoEditarRequest("Editado"));
        var eliminable = proyectoService.crear(contexto.usuario.id, proyectoRequest("Eliminar"));
        proyectoService.eliminar(contexto.usuario.id, eliminable.id());

        var insumo = insumoCrudService.crear(
                contexto.base.id,
                new InsumoCrearRequest("MAT-1", TipoInsumo.MATERIAL, "Material", "kg", new BigDecimal("1.25")));
        insumoCrudService.actualizar(
                contexto.base.id,
                insumo.id(),
                new InsumoEditarRequest("Material editado", "kg", new BigDecimal("1.50")));
        insumoCrudService.eliminar(contexto.base.id, insumo.id());
        importacionInsumoService.importarCsv(
                contexto.base.id,
                "codigo,descripcion,unidad,precio\nIMP-1,Importado,kg,2.00\n".getBytes(StandardCharsets.UTF_8));

        BaseInsumos central = baseInsumosService.crearCentral("Central audit");
        insumoCrudService.crear(
                central.id,
                new InsumoCrearRequest("CENT-1", TipoInsumo.MATERIAL, "Central", "kg", new BigDecimal("3.00")));
        copiaBaseService.copiar(
                new CopiarBaseRequest("CENTRAL", central.publicId, contexto.proyecto.publicId), contexto.usuario.id);

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
        apuCrudService.guardarEspecificacionTecnica(apu.id, "Especificación técnica auditable");

        var borrar = apuCrudService
                .crear(presupuesto.id, new ApuCrearRequest("APU-DEL", "APU eliminable", "u"), contexto.usuario.id)
                .apu();
        apuCrudService.eliminar(apuRepository.findByPublicId(borrar.id()).orElseThrow().id);
        return new Contexto(contexto.usuario, contexto.proyecto, contexto.base, presupuesto, apu);
    }

    private String producirAdministracion(Contexto contexto) {
        Usuario admin = insertarUsuario("admin@ex.com", Rol.SUPER_ADMIN);
        String token = authService
                .login(new LoginRequest(admin.email, "Pass1234", false))
                .accessToken();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Central administrada"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "desdeApuId", contexto.apu.publicId.toString(),
                        "nombre", "Plantilla auditada",
                        "descripcionRubro", "Rubro auditado"))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(201);

        Map<String, Object> parametros = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
        Map<String, Object> body = new LinkedHashMap<>(parametros);
        body.remove("id");
        body.remove("updatedAt");
        BigDecimal hmActual = new BigDecimal(String.valueOf(parametros.get("porcentajeHerramientaMenor")));
        body.put("porcentajeHerramientaMenor", hmActual.compareTo(new BigDecimal("0.0600")) == 0 ? "0.0700" : "0.0600");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200);
        return token;
    }

    private void producirPresupuestoCronogramaYDocumento(Contexto contexto) {
        String token = authService
                .login(new LoginRequest(contexto.usuario.email, "Pass1234", false))
                .accessToken();
        String versionNueva = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", contexto.presupuesto.publicId.toString(), "notas", "Cobertura D-13"))
                .when()
                .post("/api/v1/proyectos/" + contexto.proyecto.publicId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");
        assertNotNull(versionNueva);
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + versionNueva + "/vigente")
                .then()
                .statusCode(200);

        String cronogramaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .post("/api/v1/presupuestos/" + versionNueva + "/cronograma")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 3))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200);

        byte[] documento = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + versionNueva)
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        assertFalse(documento.length == 0);
    }

    private Contexto crearContexto(String email) {
        Usuario usuario = insertarUsuario(email, Rol.USUARIO);
        var response = proyectoService.crear(usuario.id, proyectoRequest("Contexto"));
        Proyecto proyecto = proyectoRepository
                .findByPublicIdAndOwnerScope(response.id(), usuario.id)
                .orElseThrow();
        BaseInsumos base = baseInsumosService.asegurarBaseProyecto(proyecto.id);
        Presupuesto presupuesto =
                presupuestoRepository.findVigenteDeProyecto(proyecto.id).orElseThrow();
        return new Contexto(usuario, proyecto, base, presupuesto, null);
    }

    @Transactional
    Usuario insertarUsuario(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.nombre = "Audit";
        usuario.email = email;
        usuario.passwordHash = passwordService.hash("Pass1234");
        usuario.rol = rol;
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

    private void assertDetalleSinPiiNiSecretos(LogActividad row) {
        try {
            String serialized =
                    objectMapper.readValue(row.detalle, MAP_TYPE).toString().toLowerCase();
            for (String forbidden : Set.of("@", "password", "contraseña", "tokenraw", "jwt", "hash", "bearer")) {
                assertFalse(serialized.contains(forbidden), row.evento + " contiene " + forbidden);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Contexto(Usuario usuario, Proyecto proyecto, BaseInsumos base, Presupuesto presupuesto, Apu apu) {}
}
