# syntax=docker/dockerfile:1
# Documentos is an independent Quarkus application; the build context is the repo root.
FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY core/build.gradle.kts core/build.gradle.kts
COPY documentos/build.gradle.kts documentos/build.gradle.kts
COPY documentos-contract/build.gradle.kts documentos-contract/build.gradle.kts
RUN ./gradlew --no-daemon :documentos:dependencies > /dev/null || true
COPY documentos/src/ documentos/src/
COPY documentos-contract/src/ documentos-contract/src/
RUN ./gradlew --no-daemon :documentos:quarkusBuild -x :documentos:test -x :documentos:spotlessCheck

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24
LABEL org.opencontainers.image.source="https://github.com/emil2245/thesis-back-quarkus" \
      org.opencontainers.image.title="propuestas-documentos"
USER root
RUN microdnf -y update ca-certificates && microdnf clean all
COPY --chown=185 --from=build /src/documentos/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 --from=build /src/documentos/build/quarkus-app/*.jar /deployments/
COPY --chown=185 --from=build /src/documentos/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 --from=build /src/documentos/build/quarkus-app/quarkus/ /deployments/quarkus/
EXPOSE 8081
USER 185
ENV QUARKUS_PROFILE=prod \
    JAVA_OPTS_APPEND="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
