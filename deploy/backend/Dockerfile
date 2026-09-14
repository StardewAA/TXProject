FROM m.daocloud.io/docker.io/library/maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

# 国内镜像加速，避免访问 Maven Central 超时导致构建失败
# 注意：/root/.m2 会被下方 RUN 的 cache mount 覆盖，故存放到镜像固定路径
COPY deploy/backend/settings-mirror.xml /opt/maven/settings-mirror.xml

COPY . .

ARG MODULE_PATH

RUN test -n "${MODULE_PATH}" \
    && mvn -B -ntp --settings /opt/maven/settings-mirror.xml \
        -pl "${MODULE_PATH}" -am -Dmaven.test.skip=true package

RUN find "${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' -exec cp '{}' /app.jar \; -quit \
    && test -f /app.jar

FROM m.daocloud.io/docker.io/library/eclipse-temurin:25-jre

RUN sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        -e 's|http://security.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get -o Acquire::Retries=5 update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app.jar /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 48080

HEALTHCHECK --interval=20s --timeout=5s --retries=15 --start-period=90s \
    CMD curl --fail --silent http://127.0.0.1:48080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
