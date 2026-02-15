FROM ubuntu:latest
LABEL authors="nikilsrishen"

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle wrapper + build scripts
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

# Copy source
COPY src/ ./src/

# Build the fat JAR inside container
RUN ./gradlew shadowJar -x test --quiet

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /app/build/libs/ProjectPilot-0.1.0.jar ./ProjectPilot.jar

# Expose API port
EXPOSE 9090

# Run the app
CMD ["java", "-jar", "ProjectPilot.jar"]
