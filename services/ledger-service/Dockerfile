# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
# Warms the dependency layer so source edits do not re-download everything. This is a cache
# optimisation only, so a partial resolve must not fail the build -- `package` below fetches
# anything still missing.
RUN mvn -B dependency:go-offline -q || true

COPY src ./src
# Tests are skipped here: the integration suite needs Testcontainers, which would mean a
# Docker daemon inside the build. Tests run via `mvn test` on the host / in CI instead.
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget \
 && addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
