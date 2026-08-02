plugins {
    java
    alias(libs.plugins.quarkus)
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
    implementation(libs.quarkus.smallrye.jwt.build)
    implementation(libs.quarkus.hibernate.validator)
    implementation(libs.quarkus.smallrye.jwt)
    implementation(libs.quarkus.security.jpa)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.config.yaml)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.elytron.security.common)

    implementation(libs.poi.ooxml)
    implementation(libs.openpdf)

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jqwik)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    include("**/*Test.class")
    include("**/*IT.class")
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
