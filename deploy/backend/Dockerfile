FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY . .

COPY deploy/backend/maven-settings.xml /root/.m2/settings.xml

ARG MODULE_PATH

RUN mvn -s /root/.m2/settings.xml \
    -Dmaven.test.skip=true \
    -pl "${MODULE_PATH}" -am \
    -Daether.connector.http.requestTimeout=120000 \
    -Daether.connector.http.retryHandler.count=3 \
    -Dmaven.wagon.http.connectionTimeout=30000 \
    -Dmaven.wagon.http.readTimeout=120000 \
    -Dmaven.wagon.http.retryHandler.count=3 \
    package

RUN find "${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' -exec cp '{}' /app.jar \; -quit \
    && test -f /app.jar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app.jar /app/app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
