package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Mspdi;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.MspdiRow;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * MSPDI format coverage ported in stage 05 from Core's retired {@code CronogramaMspdiWriterTest}
 * (Plan 031 TC_P37_40..49) to the Documentos renderer fed by the captured {@link CronogramaProjectionV1}.
 * Characterization tests: the renderer already exists. Verifica:
 *
 * <ul>
 *   <li>Namespace oficial Project 2007 y esquema URL pinned.</li>
 *   <li>Validación contra perfil XSD offline (no se descarga el XSD canónico de
 *       Microsoft; el perfil cubre el subconjunto emitido).</li>
 *   <li>Validación contra XSD OFICIAL Project 2007 cuando se ejecuta con
 *       {@code -Dmspdi.official.xsd=/ruta/absoluta/a/mspdi_pj12.xsd}. SHA-256
 *       pinned en el canon
 *       ({@code a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4}).</li>
 *   <li>{@code SaveVersion=12} (Project 2007), {@code CalendarUID} entero
 *       positivo, UID/ID Task enteros no-cero.</li>
 *   <li>Parser XXE-safe (entidades externas deshabilitadas, FEATURE_SECURE
 *       activo).</li>
 *   <li>Un {@code <Project>} + un {@code <Task>} por rubro, UID deterministas,
 *       StartDate desde {@code Proyecto.fechaInicio}, sin dependencias/CPM/lag.</li>
 *   <li>Item/code/price/weight/map preservados en {@code <Notes>} + campos
 *       nativos (Cost, ActualCost, FixedCost) o
 *       {@code <ExtendedAttribute>} con PIDs estándar de Project 2007.</li>
 * </ul>
 */
class CronogramaMspdiFormatTest {

    private static final String NAMESPACE_2007 = "http://schemas.microsoft.com/project/2007";
    private static final String XSD_LOCATION = "mspdi/mspdi-pj12-profile.xsd";

    /** SHA-256 pinned en el canon Plan 031 §Fuentes. */
    private static final String OFICIAL_XSD_SHA256 = "a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4";

    @Test
    void TC_P37_40_mspdi_namespace_oficial_y_xsd_pinned_en_cabecera() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains(NAMESPACE_2007), "namespace Project 2007 oficial");
        assertTrue(
                xml.contains("http://schemas.microsoft.com/project/2007/mspdi_pj12.xsd"),
                "URL del XSD canónico pinned");
        // Root = <Project>
        assertTrue(xml.contains("<Project"), "<Project> raíz obligatorio");
        assertTrue(xml.contains("xmlns=\"" + NAMESPACE_2007 + "\""), "xmlns declarado en raíz");
    }

    @Test
    void TC_P37_41_mspdi_un_project_y_un_task_por_rubro_sin_dependencias() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        Document doc = parsearXxeSafe(bytes);
        org.w3c.dom.NodeList proyectos = doc.getElementsByTagNameNS(NAMESPACE_2007, "Project");
        assertEquals(1, proyectos.getLength(), "exactamente un Project");
        org.w3c.dom.NodeList tareas = doc.getElementsByTagNameNS(NAMESPACE_2007, "Task");
        assertEquals(2, tareas.getLength(), "un Task por rubro (2 rubros)");
        // No debe haber <PredecessorLink> ni <Link> ni nada de CPM.
        assertEquals(
                0,
                doc.getElementsByTagNameNS(NAMESPACE_2007, "PredecessorLink").getLength(),
                "no debe haber PredecessorLink (sin CPM/lag)");
        // UIDs de Task deben ser enteros positivos no-cero (xsd:integer).
        for (int i = 0; i < tareas.getLength(); i++) {
            var t = tareas.item(i);
            String uidText = buscarHijo(t, "UID").getTextContent();
            int uid = Integer.parseInt(uidText);
            assertTrue(uid > 0, "Task UID positivo no-cero: " + uid);
        }
    }

    @Test
    void TC_P37_42_mspdi_validacion_xsd_profile_exitosa() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        validarContraXsd(bytes, recursoPerfilXsd());
    }

    /**
     * Test focal que valida contra el XSD OFICIAL Project 2007 cuando se provee
     * {@code -Dmspdi.official.xsd=/ruta/absoluta/a/mspdi_pj12.xsd}. Sin esa
     * propiedad vuelve a validar el perfil offline y nunca introduce un skip en
     * la suite normal.
     *
     * <p>Para ejecutarlo en local con validación oficial:
     * <pre>{@code
     *   curl -sS -o /tmp/mspdi_pj12.xsd https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd
     *   sha256sum /tmp/mspdi_pj12.xsd  # debe coincidir con a3e9138f...c7b4
     *   ./gradlew :documentos:test --tests CronogramaMspdiFormatTest \
     *     -Dmspdi.official.xsd=/tmp/mspdi_pj12.xsd
     * }</pre>
     */
    @Test
    void TC_P37_47_mspdi_valida_xsd_oficial_project_2007() throws Exception {
        Path oficial = rutaXsdOficial();
        byte[] xml = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        if (oficial == null || !Files.isRegularFile(oficial)) {
            validarContraXsd(xml, recursoPerfilXsd());
            return;
        }
        // Hash pinned: rechazo si el archivo no coincide (defensa contra tampering).
        byte[] bytes = Files.readAllBytes(oficial);
        String actualSha = sha256(bytes);
        assertEquals(
                OFICIAL_XSD_SHA256,
                actualSha,
                "SHA-256 del XSD oficial NO coincide con el pinned en el canon Plan 031 §Fuentes");

        validarContraXsd(xml, Files.newInputStream(oficial));
        // Log de evidencia en el reporte.
        System.out.printf(
                "TC_P37_47: XML validado contra XSD oficial (%d bytes, SHA-256=%s OK)%n",
                bytes.length, OFICIAL_XSD_SHA256.substring(0, 12));
    }

    @Test
    void TC_P37_43_mspdi_startdate_y_uid_determinista_desde_proyecto_y_rubro() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        Document doc = parsearXxeSafe(bytes);
        org.w3c.dom.NodeList tareas = doc.getElementsByTagNameNS(NAMESPACE_2007, "Task");
        boolean encontroStart = false;
        for (int i = 0; i < tareas.getLength(); i++) {
            var t = tareas.item(i);
            var start = buscarHijo(t, "Start");
            assertNotNull(start, "cada Task debe tener Start");
            String startText = start.getTextContent();
            // xsd:dateTime con o sin zona — el writer emite 'Z' (UTC) por lo que
            // basta verificar que empieza con la fecha del proyecto.
            assertTrue(
                    startText.startsWith("2026-01-01T"),
                    "StartDate del proyecto (comienza con 2026-01-01T): " + startText);
            encontroStart = true;
        }
        assertTrue(encontroStart, "al menos un Task con Start");
    }

    @Test
    void TC_P37_44_mspdi_xxe_safe_no_resuelve_entidades_externas() throws Exception {
        // Intentamos inyectar una entidad externa; el parser debe rechazar.
        String malicioso = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
                + "<Project xmlns=\"" + NAMESPACE_2007 + "\">&xxe;</Project>";
        byte[] payload = malicioso.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            parsearXxeSafe(payload);
            org.junit.jupiter.api.Assertions.fail("el parser XXE-safe debe rechazar DTD/ENTITY");
        } catch (SAXParseException | ParserConfigurationException | RuntimeException expected) {
            // Esperado: el parser XXE-safe no debe resolver entidades externas.
        }
    }

    @Test
    void TC_P37_45_mspdi_metadata_en_notes_extended_y_campos_nativos() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        Document doc = parsearXxeSafe(bytes);
        org.w3c.dom.NodeList eas = doc.getElementsByTagNameNS(NAMESPACE_2007, "ExtendedAttribute");
        boolean tieneCodigo = false;
        boolean tienePrecio = false;
        boolean tienePeso = false;
        boolean tieneUnidad = false;
        for (int i = 0; i < eas.getLength(); i++) {
            var ea = eas.item(i);
            String fieldId = buscarHijo(ea, "FieldID").getTextContent();
            // PIDs Project 2007: Text1=188743731, Text2=188743732, Number1=188743737, Cost1=188743913.
            if (fieldId.equals("188743731")) {
                tieneCodigo = true;
            }
            if (fieldId.equals("188743737")) {
                tienePeso = true;
            }
            if (fieldId.equals("188743913")) {
                tienePrecio = true;
            }
            if (fieldId.equals("188743732")) {
                tieneUnidad = true;
            }
        }
        assertTrue(tieneCodigo, "ExtendedAttribute Text1 (PID 188743731) para codigo");
        assertTrue(tieneUnidad, "ExtendedAttribute Text2 (PID 188743732) para unidad");
        assertTrue(tienePeso, "ExtendedAttribute Number1 (PID 188743737) para peso");
        assertTrue(tienePrecio, "ExtendedAttribute Cost1 (PID 188743913) para precio unitario");

        // También se preserva metadata en <Notes> (resumen textual) y en campos
        // nativos del Task (Cost, ActualCost, FixedCost).
        org.w3c.dom.NodeList tareas = doc.getElementsByTagNameNS(NAMESPACE_2007, "Task");
        boolean hayNotes = false;
        boolean hayCost = false;
        for (int i = 0; i < tareas.getLength(); i++) {
            var t = tareas.item(i);
            var notes = buscarHijo(t, "Notes");
            if (notes != null && notes.getTextContent().contains("Codigo:")) {
                hayNotes = true;
            }
            var cost = buscarHijo(t, "Cost");
            if (cost != null && !cost.getTextContent().isBlank()) {
                hayCost = true;
            }
        }
        assertTrue(hayNotes, "<Notes> presente con resumen de metadata");
        assertTrue(hayCost, "<Cost> nativo poblado con precio total");
    }

    @Test
    void TC_P37_46_mspdi_nunca_es_mpp_y_emite_xml() {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        assertNotNull(bytes);
        assertTrue(bytes.length > 200, "MPDI XML debe tener contenido no trivial");
        String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.startsWith("<?xml"), "MPDI siempre es XML");
        assertFalse(xml.startsWith("MP"), "MPDI nunca empieza con la firma .mpp");
    }

    @Test
    void TC_P37_48_mspdi_save_version_project_2007_y_calendario_base() throws Exception {
        byte[] bytes = CronogramaMspdiRenderer.renderizar(proyeccionBase());
        Document doc = parsearXxeSafe(bytes);
        var project = doc.getElementsByTagNameNS(NAMESPACE_2007, "Project").item(0);
        var saveVersion = buscarHijo(project, "SaveVersion");
        assertNotNull(saveVersion, "SaveVersion obligatorio en Project header");
        assertEquals("12", saveVersion.getTextContent(), "SaveVersion=12 (Project 2007)");

        var calendarUid = buscarHijo(project, "CalendarUID");
        assertNotNull(calendarUid, "CalendarUID obligatorio");
        assertEquals("1", calendarUid.getTextContent(), "CalendarUID=1 (entero positivo, base)");

        var calendars = doc.getElementsByTagNameNS(NAMESPACE_2007, "Calendars");
        assertEquals(1, calendars.getLength(), "bloque Calendars obligatorio");
        var calendar = doc.getElementsByTagNameNS(NAMESPACE_2007, "Calendar").item(0);
        assertNotNull(calendar, "al menos un Calendar base");
        assertEquals("true", buscarHijo(calendar, "IsBaseCalendar").getTextContent(), "Calendar.IsBaseCalendar=true");
    }

    @Test
    void TC_P37_49_mspdi_uid_determinista_no_cero_y_unico() throws Exception {
        // La misma proyección debe producir los mismos UIDs (determinismo).
        CronogramaProjectionV1 p = proyeccionBase();
        byte[] bytes1 = CronogramaMspdiRenderer.renderizar(p);
        byte[] bytes2 = CronogramaMspdiRenderer.renderizar(p);
        Document d1 = parsearXxeSafe(bytes1);
        Document d2 = parsearXxeSafe(bytes2);
        var tasks1 = d1.getElementsByTagNameNS(NAMESPACE_2007, "Task");
        var tasks2 = d2.getElementsByTagNameNS(NAMESPACE_2007, "Task");
        java.util.Set<Integer> uids1 = new java.util.HashSet<>();
        java.util.Set<Integer> uids2 = new java.util.HashSet<>();
        for (int i = 0; i < tasks1.getLength(); i++) {
            uids1.add(Integer.parseInt(buscarHijo(tasks1.item(i), "UID").getTextContent()));
        }
        for (int i = 0; i < tasks2.getLength(); i++) {
            uids2.add(Integer.parseInt(buscarHijo(tasks2.item(i), "UID").getTextContent()));
        }
        assertEquals(uids1, uids2, "UID determinista entre renders");
        for (int u : uids1) {
            assertTrue(u > 0, "UID no-cero: " + u);
        }
        assertEquals(uids1.size(), tasks1.getLength(), "UIDs únicos por task");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lee la propiedad de sistema {@code mspdi.official.xsd} y la devuelve
     * como {@link Path} si apunta a un archivo regular. {@code null} si no
     * está definida.
     */
    private static Path rutaXsdOficial() {
        String ruta = System.getProperty("mspdi.official.xsd");
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        return Paths.get(ruta);
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    /** Valida bytes XML contra un XSD (stream). Lanza AssertionError si falla. */
    private static void validarContraXsd(byte[] xml, InputStream xsdStream) throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            Schema schema = sf.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } finally {
            xsdStream.close();
        }
    }

    private static Document parsearXxeSafe(byte[] bytes) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new ByteArrayInputStream(bytes)));
    }

    private static InputStream recursoPerfilXsd() {
        InputStream is = CronogramaMspdiFormatTest.class.getClassLoader().getResourceAsStream(XSD_LOCATION);
        if (is == null) {
            throw new IllegalStateException("XSD no encontrado en classpath: " + XSD_LOCATION);
        }
        return is;
    }

    private static org.w3c.dom.Node buscarHijo(org.w3c.dom.Node nodo, String localName) {
        var hijos = nodo.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            var h = hijos.item(i);
            if (h.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && localName.equals(h.getLocalName())) {
                return h;
            }
        }
        return null;
    }

    private static CronogramaProjectionV1 proyeccionBase() {
        var rows = List.of(
                new MspdiRow(
                        UUID.randomUUID().toString(),
                        "1.1",
                        "R1",
                        "Rubro uno",
                        "u",
                        "1.000000",
                        "1.000000",
                        "1.000000",
                        "50.0000",
                        List.of(1, 2),
                        List.of("50.0000", "50.0000")),
                new MspdiRow(
                        UUID.randomUUID().toString(),
                        "1.2",
                        "R2",
                        "Rubro dos",
                        "u",
                        "1.000000",
                        "2.000000",
                        "2.000000",
                        "50.0000",
                        List.of(3, 4),
                        List.of("50.0000", "50.0000")));
        var mspdi = new Mspdi(
                UUID.randomUUID().toString(), "PROYECTO-X", "Proyecto Demo", 2026, "SEMANA", 4, "2026-01-01", rows);
        return new CronogramaProjectionV1(CronogramaProjectionV1.VERSION, "mspdi", null, null, mspdi);
    }
}
