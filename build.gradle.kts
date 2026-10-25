plugins {
    java
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}

group = "ec.uce.propuestas"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.platform.bom))

    implementation(libs.quarkus.hibernate.orm.panache)
    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.micrometer.registry.prometheus)
    implementation(libs.quarkus.opentelemetry)
    implementation(libs.quarkus.logging.json)
    implementation(libs.quarkus.smallrye.jwt.build)
    implementation(libs.quarkus.hibernate.validator)
    implementation(libs.quarkus.smallrye.jwt)
    implementation(libs.quarkus.security.jpa)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.config.yaml)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.mailer)
    implementation(libs.quarkus.elytron.security.common)

    implementation(libs.poi.ooxml)
    implementation(libs.openpdf)
    implementation(libs.commons.csv)
    implementation(libs.jackson.databind.nullable)

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jqwik)
    // Plan 031 (P-37) - PDFBox 3 API para parse-back del writer PDF.
    // Apache-2.0; solo usado en tests (writer sigue siendo OpenPDF 3.0.5).
    testImplementation(libs.pdfbox)
}

// CVE-2026-75595 (netty-handler 4.1.136): el BOM de Quarkus 3.40.x fija 4.1.138;
// se fuerza el último parche 4.1.x (4.1.139) hasta que el BOM lo incluya. Revisar al subir Quarkus.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true) {
            useVersion("4.1.139.Final")
            because("CVE-2026-75595")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Suite de ~900 tests con Dev Services agota el heap por defecto de 512m.
    maxHeapSize = "3g"
    if (project.hasProperty("excludeKnownResiduals")) {
        filter {
            // Residual aceptado, excepción documentada 2026-08-28 en CLAUDE.md.
            excludeTestsMatching("ec.uce.propuestas.motor.MotorConsolidacionTest.GM_19_total_general_tulcan")
            excludeTestsMatching("ec.uce.propuestas.motor.MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan")
        }
    }
    include("**/*Test.class")
    include("**/*IT.class")
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    // Plan 031 (P-37, audit closure) — propiedad para activar la validación
    // focal contra el XSD oficial Project 2007 descargado a /tmp. Por defecto
    // NO se pasa (los tests focales usan el profile XSD offline); en CI/local
    // se activa con -Dmspdi.official.xsd=/ruta/al/mspdi_pj12.xsd.
    if (project.findProperty("mspdi.official.xsd") != null) {
        systemProperty("mspdi.official.xsd", project.findProperty("mspdi.official.xsd"))
    }
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
