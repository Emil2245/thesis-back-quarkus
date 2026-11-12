plugins {
    java
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(project(":documentos-contract"))
    implementation(enforcedPlatform(libs.quarkus.platform.bom))
    implementation(platform(libs.quarkus.amazon.services.bom))
    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.smallrye.jwt)
    implementation(libs.quarkus.hibernate.validator)
    implementation(libs.quarkus.config.yaml)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.amazon.s3)
    implementation(libs.aws.sdk.url.connection.client)
    implementation(libs.poi.ooxml)
    implementation(libs.openpdf)

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.smallrye.jwt.build)
    testImplementation(libs.rest.assured)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.pdfbox) // PDF parse-back in renderer format tests ported from Core (stage 05)
}

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
    include("**/*Test.class")
    include("**/*IT.class")
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
