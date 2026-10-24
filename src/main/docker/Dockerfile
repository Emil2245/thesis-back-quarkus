# syntax=docker/dockerfile:1
# Imagen canónica del backend (multi-stage). Contexto de build = raíz del repo.
FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon dependencies > /dev/null || true
COPY src/ src/
RUN ./gradlew --no-daemon quarkusBuild -x test -x spotlessCheck

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24
LABEL org.opencontainers.image.source="https://github.com/emil2245/thesis-back-quarkus" \
      org.opencontainers.image.title="propuestas-api"
USER root
RUN microdnf -y update ca-certificates && microdnf clean all
COPY --chown=185 --from=build /src/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 --from=build /src/build/quarkus-app/*.jar /deployments/
COPY --chown=185 --from=build /src/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 --from=build /src/build/quarkus-app/quarkus/ /deployments/quarkus/
EXPOSE 8080 9000
USER 185
ENV QUARKUS_PROFILE=prod \
    JAVA_OPTS_APPEND="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
