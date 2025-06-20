# ============================================
# ОПТИМИЗИРОВАННЫЙ DOCKERFILE
# Поддержка различных окружений через targets
# ============================================

# ============================================
# BUILD STAGE - ОБЩИЙ ДЛЯ ВСЕХ ОКРУЖЕНИЙ
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install Maven
RUN apk add --no-cache maven

WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
ARG SKIP_TESTS=true
RUN if [ "$SKIP_TESTS" = "true" ]; then \
        mvn clean package -DskipTests -B; \
    else \
        mvn clean package -B; \
    fi

# ============================================
# DEVELOPMENT TARGET
# ============================================
FROM eclipse-temurin:21-jre-alpine AS development

# Install development tools
RUN apk add --no-cache curl net-tools

# Create non-root user
RUN addgroup -S telegram && adduser -S telegram -G telegram

WORKDIR /app

# Copy built JAR from build stage
COPY --from=builder /app/target/TelegramStarManager-*.jar app.jar

# Copy environment template
COPY .env.example .env.example

# Create logs directory
RUN mkdir -p logs config scripts && chown -R telegram:telegram /app

# Switch to non-root user
USER telegram

# Expose ports (app + debug)
EXPOSE 8080 5005

# Development JVM settings
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -Djava.awt.headless=true -Dfile.encoding=UTF-8"
ENV DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Health check for development
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/ping || exit 1

# Development entrypoint with debug support
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $DEBUG_OPTS -jar app.jar"]

# ============================================
# PRODUCTION TARGET (DEFAULT)
# ============================================
FROM eclipse-temurin:21-jre-alpine AS production

# Install only curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S telegram && adduser -S telegram -G telegram

WORKDIR /app

# Copy built JAR from build stage
COPY --from=builder /app/target/TelegramStarManager-*.jar app.jar

# Copy environment template
COPY .env.example .env.example

# Create necessary directories
RUN mkdir -p logs && chown -R telegram:telegram /app

# Switch to non-root user
USER telegram

# Expose port
EXPOSE 8080

# Production JVM settings
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

# Health check for production
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/api/ping || exit 1

# Production entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================
# KOYEB TARGET (ULTRA-OPTIMIZED)
# ============================================
FROM eclipse-temurin:21-jre-alpine AS koyeb

# Install only essential tools
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S telegram && adduser -S telegram -G telegram

WORKDIR /app

# Copy built JAR from build stage
COPY --from=builder /app/target/TelegramStarManager-*.jar app.jar

# Minimal setup
RUN chown telegram:telegram /app/app.jar

# Switch to non-root user
USER telegram

# Expose port
EXPOSE 8080

# Koyeb optimized JVM settings for 512MB RAM limit
ENV JAVA_OPTS="-Xmx400m -Xms200m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

# Optimized health check for limited resources
HEALTHCHECK --interval=45s --timeout=10s --start-period=90s --retries=2 \
    CMD curl -f http://localhost:8080/api/ping || exit 1

# Koyeb entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
