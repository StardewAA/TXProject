FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

ARG MODULE_PATH

COPY . .

# 仅构建目标服务及其 reactor 依赖，避免无关业务模块的编译状态阻断单服务镜像。
# cache mount 持久化 Maven 仓库；artifact.threads=1 串行下载避免大文件并发卡死。
RUN --mount=type=cache,target=/root/.m2/repository \
    test -n "${MODULE_PATH}" && \
    mvn -pl "${MODULE_PATH}" -am -Dmaven.test.skip=true -Dmaven.artifact.threads=1 package

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
