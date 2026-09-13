# ─── Build stage ────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy POM and resolve dependencies first (Docker layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copy source and build (skip integration tests — run separately in CI)
COPY src ./src
RUN mvn package -B -q -DskipTests

# ─── Runtime stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S pricewatch && adduser -S pricewatch -G pricewatch
USER pricewatch

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# Expose HTTP port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
