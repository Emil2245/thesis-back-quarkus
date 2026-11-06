plugins {
    base
    alias(libs.plugins.quarkus) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "ec.uce.propuestas"
    version = "1.0.0-SNAPSHOT"
    repositories { mavenCentral() }
}

// Keep the root build/check/test entrypoints usable while the applications
// have independent compile, test and image lifecycles.
tasks.named("assemble") { dependsOn(":core:assemble", ":documentos:assemble") }
tasks.named("check") { dependsOn(":core:check", ":documentos:check") }
tasks.register("test") {
    group = "verification"
    dependsOn(":core:test", ":documentos:test")
}
tasks.register("spotlessCheck") {
    group = "verification"
    dependsOn(":core:spotlessCheck", ":documentos:spotlessCheck")
}
tasks.register("spotlessApply") {
    group = "formatting"
    dependsOn(":core:spotlessApply", ":documentos:spotlessApply")
}
