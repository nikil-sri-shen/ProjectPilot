# ----------------------------
# Stage 1: Build
# ----------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

# Copy source code
COPY src/ ./src/

# Make wrapper executable
RUN chmod +x gradlew

# Build fat JAR
RUN ./gradlew clean shadowJar -x test --quiet


# ----------------------------
# Stage 2: Runtime
# ----------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy shadow JAR (IMPORTANT: use wildcard)
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose app port (match your app config!)
EXPOSE 9090

# Run application
CMD ["java", "-jar", "app.jar"]
