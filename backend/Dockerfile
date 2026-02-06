# ════════════════════════════════════════════════
# HiveApp Platform — Multi-Stage Docker Build
# ════════════════════════════════════════════════

# ── Stage 1: Build ──────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Cache dependencies first (layer caching optimization)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
COPY config ./config
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ───────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="HiveApp Team"
LABEL description="HiveApp Modular ERP Platform"

# Security: non-root user
RUN addgroup -S hiveapp && adduser -S hiveapp -G hiveapp

WORKDIR /app

# Copy only the built artifact
COPY --from=build /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Switch to non-root user
USER hiveapp

# Expose port
EXPOSE 8080

# JVM optimization for containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
