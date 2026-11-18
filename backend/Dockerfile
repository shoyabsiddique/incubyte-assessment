# Multi-stage build of the API. The final image only holds a JRE and the application jar.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 salary
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER salary
# The production profile reads every setting from the environment (see .env.example) and
# refuses to start if one is missing or the JWT signing key is shorter than 32 bytes.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
