# Imagen de producción (OPS-01). Se construye en CI y se publica por versión; un colegio solo la descarga.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/colegio-saas-*.jar /src/app.jar

FROM eclipse-temurin:25-jre
# curl para el chequeo de salud; un usuario sin privilegios para la aplicación.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home /app colegio \
    && mkdir -p /app /data/files /backups \
    && chown colegio /data/files
COPY --from=build /src/app.jar /app/app.jar
USER colegio
ENV SPRING_PROFILES_ACTIVE=mysql \
    APP_ENVIRONMENT=production \
    APP_STORAGE_DIR=/data/files \
    APP_SECURE_COOKIES=true \
    APP_BACKUP_STATUS_FILE=/backups/last-backup.json \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
VOLUME ["/data/files"]
# Solo "¿está vivo?": un respaldo atrasado alerta, pero no reinicia el sitio.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
