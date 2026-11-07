# syntax=docker/dockerfile:1.7

FROM node:24.11.0-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build:prod \
    && test -f dist/identipat-ia/browser/index.html

FROM eclipse-temurin:21.0.8_9-jdk-jammy AS backend-build
WORKDIR /workspace/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend-build /workspace/frontend/dist/identipat-ia/browser/ src/main/resources/static/
RUN test -f src/main/resources/static/index.html \
    && ./mvnw -B -DskipTests package \
    && jar tf target/identipat-ia-0.0.1-SNAPSHOT.jar | grep -q '^BOOT-INF/classes/static/index.html$' \
    && jar tf target/identipat-ia-0.0.1-SNAPSHOT.jar | grep -Eq '^BOOT-INF/classes/static/.*\.(js|css)$'

FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 identipat \
    && useradd --uid 10001 --gid identipat --no-create-home --shell /usr/sbin/nologin identipat
WORKDIR /app
COPY --from=backend-build --chown=10001:10001 /workspace/backend/target/identipat-ia-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
