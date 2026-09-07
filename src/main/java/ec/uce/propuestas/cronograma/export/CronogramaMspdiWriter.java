package ec.uce.propuestas.cronograma.export;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Plan 031 (P-37) — writer MSPDI XML (Lane C). Produce un archivo
 * {@code application/xml} genuinamente compatible con Microsoft Project 2007
 * (namespace {@code http://schemas.microsoft.com/project/2007}, XSD canónico
 * <a href="https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd">
 * mspdi_pj12.xsd</a>, revisión 2007-11-28, 239895 bytes, SHA-256
 * {@code a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4}).
 *
 * <p>Decisiones locked (Plan 031 §MSPDI + canon 026, audit closure §MSPDI):
 * <ul>
 *   <li>{@code SaveVersion=12} — Project 2007; cualquier otro valor hace que MS
 *       Project rechace o malinterprete el archivo.</li>
 *   <li>Un {@code <Project>} + un {@code <Task>} por rubro; nunca se emite
 *       {@code .mpp}.</li>
 *   <li>{@code CalendarUID}, {@code UID}, {@code ID} son enteros positivos
 *       no-cero y únicos por documento (el XSD exige
 *       {@code xsd:integer}/{@code xsd:int}).</li>
 *   <li>Se emite UN calendario base ({@code <Calendar>}), referencia del
 *       {@code CalendarUID} del Project y de los Task.</li>
 *   <li>Elementos Task en el ORDEN EXACTO del schema canónico (UID → ID →
 *       Name → ... → ExtendedAttribute al final). Validar contra el XSD
 *       oficial exige ese orden.</li>
 *   <li>Fechas en formato {@code xsd:dateTime} ISO-8601 con zona UTC
 *       (formato que {@code xsd:dateTime} y MS Project aceptan).</li>
 *   <li>Duraciones en formato {@code xsd:duration} ISO-8601
 *       ({@code PT0H0M0S} — sin duración por periodo, esquema aceptado).</li>
 *   <li>Metadata custom del rubro (item/code/unidad/cantidad/precio/peso/mapa)
 *       se preserva en campos nativos del Task cuando existen (Cost, Notes,
 *       WBS, OutlineNumber, ActualCost, FixedCost) y, para el resto, en
 *       {@code <ExtendedAttribute>} con {@code FieldID} apuntando a PIDs
 *       estándar de Project 2007 (Text1, Text2, Number1, Cost1, etc. — los
 *       valores que MS Project sí entiende).</li>
 *   <li>Sin dependencias/CPM/lag — no se emite {@code <PredecessorLink>} ni
 *       {@code <Assignment>}.</li>
 *   <li>No se descargan recursos remotos; no se incluyen entidades externas.</li>
 * </ul>
 *
 * <p>Validación XSD: el writer se valida contra el perfil offline
 * {@code mspdi/mspdi-pj12-profile.xsd} por defecto (subset restrictivo
 * pinned). Si se define la propiedad de sistema
 * {@code -Dmspdi.official.xsd=/ruta/absoluta/a/mspdi_pj12.xsd}, el test
 * específico {@code CronogramaMspdiWriterTest#TC_P37_47_mspdi_valida_xsd_oficial}
 * (y cualquier futuro helper) consume ese path para validar contra el XSD
 * oficial. SHA-256 pinned en el canon
 * ({@code a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4}).</p>
 */
public final class CronogramaMspdiWriter {

    public static final String NAMESPACE = "http://schemas.microsoft.com/project/2007";
    public static final String XSD_URL = "http://schemas.microsoft.com/project/2007/mspdi_pj12.xsd";
    public static final int SAVE_VERSION_PROJECT_2007 = 12;

    /** Calendar UID determinista para el calendario base (no-cero, único). */
    public static final int BASE_CALENDAR_UID = 1;
    /** Offset de UID para tasks (no-cero, único, ≥ MAX_BASE_CALENDAR). */
    public static final int TASK_UID_BASE = 1000;

    /** Property de sistema para activar la validación contra el XSD oficial. */
    public static final String PROP_OFICIAL_XSD = "mspdi.official.xsd";

    private CronogramaMspdiWriter() {}

    public record RubroMspdi(
            UUID rubroId,
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal precioTotal,
            BigDecimal pesoPonderado,
            List<Integer> periodosActivos,
            List<BigDecimal> porcentajesPorPeriodo) {}

    public record ProyeccionMspdi(
            UUID proyectoUid,
            String proyectoCodigo,
            String proyectoNombre,
            int anio,
            String unidadTiempo,
            int numeroPeriodos,
            LocalDate fechaInicio,
            List<RubroMspdi> rubros) {}

    /** Renderiza el MSPDI XML conforme al XSD oficial Project 2007. */
    public static byte[] renderizar(ProyeccionMspdi p) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // Seguridad: entidades externas deshabilitadas.
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElementNS(NAMESPACE, "Project");
            root.setAttributeNS(
                    "http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            root.setAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation", NAMESPACE + " " + XSD_URL);
            doc.appendChild(root);

            // ── Project header (orden canónico del XSD oficial) ────────────
            // 1. SaveVersion (obligatorio, xsd:integer) = 12 (Project 2007).
            agregarEntero(root, "SaveVersion", SAVE_VERSION_PROJECT_2007);
            // 2. UID (string maxLength=16). Usamos el hash int determinista.
            agregarTexto(root, "UID", Integer.toString(uidDeterminista(p.proyectoUid(), 1)));
            // 3. Name (string maxLength=255)
            agregarTexto(root, "Name", seguro(p.proyectoNombre()));
            // 4. Title (string maxLength=512)
            agregarTexto(root, "Title", seguro(p.proyectoNombre()));
            // 5. Subject (string maxLength=512)
            agregarTexto(root, "Subject", "Cronograma valorado de trabajos");
            // 6. Company (string maxLength=512)
            agregarTexto(root, "Company", "Sistema APU");
            // 7. Manager (string maxLength=512)
            agregarTexto(root, "Manager", "Sistema APU");
            // 8. Author (string maxLength=512)
            agregarTexto(root, "Author", "Sistema APU");
            // 9. CreationDate (xsd:dateTime)
            agregarTexto(root, "CreationDate", formatFechaHora(OffsetDateTime.now(ZoneOffset.UTC)));
            // 10. Revision (xsd:integer, optional)
            agregarEntero(root, "Revision", 1);
            // 11. LastSaved (xsd:dateTime, optional)
            agregarTexto(root, "LastSaved", formatFechaHora(OffsetDateTime.now(ZoneOffset.UTC)));
            // 12. ScheduleFromStart (xsd:boolean, default=true, optional)
            agregarBoolean(root, "ScheduleFromStart", true);
            // 13. StartDate (xsd:dateTime, optional pero requerido cuando ScheduleFromStart=true)
            agregarTexto(
                    root,
                    "StartDate",
                    formatFechaHora(p.fechaInicio().atStartOfDay().atOffset(ZoneOffset.UTC)));
            // 14. FinishDate (xsd:dateTime, optional)
            agregarTexto(
                    root,
                    "FinishDate",
                    formatFechaHora(p.fechaInicio()
                            .plusDays(Math.max(1, p.numeroPeriodos() * 7L))
                            .atStartOfDay()
                            .atOffset(ZoneOffset.UTC)));
            // 15. CurrencyCode (string maxLength=3) — nuevo en Project 2007.
            agregarTexto(root, "CurrencyCode", "USD");
            // 16. CurrencySymbolPosition (xsd:integer 0..3, optional)
            agregarEntero(root, "CurrencySymbolPosition", 1);
            // 17. CalendarUID (xsd:integer, optional) — entero positivo, no GUID.
            //     Posición exacta del XSD oficial: DESPUÉS de CurrencySymbolPosition
            //     y ANTES de DefaultStartTime.
            agregarEntero(root, "CalendarUID", BASE_CALENDAR_UID);
            // 18. DefaultStartTime (xsd:time, optional)
            agregarTexto(root, "DefaultStartTime", "08:00:00");
            // 19. DefaultFinishTime (xsd:time, optional)
            agregarTexto(root, "DefaultFinishTime", "17:00:00");
            // 20. MinutesPerDay (xsd:integer)
            agregarEntero(root, "MinutesPerDay", 480);
            // 21. MinutesPerWeek (xsd:integer)
            agregarEntero(root, "MinutesPerWeek", 2400);
            // 22. DaysPerMonth (xsd:integer)
            agregarEntero(root, "DaysPerMonth", 20);
            // 23. DefaultTaskType (xsd:integer 0..2, default=1)
            agregarEntero(root, "DefaultTaskType", 1);
            // 24. DefaultFixedCostAccrual (xsd:integer 1..3, optional)
            agregarEntero(root, "DefaultFixedCostAccrual", 3);
            // 25. DefaultStandardRate (xsd:float, optional)
            agregarTexto(root, "DefaultStandardRate", "0");
            // 26. DefaultOvertimeRate (xsd:float, optional)
            agregarTexto(root, "DefaultOvertimeRate", "0");
            // 27. DurationFormat (xsd:integer, enum) — 7 = days
            agregarEntero(root, "DurationFormat", 7);
            // 28. WorkFormat (xsd:integer 1..5, optional) — 3 = days
            agregarEntero(root, "WorkFormat", 3);
            // 29. HonorConstraints (xsd:boolean, default=true)
            agregarBoolean(root, "HonorConstraints", true);
            // 30. EarnedValueMethod (xsd:integer 0..1, optional)
            agregarEntero(root, "EarnedValueMethod", 0);
            // 31. InsertedProjectsLikeSummary (xsd:boolean, default=true)
            agregarBoolean(root, "InsertedProjectsLikeSummary", true);
            // 32. MultipleCriticalPaths (xsd:boolean, default=false)
            agregarBoolean(root, "MultipleCriticalPaths", false);
            // 33. NewTasksEffortDriven (xsd:boolean, default=true)
            agregarBoolean(root, "NewTasksEffortDriven", true);
            // 34. NewTasksEstimated (xsd:boolean, default=true)
            agregarBoolean(root, "NewTasksEstimated", true);
            // 35. SplitsInProgressTasks (xsd:boolean, default=true)
            agregarBoolean(root, "SplitsInProgressTasks", true);
            // 36. SpreadActualCost (xsd:boolean, default=true)
            agregarBoolean(root, "SpreadActualCost", true);
            // 37. SpreadPercentComplete (xsd:boolean, default=false)
            agregarBoolean(root, "SpreadPercentComplete", false);
            // 38. WeekStartDay (xsd:integer 0..6, optional) — 1 = Monday
            agregarEntero(root, "WeekStartDay", 1);
            // 39. AutoAddNewResourcesAndTasks (xsd:boolean, default=true)
            agregarBoolean(root, "AutoAddNewResourcesAndTasks", true);
            // 40. CurrentDate (xsd:dateTime, optional)
            agregarTexto(root, "CurrentDate", formatFechaHora(OffsetDateTime.now(ZoneOffset.UTC)));

            // ── Calendars (bloque obligatorio para que el Project sea válido) ──
            Element calendars = doc.createElementNS(NAMESPACE, "Calendars");
            root.appendChild(calendars);
            Element calendar = doc.createElementNS(NAMESPACE, "Calendar");
            calendars.appendChild(calendar);
            agregarEntero(calendar, "UID", BASE_CALENDAR_UID);
            agregarTexto(calendar, "Name", "Standard");
            agregarBoolean(calendar, "IsBaseCalendar", true);
            agregarEntero(calendar, "BaseCalendarUID", -1);
            Element weekDays = doc.createElementNS(NAMESPACE, "WeekDays");
            calendar.appendChild(weekDays);
            // Lunes a Viernes (DayType 2..6) como días de trabajo.
            for (int day = 2; day <= 6; day++) {
                Element wd = doc.createElementNS(NAMESPACE, "WeekDay");
                weekDays.appendChild(wd);
                agregarEntero(wd, "DayType", day);
                agregarBoolean(wd, "DayWorking", true);
                Element wt = doc.createElementNS(NAMESPACE, "WorkingTimes");
                wd.appendChild(wt);
                Element wt1 = doc.createElementNS(NAMESPACE, "WorkingTime");
                wt.appendChild(wt1);
                agregarTexto(wt1, "FromTime", "08:00:00");
                agregarTexto(wt1, "ToTime", "12:00:00");
                Element wt2 = doc.createElementNS(NAMESPACE, "WorkingTime");
                wt.appendChild(wt2);
                agregarTexto(wt2, "FromTime", "13:00:00");
                agregarTexto(wt2, "ToTime", "17:00:00");
            }
            // Sábado y Domingo (1 y 7) como no laborables.
            for (int day : new int[] {1, 7}) {
                Element wd = doc.createElementNS(NAMESPACE, "WeekDay");
                weekDays.appendChild(wd);
                agregarEntero(wd, "DayType", day);
                agregarBoolean(wd, "DayWorking", false);
            }

            // ── Tasks ──────────────────────────────────────────────────────
            Element tasks = doc.createElementNS(NAMESPACE, "Tasks");
            root.appendChild(tasks);

            int taskId = 1;
            for (RubroMspdi r : p.rubros()) {
                Element t = doc.createElementNS(NAMESPACE, "Task");
                tasks.appendChild(t);

                // ORDEN EXACTO DEL XSD OFICIAL (TaskType sequence):
                // UID (xsd:integer, obligatorio)
                int taskUid = uidDeterminista(r.rubroId(), TASK_UID_BASE);
                agregarEntero(t, "UID", taskUid);
                // ID (xsd:integer, optional)
                agregarEntero(t, "ID", taskId++);
                // Name (string maxLength=512, optional)
                agregarTexto(t, "Name", seguro(r.descripcion()));
                // Type (xsd:integer 0..2, optional)
                agregarEntero(t, "Type", 1);
                // IsNull (xsd:boolean, optional)
                agregarBoolean(t, "IsNull", false);
                // CreateDate (xsd:dateTime, optional)
                agregarTexto(t, "CreateDate", formatFechaHora(OffsetDateTime.now(ZoneOffset.UTC)));
                // WBS (xsd:string, optional)
                agregarTexto(t, "WBS", seguro(r.item()));
                // OutlineNumber (string maxLength=512, optional)
                agregarTexto(t, "OutlineNumber", seguro(r.item()));
                // OutlineLevel (xsd:integer, optional)
                agregarEntero(t, "OutlineLevel", 1);
                // Priority (xsd:integer, optional) — 500 = Medium
                agregarEntero(t, "Priority", 500);
                // Start (xsd:dateTime, optional)
                agregarTexto(
                        t,
                        "Start",
                        formatFechaHora(p.fechaInicio().atStartOfDay().atOffset(ZoneOffset.UTC)));
                // Finish (xsd:dateTime, optional)
                agregarTexto(
                        t,
                        "Finish",
                        formatFechaHora(
                                p.fechaInicio().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
                // Duration (xsd:duration ISO-8601, optional)
                agregarTexto(t, "Duration", "PT0H0M0S");
                // DurationFormat (xsd:integer 3..12, optional) — 7 = days
                agregarEntero(t, "DurationFormat", 7);
                // Work (xsd:duration, optional)
                agregarTexto(t, "Work", "PT0H0M0S");
                // Stop, Resume, ResumeValid, EffortDriven, Recurring, OverAllocated,
                // Estimated, Milestone, Summary, Critical, IsSubproject,
                // IsSubprojectReadOnly, SubprojectName, ExternalTask,
                // ExternalTaskProject — todos optional; no se emiten (default OK).
                // EarlyStart, EarlyFinish, LateStart, LateFinish, StartVariance,
                // FinishVariance, WorkVariance, FreeSlack, TotalSlack — optional.
                // FixedCost (xsd:float, optional) — precio unitario
                agregarTexto(
                        t,
                        "FixedCost",
                        r.precioUnitario() == null ? "0" : r.precioUnitario().toPlainString());
                // FixedCostAccrual (xsd:integer 1..3, optional)
                agregarEntero(t, "FixedCostAccrual", 3);
                // PercentComplete (xsd:integer 0..100, optional)
                agregarEntero(t, "PercentComplete", 100);
                // PercentWorkComplete (xsd:integer 0..100, optional)
                agregarEntero(t, "PercentWorkComplete", 100);
                // Cost (xsd:decimal, optional) — precio total del rubro
                agregarTexto(
                        t,
                        "Cost",
                        r.precioTotal() == null ? "0" : r.precioTotal().toPlainString());
                // OvertimeCost, OvertimeWork — optional; default 0.
                agregarTexto(t, "OvertimeCost", "0");
                agregarTexto(t, "OvertimeWork", "PT0H0M0S");
                // ActualStart (xsd:dateTime, optional)
                agregarTexto(
                        t,
                        "ActualStart",
                        formatFechaHora(p.fechaInicio().atStartOfDay().atOffset(ZoneOffset.UTC)));
                // ActualFinish (xsd:dateTime, optional)
                agregarTexto(
                        t,
                        "ActualFinish",
                        formatFechaHora(
                                p.fechaInicio().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
                // ActualDuration (xsd:duration, optional)
                agregarTexto(t, "ActualDuration", "PT0H0M0S");
                // ActualCost (xsd:decimal, optional)
                agregarTexto(
                        t,
                        "ActualCost",
                        r.precioTotal() == null ? "0" : r.precioTotal().toPlainString());
                // ActualOvertimeCost, ActualWork, ActualOvertimeWork, RegularWork,
                // RemainingDuration, RemainingCost, RemainingWork,
                // RemainingOvertimeCost, RemainingOvertimeWork — optional; default 0.
                agregarTexto(t, "ActualOvertimeCost", "0");
                agregarTexto(t, "ActualWork", "PT0H0M0S");
                agregarTexto(t, "ActualOvertimeWork", "PT0H0M0S");
                agregarTexto(t, "RegularWork", "PT0H0M0S");
                // ACWP (xsd:float, optional) — Actual Cost of Work Performed
                agregarTexto(
                        t,
                        "ACWP",
                        r.precioTotal() == null ? "0" : r.precioTotal().toPlainString());
                // CV (xsd:float, optional) — Cost Variance
                agregarTexto(t, "CV", "0");
                // ConstraintType (xsd:integer 0..7, optional)
                agregarEntero(t, "ConstraintType", 0);
                // CalendarUID (xsd:integer, optional) — referencia al calendario base
                agregarEntero(t, "CalendarUID", BASE_CALENDAR_UID);
                // ConstraintDate, Deadline, LevelAssignments, LevelingCanSplit,
                // LevelingDelay, LevelingDelayFormat — optional; no se emiten.
                // PreLeveledStart, PreLeveledFinish — optional.
                // Hyperlink, HyperlinkAddress, HyperlinkSubAddress — optional.
                // IgnoreResourceCalendar (xsd:boolean, optional)
                agregarBoolean(t, "IgnoreResourceCalendar", false);
                // Notes (xsd:string, optional) — empaqueta metadata legacy en texto
                agregarTexto(
                        t,
                        "Notes",
                        "Codigo: " + seguro(r.codigo())
                                + "\nUnidad: " + seguro(r.unidad())
                                + "\nCantidad: "
                                + (r.cantidad() == null ? "" : r.cantidad().toPlainString())
                                + "\nPeso (%): "
                                + (r.pesoPonderado() == null
                                        ? ""
                                        : r.pesoPonderado().toPlainString())
                                + "\nPeriodo unidad: " + seguro(p.unidadTiempo())
                                + "\nNumero periodos: " + p.numeroPeriodos()
                                + "\nPeriodos activos: " + mapPeriodos(r.periodosActivos(), r.porcentajesPorPeriodo()));
                // HideBar, Rollup, BCWS, BCWP, PhysicalPercentComplete — optional.
                // EarnedValueMethod (xsd:integer 0..1, optional)
                agregarEntero(t, "EarnedValueMethod", 0);
                // PredecessorLink — SKIP (no emitimos dependencias/CPM/lag por canon).
                // ExtendedAttribute (ExtendedAttributeType, optional unbounded)
                //   Se preserva la metadata legacy del rubro con PIDs estándar
                //   de Project 2007 (Text1, Text2, Number1, Cost1). El XSD
                //   oficial exige que ExtendedAttribute vaya AL FINAL del Task.
                agregarEa(t, "188743731", "Text1", r.codigo()); // Text1
                agregarEa(t, "188743732", "Text2", r.unidad()); // Text2
                agregarEa(t, "188743733", "Text3", r.item()); // Text3
                agregarEa(
                        t, "188743734", "Text4", mapPeriodos(r.periodosActivos(), r.porcentajesPorPeriodo())); // Text4
                agregarEa(
                        t,
                        "188743737",
                        "Number1",
                        r.pesoPonderado() == null ? "" : r.pesoPonderado().toPlainString()); // Number1 (peso)
                agregarEa(
                        t,
                        "188743738",
                        "Number2",
                        r.cantidad() == null ? "" : r.cantidad().toPlainString()); // Number2 (cantidad)
                agregarEa(
                        t,
                        "188743913",
                        "Cost1",
                        r.precioUnitario() == null ? "" : r.precioUnitario().toPlainString()); // Cost1
            }

            // Resources (vacío: no emitimos recursos humanos)
            Element resources = doc.createElementNS(NAMESPACE, "Resources");
            root.appendChild(resources);

            // Serializar a bytes.
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            t.setOutputProperty(OutputKeys.STANDALONE, "no");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el MSPDI XML del cronograma", e);
        }
    }

    /**
     * Genera un UID determinista no-cero y positivo a partir de un UUID: toma
     * los 63 bits menos significativos del leastSignificantBits y los reduce
     * módulo {@code 2_000_000_000} (dejamos 2·10⁹ como techo seguro, lejos del
     * límite {@code xsd:integer} = 2^31-1). Sumamos {@code offset} para evitar
     * colisión con IDs reservados (1 = Project UID, 1..999 = Calendar).
     */
    static int uidDeterminista(UUID uuid, int offset) {
        if (uuid == null) {
            return offset;
        }
        long lsb = uuid.getLeastSignificantBits();
        long msb = uuid.getMostSignificantBits();
        long mixed = Math.abs(lsb ^ msb);
        long mod = mixed % 2_000_000_000L;
        int candidate = (int) mod;
        if (candidate <= 0) {
            candidate = 1;
        }
        int result = candidate + offset;
        // Saturar para no rebasar Integer.MAX_VALUE.
        if (result <= 0) {
            result = Integer.MAX_VALUE;
        }
        return result;
    }

    private static String mapPeriodos(List<Integer> activos, List<BigDecimal> porcentajes) {
        if (activos == null || activos.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < activos.size(); i++) {
            int periodo = activos.get(i);
            BigDecimal pct = i < porcentajes.size() ? porcentajes.get(i) : BigDecimal.ZERO;
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(periodo).append(':').append(pct == null ? "0.0000" : pct.toPlainString());
        }
        return sb.toString();
    }

    private static void agregarTexto(Element padre, String localName, String texto) {
        Element e = padre.getOwnerDocument().createElementNS(NAMESPACE, localName);
        e.setTextContent(texto == null ? "" : texto);
        padre.appendChild(e);
    }

    private static void agregarEntero(Element padre, String localName, int valor) {
        Element e = padre.getOwnerDocument().createElementNS(NAMESPACE, localName);
        e.setTextContent(Integer.toString(valor));
        padre.appendChild(e);
    }

    private static void agregarBoolean(Element padre, String localName, boolean valor) {
        Element e = padre.getOwnerDocument().createElementNS(NAMESPACE, localName);
        e.setTextContent(valor ? "true" : "false");
        padre.appendChild(e);
    }

    /**
     * Emite un {@code <ExtendedAttribute>} con FieldID (PID entero de Project
     * 2007) + alias opcional. La estructura interna es la del XSD oficial:
     * {@code <ExtendedAttribute> <FieldID>...</FieldID> <Value>...</Value> </ExtendedAttribute>}.
     */
    private static void agregarEa(Element task, String fieldId, String alias, String value) {
        Element ea = task.getOwnerDocument().createElementNS(NAMESPACE, "ExtendedAttribute");
        Element fid = task.getOwnerDocument().createElementNS(NAMESPACE, "FieldID");
        fid.setTextContent(fieldId);
        ea.appendChild(fid);
        Element val = task.getOwnerDocument().createElementNS(NAMESPACE, "Value");
        val.setTextContent(value == null ? "" : value);
        ea.appendChild(val);
        task.appendChild(ea);
    }

    private static String formatFechaHora(OffsetDateTime fecha) {
        // xsd:dateTime admite dos formas: con o sin zona. Usamos formato
        // ISO-8601 estricto 'YYYY-MM-DDTHH:MM:SSZ' (segundos siempre presentes)
        // para máxima portabilidad con MS Project. El toString() de
        // OffsetDateTime puede emitir nanosegundos (no aceptados por el
        // patrón del XSD).
        return String.format(
                "%04d-%02d-%02dT%02d:%02d:%02dZ",
                fecha.getYear(),
                fecha.getMonthValue(),
                fecha.getDayOfMonth(),
                fecha.getHour(),
                fecha.getMinute(),
                fecha.getSecond());
    }

    private static String seguro(String s) {
        return s == null ? "" : s;
    }
}
