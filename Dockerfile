# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM gradle:jdk21 AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY config config
COPY domain domain
COPY application application
COPY infrastructure infrastructure
COPY api api
COPY bootstrap bootstrap
RUN chmod +x gradlew && ./gradlew :bootstrap:bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/bootstrap/build/libs/kotlin-bitcoins.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
