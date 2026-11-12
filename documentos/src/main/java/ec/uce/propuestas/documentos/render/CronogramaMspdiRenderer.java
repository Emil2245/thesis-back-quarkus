package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.MspdiRow;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;

/** Renders the captured MSPDI values using the Project 2007 profile. */
public final class CronogramaMspdiRenderer {
    private static final String NS = "http://schemas.microsoft.com/project/2007";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");

    private CronogramaMspdiRenderer() {}

    public static byte[] renderizar(CronogramaProjectionV1 projection) {
        if (projection == null || projection.mspdi() == null || !"mspdi".equals(projection.format())) {
            throw new IllegalArgumentException("Se requiere una proyección MSPDI");
        }
        var p = projection.mspdi();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            var doc = factory.newDocumentBuilder().newDocument();
            var root = doc.createElementNS(NS, "Project");
            root.setAttributeNS(
                    XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            root.setAttributeNS(
                    XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
                    "xsi:schemaLocation",
                    NS + " " + NS + "/mspdi_pj12.xsd");
            doc.appendChild(root);
            LocalDate start = LocalDate.parse(p.startDate());
            String now = DATE.format(OffsetDateTime.now(ZoneOffset.UTC));
            put(root, "SaveVersion", "12");
            put(root, "UID", Integer.toString(uid(p.projectUid(), 1)));
            put(root, "Name", p.projectName());
            put(root, "Title", p.projectName());
            put(root, "Subject", "Cronograma valorado de trabajos");
            put(root, "Company", "Sistema APU");
            put(root, "Manager", "Sistema APU");
            put(root, "Author", "Sistema APU");
            put(root, "CreationDate", now);
            put(root, "Revision", "1");
            put(root, "LastSaved", now);
            put(root, "ScheduleFromStart", "true");
            put(root, "StartDate", date(start));
            put(root, "FinishDate", date(start.plusDays(Math.max(1, p.periodCount() * 7L))));
            put(root, "CurrencyCode", "USD");
            put(root, "CurrencySymbolPosition", "1");
            put(root, "CalendarUID", "1");
            put(root, "DefaultStartTime", "08:00:00");
            put(root, "DefaultFinishTime", "17:00:00");
            put(root, "MinutesPerDay", "480");
            put(root, "MinutesPerWeek", "2400");
            put(root, "DaysPerMonth", "20");
            put(root, "DefaultTaskType", "1");
            put(root, "DefaultFixedCostAccrual", "3");
            put(root, "DefaultStandardRate", "0");
            put(root, "DefaultOvertimeRate", "0");
            put(root, "DurationFormat", "7");
            put(root, "WorkFormat", "3");
            put(root, "HonorConstraints", "true");
            put(root, "EarnedValueMethod", "0");
            put(root, "InsertedProjectsLikeSummary", "true");
            put(root, "MultipleCriticalPaths", "false");
            put(root, "NewTasksEffortDriven", "true");
            put(root, "NewTasksEstimated", "true");
            put(root, "SplitsInProgressTasks", "true");
            put(root, "SpreadActualCost", "true");
            put(root, "SpreadPercentComplete", "false");
            put(root, "WeekStartDay", "1");
            put(root, "AutoAddNewResourcesAndTasks", "true");
            put(root, "CurrentDate", now);
            var calendar = child(child(root, "Calendars"), "Calendar");
            put(calendar, "UID", "1");
            put(calendar, "Name", "Standard");
            put(calendar, "IsBaseCalendar", "true");
            put(calendar, "BaseCalendarUID", "-1");
            var days = child(calendar, "WeekDays");
            for (int day : new int[] {2, 3, 4, 5, 6, 1, 7}) {
                var weekday = child(days, "WeekDay");
                put(weekday, "DayType", Integer.toString(day));
                boolean working = day >= 2 && day <= 6;
                put(weekday, "DayWorking", Boolean.toString(working));
                if (working) {
                    var times = child(weekday, "WorkingTimes");
                    var morning = child(times, "WorkingTime");
                    put(morning, "FromTime", "08:00:00");
                    put(morning, "ToTime", "12:00:00");
                    var afternoon = child(times, "WorkingTime");
                    put(afternoon, "FromTime", "13:00:00");
                    put(afternoon, "ToTime", "17:00:00");
                }
            }
            var tasks = child(root, "Tasks");
            var used = new HashSet<Integer>();
            int id = 1;
            for (MspdiRow row : p.rows()) {
                var task = child(tasks, "Task");
                int taskUid = uid(row.rubroUid(), 1000);
                while (!used.add(taskUid)) taskUid = taskUid == Integer.MAX_VALUE ? 1000 : taskUid + 1;
                put(task, "UID", Integer.toString(taskUid));
                put(task, "ID", Integer.toString(id++));
                put(task, "Name", row.description());
                put(task, "Type", "1");
                put(task, "IsNull", "false");
                put(task, "CreateDate", now);
                put(task, "WBS", row.item());
                put(task, "OutlineNumber", row.item());
                put(task, "OutlineLevel", "1");
                put(task, "Priority", "500");
                put(task, "Start", date(start));
                put(task, "Finish", date(start.plusDays(1)));
                put(task, "Duration", "PT0H0M0S");
                put(task, "DurationFormat", "7");
                put(task, "Work", "PT0H0M0S");
                put(task, "FixedCost", decimal(row.unitPrice(), "0"));
                put(task, "FixedCostAccrual", "3");
                put(task, "PercentComplete", "100");
                put(task, "PercentWorkComplete", "100");
                put(task, "Cost", decimal(row.totalPrice(), "0"));
                put(task, "OvertimeCost", "0");
                put(task, "OvertimeWork", "PT0H0M0S");
                put(task, "ActualStart", date(start));
                put(task, "ActualFinish", date(start.plusDays(1)));
                put(task, "ActualDuration", "PT0H0M0S");
                put(task, "ActualCost", decimal(row.totalPrice(), "0"));
                put(task, "ActualOvertimeCost", "0");
                put(task, "ActualWork", "PT0H0M0S");
                put(task, "ActualOvertimeWork", "PT0H0M0S");
                put(task, "RegularWork", "PT0H0M0S");
                put(task, "ACWP", decimal(row.totalPrice(), "0"));
                put(task, "CV", "0");
                put(task, "ConstraintType", "0");
                put(task, "CalendarUID", "1");
                put(task, "IgnoreResourceCalendar", "false");
                String periods = periods(row);
                put(
                        task,
                        "Notes",
                        "Codigo: " + safe(row.code()) + "\nUnidad: " + safe(row.unit())
                                + "\nCantidad: " + decimal(row.quantity(), "") + "\nPeso (%): "
                                + decimal(row.weight(), "")
                                + "\nPeriodo unidad: " + safe(p.timeUnit()) + "\nNumero periodos: " + p.periodCount()
                                + "\nPeriodos activos: " + periods);
                put(task, "EarnedValueMethod", "0");
                attribute(task, "188743731", row.code());
                attribute(task, "188743732", row.unit());
                attribute(task, "188743733", row.item());
                attribute(task, "188743734", periods);
                attribute(task, "188743737", decimal(row.weight(), ""));
                attribute(task, "188743738", decimal(row.quantity(), ""));
                attribute(task, "188743913", decimal(row.unitPrice(), ""));
            }
            child(root, "Resources");
            var transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformers.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(output));
            return output.toByteArray();
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el MSPDI XML del cronograma", e);
        }
    }

    private static Element child(Element parent, String name) {
        var e = parent.getOwnerDocument().createElementNS(NS, name);
        parent.appendChild(e);
        return e;
    }

    private static void put(Element parent, String name, String value) {
        child(parent, name).setTextContent(safe(value));
    }

    private static void attribute(Element task, String field, String value) {
        var ea = child(task, "ExtendedAttribute");
        put(ea, "FieldID", field);
        put(ea, "Value", value);
    }

    private static String periods(MspdiRow row) {
        var result = new StringBuilder();
        for (int i = 0; i < row.activePeriods().size(); i++) {
            if (i > 0) result.append(';');
            result.append(row.activePeriods().get(i))
                    .append(':')
                    .append(decimal(row.percentages().get(i), "0.0000"));
        }
        return result.toString();
    }

    private static String decimal(String value, String fallback) {
        return value == null ? fallback : new BigDecimal(value).toPlainString();
    }

    private static String safe(String value) {
        if (value == null) return "";
        if (value.codePoints()
                .anyMatch(code -> !(code == 0x9
                        || code == 0xA
                        || code == 0xD
                        || (code >= 0x20 && code <= 0xD7FF)
                        || (code >= 0xE000 && code <= 0xFFFD)
                        || (code >= 0x10000 && code <= 0x10FFFF)))) {
            throw new IllegalArgumentException("Invalid XML 1.0 character in cronograma projection");
        }
        return value;
    }

    private static String date(LocalDate date) {
        return DATE.format(date.atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    private static int uid(String value, int offset) {
        if (value == null) return offset;
        UUID uuid = UUID.fromString(value);
        long mixed = Math.abs(uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits());
        int candidate = (int) (mixed % 2_000_000_000L);
        return (candidate <= 0 ? 1 : candidate) + offset;
    }
}
