# Stage 1: Build dependencies and the application jar
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy root pom and all submodules to support reactor compilation
COPY pom.xml .
COPY repo repo
COPY addon-core/pom.xml addon-core/
COPY addon-db/pom.xml addon-db/
COPY clockify-rest-client/pom.xml clockify-rest-client/
COPY addon-testkit/pom.xml addon-testkit/
COPY addon-expenses-rest-api/pom.xml addon-expenses-rest-api/

# Download dependencies first (cache layer)
RUN mvn dependency:go-offline -B -pl addon-expenses-rest-api -am || true

# Copy source code of all modules
COPY addon-core addon-core
COPY addon-db addon-db
COPY clockify-rest-client clockify-rest-client
COPY addon-testkit addon-testkit
COPY addon-expenses-rest-api addon-expenses-rest-api

# Package the target module (addon-expenses-rest-api) alongside its dependent sibling modules
RUN mvn clean package -B -pl addon-expenses-rest-api -am -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar from the builder stage
COPY --from=builder /build/addon-expenses-rest-api/target/mileage-for-clockify-*.jar app.jar

# Expose standard HTTP port
EXPOSE 8080

# Configure JVM flags for container environments and start
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
