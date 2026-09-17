FROM m.daocloud.io/docker.io/library/maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

# 国内镜像加速，避免访问 Maven Central 超时导致构建失败
# 注意：/root/.m2 由下方 RUN 的 cache mount 持久化（BuildKit 命名缓存卷，跨构建保留），
#       依赖与插件只下载一次，后续构建直接命中缓存
COPY deploy/backend/settings-mirror.xml /opt/maven/settings-mirror.xml

COPY . .

ARG MODULE_PATH

# --mount=type=cache：Maven 本地仓库跨构建持久化。
# 清理缓存需显式执行 docker builder prune（日常 docker system prune 不影响）
RUN --mount=type=cache,target=/root/.m2 \
    test -n "${MODULE_PATH}" \
    && mvn -B -ntp --settings /opt/maven/settings-mirror.xml \
        -pl "${MODULE_PATH}" -am -Dmaven.test.skip=true package

RUN set -eux; \
    MAIN_JAR=$(find "${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1); \
    echo "picked jar: ${MAIN_JAR}"; \
    ls -l "${MAIN_JAR}"; \
    cp "${MAIN_JAR}" /app.jar; \
    ls -l /app.jar; \
    test -s /app.jar

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
