# Runtime image only — Jenkins builds and tests the JAR with Maven first,
# so we don't compile twice. For a fully self-contained build, see Dockerfile.multistage.
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="java-app" \
      org.opencontainers.image.description="Java app built by Jenkins, deployed by Argo CD"

# Run as a non-root user
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
WORKDIR /app

COPY --chown=app:app target/java-app.jar app.jar

USER app
EXPOSE 8080

# Container-aware JVM memory settings
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
