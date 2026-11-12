package ec.uce.propuestas.documentos;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import ec.uce.propuestas.documentos.storage.S3PrivateObjectStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
@TestProfile(ServiceJwtTestProfile.class)
class InternalExportResourceTest {
    private static final String PATH = "/internal/v1/export";
    private static final String OPERATION_ID = "01900000-0000-7000-8000-000000000001";
    private static final String KEY = "exports/" + OPERATION_ID;
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String PAYLOAD = """
            {"schemaVersion":1,"key":"exports/01900000-0000-7000-8000-000000000001",
             "operationId":"01900000-0000-7000-8000-000000000001",
             "fingerprint":"%s","filename":"budget.pdf",
             "projection":{"schemaVersion":1,"documento":"presupuesto","formato":"pdf","opciones":{},
              "snapshot":{"presupuestoId":"01900000-0000-7000-8000-000000000001","version":1,
              "vigente":true,"total":0,"proyecto":{"id":"01900000-0000-7000-8000-000000000002",
              "nombre":"Example","codigo":"E","anio":2025},"parametros":{"moneda":"USD"},
              "display":{"precision":2,"precisionPorcentaje":2},"firmantes":[],"capitulos":[],"apus":[]}}}
            """.formatted(FINGERPRINT);

    private static final String CRONOGRAMA = """
            {"schemaVersion":1,"format":"xlsx","sheet":{"projectCode":"P1","projectName":"Project",
             "year":2025,"timeUnit":"SEMANA","periodCount":1,"startDate":"2025-01-01",
             "institution":"Institution","subdirection":"Division","moneyPrecision":2,"percentagePrecision":4,
             "rows":[{"item":"1.1","code":"R1","description":"Captured","unit":"u",
              "quantity":"1.000000","unitPrice":"12.340000","totalPrice":"12.340000","weight":"100.0000",
              "percentByPeriod":["100.0000"],"amountByPeriod":[{"period":1,"value":"12.340000"}]}],
             "chapters":[],"totals":{"partialPercentage":["100.0000"],"accumulatedPercentage":["100.0000"],
             "partialAmount":["12.340000"],"accumulatedAmount":["12.340000"]}}}
            """;
    private static final String CRONOGRAMA_UPLOAD = """
            {"schemaVersion":1,"key":"%s","operationId":"%s","fingerprint":"%s",
             "filename":"schedule.xlsx","cronograma":%s}
            """.formatted(KEY, OPERATION_ID, FINGERPRINT, CRONOGRAMA);

    @InjectMock
    S3PrivateObjectStore storage;

    @Test
    void uploadsCapturedCronogramaAndReconcilesConfirmedPrivateMetadata() {
        var confirmed = new S3PrivateObjectStore.StoredObject(
                KEY,
                123,
                "checksum",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "schedule.xlsx");
        when(storage.store(eq(KEY), any(byte[].class), eq(confirmed.mimeType()), eq("schedule.xlsx"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        when(storage.confirmExisting(eq(KEY), eq(confirmed.mimeType()), eq("schedule.xlsx"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(CRONOGRAMA_UPLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(200)
                .body("key", equalTo(KEY))
                .body("checksumSha256", equalTo("checksum"));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(eq(KEY), bytes.capture(), eq(confirmed.mimeType()), eq("schedule.xlsx"), eq(FINGERPRINT));
        assertEquals('P', bytes.getValue()[0]);
        assertEquals('K', bytes.getValue()[1]);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(CRONOGRAMA_UPLOAD)
                .when()
                .post(PATH + "/reconcile")
                .then()
                .statusCode(200)
                .body("key", equalTo(KEY));
        verify(storage).confirmExisting(eq(KEY), eq(confirmed.mimeType()), eq("schedule.xlsx"), eq(FINGERPRINT));
    }

    @Test
    void rejectsMultipleProjectionVariantsBeforeStorage() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD.replace("\"projection\":", "\"cronograma\":" + CRONOGRAMA + ",\"projection\":"))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }

    @Test
    void exportBoundaryReturnsSharedContractRatherThanStorageImplementation() throws Exception {
        assertEquals(
                StoredObjectV1.class,
                InternalExportResource.class
                        .getMethod("upload", ExportUploadV1.class)
                        .getReturnType());
        assertEquals(
                StoredObjectV1.class,
                InternalExportResource.class
                        .getMethod("reconcile", ExportUploadV1.class)
                        .getReturnType());
    }

    @Test
    void uploadsRenderedPdfAndReturnsOnlyConfirmedMetadata() {
        var confirmed = new S3PrivateObjectStore.StoredObject(KEY, 123, "checksum", "application/pdf", "budget.pdf");
        when(storage.store(eq(KEY), any(byte[].class), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("key", equalTo(KEY))
                .body("checksumSha256", equalTo("checksum"))
                .body("filename", equalTo("budget.pdf"));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(eq(KEY), bytes.capture(), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT));
        assertTrue(bytes.getValue().length > 100);
        assertEquals("%PDF", new String(bytes.getValue(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void reconcilesByCheckingBoundS3MetadataWithoutDependingOnRerenderedBytes() {
        var confirmed = new S3PrivateObjectStore.StoredObject(KEY, 123, "checksum", "application/pdf", "budget.pdf");
        when(storage.confirmExisting(eq(KEY), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH + "/reconcile")
                .then()
                .statusCode(200)
                .body("key", equalTo(KEY));
        verify(storage).confirmExisting(eq(KEY), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT));
    }

    @Test
    void rejectsAnonymousUpload() {
        given().contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUserRoleBeforeRenderingOrUploading() {
        String token = ServiceJwtTestProfile.token(
                "core",
                "documentos",
                Set.of("USUARIO"),
                Instant.now().plusSeconds(30).getEpochSecond(),
                false);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(403);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsMalformedUploadBeforeRenderingOrUploading() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD.replace("\"schemaVersion\":1,\"key\"", "\"schemaVersion\":2,\"key\""))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnboundOrMismatchedServiceToken() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken("01900000-0000-7000-8000-000000000009"))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsNonRfcUuidVariantEvenWithMatchingServiceClaimAndKey() {
        String invalidVariant = "01900000-0000-7000-7000-000000000001";
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(invalidVariant))
                .contentType("application/json")
                .body(CRONOGRAMA_UPLOAD.replace(OPERATION_ID, invalidVariant))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsInvalidKeyAndFingerprintWithoutUploading() {
        String token = ServiceJwtTestProfile.exportToken(OPERATION_ID);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD.replace(KEY, "exports/------------------------------------"))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD.replace(FINGERPRINT, "x"))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }

    // Ported in stage 05 from the retired /internal/v1/render and /render/specifications tests:
    // the same service-identity, contract and rendering guarantees now apply to the export upload.

    private static final String APU_RENDER = """
            "apuRender":[{"id":"01900000-0000-7000-8000-000000000001",
            "codigo":"A-1","etiquetaCodigo":"A-1","descripcion":"APU de prueba","unidad":"m³",
            "bloques":[],"pie":{"subtotales":[],"porcentajeIndirecto":0,"costoDirecto":1,
            "costoIndirecto":0,"costoTotal":1,"valorOfertado":1}}]""";
    private static final String SPECIFICATION = """
            {"schemaVersion":1,"presupuestoId":"01900000-0000-7000-8000-000000000001",
            "nombreProyecto":"Proyecto","anio":2026,"titulo1":"Título","titulo2":"Subtítulo",
            "fechaExportacion":"2026-11-07","especificaciones":[{"codigo":"A-1",
            "descripcion":"Losa","unidad":"u","especificacionTecnica":"Texto técnico"}]}
            """;

    private static String apuUpload(String formato) {
        String opciones = "pdf".equals(formato) ? "{}" : "{\"layout\":\"pestanas\"}";
        return PAYLOAD.replace("\"budget.pdf\"", "\"apus." + formato + "\"")
                .replace(
                        "\"documento\":\"presupuesto\",\"formato\":\"pdf\",\"opciones\":{}",
                        "\"documento\":\"apus\",\"formato\":\"" + formato + "\",\"opciones\":" + opciones)
                .replace("\"apus\":[]}}}", "\"apus\":[]," + APU_RENDER + "}}}");
    }

    private static String specificationUpload(String specification) {
        return """
                {"schemaVersion":1,"key":"%s","operationId":"%s","fingerprint":"%s",
                 "filename":"especificaciones.docx","specification":%s}
                """.formatted(KEY, OPERATION_ID, FINGERPRINT, specification);
    }

    private byte[] uploadedBytes(String body, String mimeType, String filename) {
        when(storage.store(eq(KEY), any(byte[].class), eq(mimeType), eq(filename), eq(FINGERPRINT)))
                .thenReturn(new S3PrivateObjectStore.StoredObject(KEY, 1, "checksum", mimeType, filename));
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(body)
                .when()
                .post(PATH)
                .then()
                .statusCode(200);
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(eq(KEY), bytes.capture(), eq(mimeType), eq(filename), eq(FINGERPRINT));
        return bytes.getValue();
    }

    private static void assertMagic(byte[] bytes, String magic) {
        assertEquals(magic, new String(bytes, 0, magic.length(), StandardCharsets.US_ASCII));
    }

    private void rejected(String token, String body, int status) {
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(body)
                .when()
                .post(PATH)
                .then()
                .statusCode(status);
        verifyNoInteractions(storage);
    }

    @Test
    void uploadsRenderedApuWorkbook() {
        assertMagic(
                uploadedBytes(
                        apuUpload("xlsx"),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "apus.xlsx"),
                "PK");
    }

    @Test
    void uploadsRenderedApuPdf() {
        assertMagic(uploadedBytes(apuUpload("pdf"), "application/pdf", "apus.pdf"), "%PDF");
    }

    @Test
    void uploadsRenderedBudgetWorkbook() {
        String body = PAYLOAD.replace("\"formato\":\"pdf\"", "\"formato\":\"xlsx\"")
                .replace("\"budget.pdf\"", "\"budget.xlsx\"");
        assertMagic(
                uploadedBytes(body, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "budget.xlsx"),
                "PK");
    }

    @Test
    void uploadsRenderedTechnicalSpecificationsDocx() {
        assertMagic(
                uploadedBytes(
                        specificationUpload(SPECIFICATION),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "especificaciones.docx"),
                "PK");
    }

    @Test
    void rejectsInvalidTechnicalSpecificationBeforeStorage() {
        String token = ServiceJwtTestProfile.exportToken(OPERATION_ID);
        rejected(token, specificationUpload(SPECIFICATION.replace("\"schemaVersion\":1", "\"schemaVersion\":2")), 400);
        rejected(token, specificationUpload(SPECIFICATION.replace("Texto técnico", "   ")), 400);
    }

    @Test
    void rejectsWrongIssuer() {
        rejected(
                ServiceJwtTestProfile.exportToken(
                        "user", "documentos", Set.of("CORE_SERVICE"), 30, false, OPERATION_ID),
                PAYLOAD,
                401);
    }

    @Test
    void rejectsWrongAudience() {
        rejected(
                ServiceJwtTestProfile.exportToken(
                        "core", "otro-servicio", Set.of("CORE_SERVICE"), 30, false, OPERATION_ID),
                PAYLOAD,
                401);
    }

    @Test
    void rejectsExpiredToken() {
        rejected(
                ServiceJwtTestProfile.exportToken(
                        "core", "documentos", Set.of("CORE_SERVICE"), -120, false, OPERATION_ID),
                PAYLOAD,
                401);
    }

    @Test
    void rejectsLongLivedServiceToken() {
        rejected(
                ServiceJwtTestProfile.exportToken(
                        "core", "documentos", Set.of("CORE_SERVICE"), 120, false, OPERATION_ID),
                PAYLOAD,
                401);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        rejected(
                ServiceJwtTestProfile.exportToken("core", "documentos", Set.of("CORE_SERVICE"), 30, true, OPERATION_ID),
                PAYLOAD,
                401);
    }

    @Test
    void rejectsUnsupportedProjectionVersionAndFormat() {
        String token = ServiceJwtTestProfile.exportToken(OPERATION_ID);
        rejected(
                token,
                PAYLOAD.replace("\"projection\":{\"schemaVersion\":1", "\"projection\":{\"schemaVersion\":2"),
                400);
        rejected(
                token,
                PAYLOAD.replace("\"formato\":\"pdf\"", "\"formato\":\"exe\"").replace("budget.pdf", "budget.exe"),
                400);
    }

    @Test
    void rejectsOversizedUploadBeforeRendering() {
        // Let the server reject Content-Length before the client streams an oversized body.
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .header("Expect", "100-continue")
                .contentType("application/json")
                .body(PAYLOAD + " ".repeat(8 * 1024 * 1024))
                .when()
                .post(PATH)
                .then()
                .statusCode(413);
        verifyNoInteractions(storage);
    }
}
