# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system --gid 1001 formalda \
    && useradd --system --uid 1001 --gid formalda --home-dir /app --shell /usr/sbin/nologin formalda

WORKDIR /app

COPY --from=builder --chown=formalda:formalda /workspace/build/libs/*.jar /app/app.jar

USER formalda

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
