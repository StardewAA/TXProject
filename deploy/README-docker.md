# txgy 大服务 —— Docker Compose 运维说明

所有服务统一由大服务项目 **txgy**（`deploy/docker-compose.yml`）管理，包含 MySQL、Redis、Nacos
以及 22 个后端微服务（网关 + 各业务模块）。每个服务都有独立的容器名 `txgy-*`，可单独启停，
互不干扰；服务间仅通过 Docker 网络（`txgy-dev`）按需通信，不强制全部同时运行。

## ⚠️ 受保护配置：Nacos 命名空间必须为 public

本机多次出现「docker 配置被改坏 → 服务全部起不来」，根因两类，现已加保护：

**两侧口径（2026-09-20 确认，别搞混）**：

| 侧 | 命名空间 | 谁在用 |
| --- | --- | --- |
| **远端 `origin/master`** | `dev`（团队默认，由 `8dd0b141` 起） | 其他同事克隆/拉取得到的值 |
| **本机工作区** | `public`（空值） | 本机容器 + `run-local.cmd`（网关按 public 找 `bidding-server`） |

两侧靠 **git `skip-worktree`** 隔离：本机 public 不会被 push 上去，远端 dev 也不会在 pull 时覆盖本机。
三个本机化文件必须保持该标记：

```bash
git update-index --skip-worktree deploy/docker-compose.yml \
  txgy-module-bidding/deploy-bidding-local.override.yml \
  txgy-module-bidding/txgy-module-bidding-server/src/main/resources/application-local.yaml
```

- 若标记丢失：pull 会把本机 public 覆盖回 dev（表现为「服务起不来」），push 则可能把 public 推给全组。
- 若本机 yaml 被写成 `dev`、或 override 里 gateway/system/infra 的置空覆盖被删：
  网关在 public 里查不到 `bidding-server`（日志 `Unable to find instance for bidding-server`），前端 `/admin-api/bidding/**` 全线 503。

**守卫脚本**（检测 + 自愈 + 识别抢端口的外来容器）：

```bash
# 只检查（退出码 0=通过，1=存在漂移）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1
# 检查并自动修复：namespace 修回空(=public)、重写 override、把 skip-worktree 标记补回来
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1 -Fix
# 改动过保护脚本本身后，重新封印（否则会告警"与封印不一致"）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/guard-nacos-config.ps1 -Seal
```

守卫已接入三处，日常无需手动跑：
- `deploy/start.bat` 的 [2.5/5] 步：漂移即阻止启动并给出修复命令；
- `txgy-module-bidding/txgy-module-bidding-server/run-local.cmd`：本机启动 bidding 前先过守卫（跳过用 `set SKIP_NACOS_GUARD=1`）；
- `txgy-module-bidding/.githooks/pre-commit`：新增 ns-guard（需 `git config core.hooksPath .githooks` 启用），
  拒绝「把 public 改成 dev」或「丢掉置空覆盖」的提交（临时跳过 `NO_NS_GUARD=1`）。

**另外**：若出现非 `txgy-nacos` 的 nacos 容器（例如手工 `docker run` 起的、把 9848 映射到 9858 的那只），
它会抢占 88xx/98xx 端口并造成"映射被改成了 98 啥啥"的错觉 —— 守卫会报出来，确认无用后 `docker rm -f <name>`。

**提交时别把本机 public 推出去**：用 `deploy/commit-nacos-protection.ps1`（默认预览，`-Apply` 才写历史）。
它的白名单**只含保护工具链**（守卫脚本/封印/start.bat/README/run-local.cmd/pre-commit），
并内置反推送闸：一旦发现上表三个本机化文件被 `git add` 进暂存区就**直接中止**，避免把 public 提交给全组。
守卫接线后，即便某次 `pull` / `reset --hard` 覆盖了本机文件，**下次启动会自动 `-Fix` 修回 public**（start.bat [2.5/5] 与 run-local.cmd 都有兜底）。

## 部署流程（先本地验证，后构建镜像）

1. 本地打包（JDK 25，跳过测试）：
   ```bash
   mvn -P '!2.1' -P '!jdk-1.8' -Dmaven.compiler.source=25 -Dmaven.compiler.target=25 -Dmaven.test.skip=true package
   ```
   > 本机 `D:\maven\apache-maven-3.9.11\conf\settings.xml` 中存在损坏的 profile（id=2.1，
   > 把编译目标强制成 2.1），必须用 `-P '!2.1'` 停用，并显式指定编译目标为 25。

2. 逐个本地验证模块（脚本会逐个启动→健康检查→停止，只占用少量内存）：
   ```bash
   bash script/docker/verify-local.sh
   ```
   前提：Docker 中 mysql/redis/nacos 已启动且数据库已初始化（`deploy/mysql/init.d`）。

3. 暂存 jar 并构建镜像（薄镜像，秒级构建，不再重复跑 Maven）：
   ```bash
   bash script/docker/stage-jars.sh     # 将 target/*.jar 复制到 deploy/jars/<svc>/app.jar
   bash script/docker/build-images.sh   # 逐个 docker compose build
   ```

4. 逐个在 Docker 中验证模块（每次只运行 mysql+redis+nacos+1 个模块）：
   ```bash
   bash script/docker/verify-modules.sh
   ```

## 环境变量（deploy/.env）

| 变量 | 值 | 说明 |
| --- | --- | --- |
| MYSQL_ROOT_PASSWORD | 123456 | MySQL root 密码 |
| MYSQL_DATABASE | ruoyi-vue-pro | 主数据库名 |
| NACOS_AUTH_TOKEN / IDENTITY_* | 见文件 | Nacos 鉴权配置（服务端 API 鉴权默认关闭） |
| TZ | Asia/Shanghai | 时区 |

## 常用命令（在 deploy 目录执行）

```bash
cd deploy

# 查看大服务状态
docker compose ps

# 启动中间件（MySQL/Redis/Nacos，默认端口 3306/6379/8848）
docker compose up -d mysql redis nacos

# 启动单个模块（可任意组合，建议同一时间只运行少量服务以节省内存）
docker compose up -d gateway          # 网关 48080
docker compose up -d system           # 系统模块
docker compose up -d infra            # 基础设施模块
# ... 其余模块: member bpm pay report mp product trade promotion statistics crm erp iot iot-gateway mes wms im ai hrm fms

# 停止单个模块
docker compose stop system

# 停止全部（不删除容器与镜像）
docker compose stop

# 重新启动已停止的服务
docker compose start system

# 查看模块日志
docker compose logs -f --tail=100 system
```

> 内存有限时，建议同时只运行：mysql + redis + nacos + 1~2 个业务模块。

## 数据库初始化

MySQL 首次启动时执行 `deploy/mysql/init.d` 中的脚本（67 个模块脚本 + 1 个全量基础库
`0099-base-ruoyi-vue-pro.sql`，约 560 张表；该基础库文件末尾已附加 Flowable 冗余表清理）。
初始化完成后数据保存在命名卷 `mysql-data` 中，重启容器不会重复初始化。
> 注意：原始 `deploy/mysql/init` 中的 `0068/0069`（全库 dump，含 mysql 系统库）与
> `0070`（旧版 Flowable 修复脚本）未纳入 `init.d`，避免污染/破坏初始化。
>
> 文件存储：基础库默认将数据库存储（通常为 `infra_file_config.id = 4`）设为 master。
> 需要接入 OSS 时，在「基础设施 → 文件配置」新建 S3 配置（不要覆盖数据库存储配置，
> 以免历史文件失去原存储映射），Endpoint 填 `oss-cn-beijing.aliyuncs.com`，Bucket 填
> 对应 Bucket，Region 填 `cn-beijing`，Domain 留空使用 HTTPS 默认 Bucket 域名，
> Path Style 关闭，公开访问仅用于确定可公开的图片。Access Key/Secret 分别填写
> `env:OSS_ACCESS_KEY`、`env:OSS_ACCESS_SECRET`，并将轮换后的最小权限 RAM 凭证放入
> 未跟踪的 `deploy/.env` 或部署密钥管理器。切换 master 后重启 infra 模块（客户端有本地缓存）；
> 新上传文件写入 OSS，旧文件继续依据各自 configId 从原存储读取，存量图片迁移需另行执行和验收。
> PC、H5 的商城图片上传已统一走 `/infra/file/upload`，不需要前端持有 OSS 密钥。
> 本地开发如开着 Windows 系统代理，S3 Java 客户端默认直连 OSS，不会自动继承系统代理；建议 TUN 关闭或在代理规则中将 OSS 域名直连。若要让容器显式走代理，须单独配置并验证代理客户端对 OSS TLS 的支持。

## 说明

- 所有后端模块容器内部监听 48080（`SERVER_PORT`），仅网关对外发布 48080；微服务之间通过
  Nacos 注册发现（服务端 API 鉴权关闭，namespace 使用 public）。
- 模块的数据库/Redis/Nacos 连接通过环境变量注入（见 compose 中 `x-backend-service` 锚点），
  无需修改代码即可在容器内连通 `mysql`、`redis`、`nacos` 服务名。
- 镜像基于本地打包的 jar 构建（薄镜像，见 `deploy/jars` 与 `x-thin-build` 锚点）。
- 前端（`frontend` 服务，5173 端口）为可选，默认未构建运行。
