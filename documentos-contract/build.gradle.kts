plugins {
    `java-library`
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
