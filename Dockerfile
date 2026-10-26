FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/wrapper/ ./gradle/wrapper/
COPY src/ ./src/
RUN sh ./gradlew --no-daemon bootJar \
    && cp build/libs/signify-be-0.0.1-SNAPSHOT.jar /tmp/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build --chown=10001:10001 /tmp/app.jar /app/app.jar
ENV SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=30s
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
