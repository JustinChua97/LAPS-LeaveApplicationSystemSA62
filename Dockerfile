# syntax=docker/dockerfile:1
# Multi-stage build — issue #34
# Stage 1: build the JAR with Maven + JDK 17
# Stage 2: run with a minimal JRE 17 image

# -------------------------------------------------------------------
# Stage 1: builder
# -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy Maven wrapper and POM first to cache dependency downloads
COPY laps/pom.xml .
RUN mvn -B dependency:go-offline -q

# Copy source and build (skip tests — tests run in CI before this stage)
COPY laps/src ./src
RUN mvn -B package -DskipTests -q && \
    mv target/laps-*.jar target/app.jar

# -------------------------------------------------------------------
# Stage 2: runtime
# -------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for least-privilege (ASVS V10.2.3)
RUN addgroup -S laps && adduser -S laps -G laps

WORKDIR /app

COPY --from=builder /build/target/app.jar app.jar

# No secrets in ENV — all injected at runtime via --env-file (ASVS V8.3.4)
EXPOSE 8080

USER laps

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
