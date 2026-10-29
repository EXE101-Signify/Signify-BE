FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src/ src/
RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" /app/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
