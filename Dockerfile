# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

# Minimal runtime stage: no Maven, source tree, or root user.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 finance \
    && useradd --system --uid 10001 --gid finance --home-dir /app --shell /usr/sbin/nologin finance

COPY --from=build --chown=finance:finance /workspace/target/finance-0.0.1-SNAPSHOT.jar /app/finance.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=75.0"

USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/finance.jar"]
