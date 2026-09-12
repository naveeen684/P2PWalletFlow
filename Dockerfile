FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S wallet && adduser -S wallet -G wallet \
    && apk add --no-cache wget \
    && mkdir -p /app \
    && chown -R wallet:wallet /app
WORKDIR /app
COPY --from=build --chown=wallet:wallet /workspace/target/wallet-transfer-service-1.0.0.jar app.jar
USER wallet
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]

