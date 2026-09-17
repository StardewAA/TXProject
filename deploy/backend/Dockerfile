# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY . .

# cache mount 持久化 Maven 仓库；artifact.threads=1 串行下载避免大文件并发卡死
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -Dmaven.test.skip=true -Dmaven.artifact.threads=1 package

ARG MODULE_PATH

RUN set -eux; \
    MAIN_JAR=$(find "${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1); \
    echo "picked jar: ${MAIN_JAR}"; \
    ls -l "${MAIN_JAR}"; \
    cp "${MAIN_JAR}" /app.jar; \
    ls -l /app.jar; \
    test -s /app.jar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app.jar /app/app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
