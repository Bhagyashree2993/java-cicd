# =============================================================================
# Habit Tracker (given HabitApp code) - production multi-stage Docker build
#   Stage 1 (build)  : Maven + JDK 21 compiles and packages target/habit-tracker.jar
#   Stage 2 (runtime): minimal JRE 21 Alpine image, non-root user, health check
# Build:  docker build -t habit-tracker:local .
# Run:    docker run -p 8080:8080 habit-tracker:local   ->  http://localhost:8080
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Download dependencies first: this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Then compile and package (tests already ran in the Jenkins "Build & Unit Test" stage)
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="habit-tracker" \
      org.opencontainers.image.description="Habit Tracker Spring Boot REST API + dashboard (HabitApp)" \
      org.opencontainers.image.version="${APP_VERSION}"

# Non-root user with a fixed UID (matches runAsUser in the Helm chart)
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
WORKDIR /app

COPY --from=build --chown=app:app /build/target/habit-tracker.jar app.jar

USER 1001
EXPOSE 8080

ENV APP_VERSION=${APP_VERSION} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# The app has no actuator, so the health check calls its own REST API
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/habits >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
