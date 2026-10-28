# ---------------------------------------------------------------------------
# ClaseYa backend — multi-stage build (Spring Boot 3.5.16 / Java 17)
# Stage 1 compiles with Maven; stage 2 ships only the runnable JAR, runs as a
# non-root user and exposes a container health check (OPS-02).
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first so source changes don't re-download Maven artifacts.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# OPS-02: never run the JVM as root inside the container.
RUN groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /app/target/clase-ya-backend-0.0.1-SNAPSHOT.jar app.jar

# UTC timestamps regardless of the host timezone.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

EXPOSE 8080

# OPS-02: probe the public Actuator health endpoint (OPS-001). A 503 marks the container unhealthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

USER app

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
