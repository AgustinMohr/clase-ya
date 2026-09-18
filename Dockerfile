# ---------------------------------------------------------------------------
# ClaseYa backend — multi-stage build (Spring Boot 3.2.6 / Java 17)
# Stage 1 compiles with Maven; stage 2 ships only the runnable JAR.
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

COPY --from=build /app/target/clase-ya-backend-0.0.1-SNAPSHOT.jar app.jar

# UTC timestamps regardless of the host timezone.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
