# Use OpenJDK 21 as base image
FROM openjdk:21-jre-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
RUN ./mvnw clean package -DskipTests

# Create final runtime image
FROM openjdk:21-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r telegram && useradd -r -g telegram telegram

# Set working directory
WORKDIR /app

# Copy built JAR from build stage
COPY --from=0 /app/target/TelegramStarManager-*.jar app.jar

# Copy environment template
COPY .env.example .env.example

# Change ownership to non-root user
RUN chown -R telegram:telegram /app

# Switch to non-root user
USER telegram

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/api/ping || exit 1

# Set JVM options for container
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
