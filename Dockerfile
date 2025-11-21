# ============================================
# ProdQ API - Optimized for Raspberry Pi
# ============================================
# Multi-arch support: linux/amd64, linux/arm64
# Build: docker buildx build --platform linux/amd64,linux/arm64 -t czubakjakub/prodq-api:latest .
# ============================================

# ============================================
# Etap 1: Maven Build
# ============================================
FROM eclipse-temurin:21-jdk-jammy AS builder

# Metadane
LABEL maintainer="ProdQ Team"
LABEL description="ProdQ API - Production Management System Builder"
LABEL version="5.0.0"

WORKDIR /build

# Optymalizacja dla ARM64: Kopiuj tylko Maven files dla lepszego cache
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Nadaj uprawnienia wykonywania dla mvnw (ważne na Linux/ARM64)
RUN chmod +x mvnw

# Pobierz dependencies (cache layer - zmieni się tylko gdy zmieni się pom.xml)
RUN ./mvnw dependency:go-offline -B

# Kopiowanie kodu źródłowego
COPY src src

# Budowanie aplikacji (skipTests dla szybszego buildu na ARM64)
# Spring Boot tworzy executable JAR z embedded Tomcat
RUN ./mvnw clean package -DskipTests -B && \
    # Wyciągnij JAR do znanej lokalizacji
    mv target/*.jar target/app.jar

# ============================================
# Etap 2: Runtime (JRE only)
# ============================================
FROM eclipse-temurin:21-jre-jammy

# Metadane
LABEL maintainer="ProdQ Team"
LABEL description="ProdQ API - Runtime"

WORKDIR /app

# Instalacja narzędzi do healthcheck
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Kopiowanie JAR z etapu builder
COPY --from=builder /build/target/app.jar app.jar

# Security: Stworzenie non-root user
RUN groupadd -r -g 1001 prodq && \
    useradd -r -u 1001 -g prodq -d /app -s /sbin/nologin prodq && \
    chown -R prodq:prodq /app

# Przełączenie na non-root user
USER prodq

# Expose portu (domyślnie Spring Boot używa 8080)
EXPOSE 8080

# Healthcheck (sprawdzenie czy Spring Boot Actuator odpowiada)
# Wymaga: spring-boot-starter-actuator w pom.xml
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM optimization dla Raspberry Pi ARM64
# -Xms256m: Initial heap (szybki start)
# -Xmx1200m: Max heap 1.2GB (dostosowane dla Raspberry Pi 8GB, limit 1.5GB)
# -XX:+UseG1GC: Garbage-First GC (lepszy dla ARM64)
# -XX:MaxGCPauseMillis=200: Max GC pause 200ms
# -XX:+UseStringDeduplication: Oszczędność RAM
ENV JAVA_OPTS="-Xms256m -Xmx1200m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

# Uruchomienie aplikacji
# Spring Boot DevTools są automatycznie wyłączone w packaged JAR
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
