FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

# 国内镜像加速，避免访问 Maven Central 超时导致构建失败
# 注意：/root/.m2 会被下方 RUN 的 cache mount 覆盖，故存放到镜像固定路径
COPY deploy/backend/settings-mirror.xml /opt/maven/settings-mirror.xml

COPY . .

ARG MODULE_PATH

RUN --mount=type=cache,target=/root/.m2 \
    mvn --settings /opt/maven/settings-mirror.xml -pl "${MODULE_PATH}" -am -Dmaven.test.skip=true package

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
