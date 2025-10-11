# Multi-stage build for optimization
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Copy source code
COPY config-server ./config-server
COPY common ./common

# Build the application
RUN gradle :config-server:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user for security
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copy JAR from builder
COPY --from=builder /app/config-server/build/libs/*.jar app.jar

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
