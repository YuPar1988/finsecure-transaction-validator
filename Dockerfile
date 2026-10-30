# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml dependency-check-suppressions.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && mkdir -p target/extracted \
    && java -Djarmode=tools -jar target/transaction-validator.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine
ARG APP_VERSION=1.0.0
LABEL org.opencontainers.image.title="finsecure-transaction-validator" \
      org.opencontainers.image.description="FinSecure Pay transaction validation API" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN apk upgrade --no-cache \
    && addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/ ./

USER 10001
EXPOSE 8080
ENV IMAGE_TAG=${APP_VERSION}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
