package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportRevisionTest {
    @Test
    void specificationRevisionIgnoresRenderDateButChangesWithDomainContent() {
        var json = new ObjectMapper();
        UUID budget = UUID.fromString("01900000-0000-7000-8000-000000000011");
        var rows = List.of(new SpecificationProjectionV1.Specification("A", "Apu", "u", "Details"));
        var dayOne = new SpecificationProjectionV1(
                1, budget, "Project", (short) 2026, "Technical", "Notes", "2026-01-01", rows);
        var dayTwo = new SpecificationProjectionV1(
                1, budget, "Project", (short) 2026, "Technical", "Notes", "2026-01-02", rows);
        var changed = new SpecificationProjectionV1(
                1,
                budget,
                "Project",
                (short) 2026,
                "Technical",
                "Notes",
                "2026-01-02",
                List.of(new SpecificationProjectionV1.Specification("A", "Apu", "u", "Changed")));
        assertEquals(ExportRevision.hash(json, dayOne), ExportRevision.hash(json, dayTwo));
        assertNotEquals(ExportRevision.hash(json, dayOne), ExportRevision.hash(json, changed));
    }
}
