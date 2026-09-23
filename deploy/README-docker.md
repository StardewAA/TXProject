# txgy 大服务 —— Docker Compose 运维说明

所有服务统一由大服务项目 **txgy**（`deploy/docker-compose.yml`）管理，包含 MySQL、Redis、Nacos
以及 22 个后端微服务（网关 + 各业务模块）。每个服务都有独立的容器名 `txgy-*`，可单独启停，
互不干扰；服务间仅通过 Docker 网络（`txgy-dev`）按需通信，不强制全部同时运行。

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
> 文件存储：内置的示例配置里，只有「数据库」这一条（`infra_file_config.id = 4`）在本
> 环境可用，其余云存储示例都是占位密钥、连不通。基础库已把它设为 master，否则
> `/admin-api/infra/file/upload` 会直接返回 500，头像、GoView 大屏缩略图等所有上传
> 功能都会失败。切换 master 后需重启 infra 模块（客户端有本地缓存）。

## 说明

- 所有后端模块容器内部监听 48080（`SERVER_PORT`），仅网关对外发布 48080；微服务之间通过
  Nacos 注册发现（服务端 API 鉴权关闭，namespace 使用 public）。
- 模块的数据库/Redis/Nacos 连接通过环境变量注入（见 compose 中 `x-backend-service` 锚点），
  无需修改代码即可在容器内连通 `mysql`、`redis`、`nacos` 服务名。
- 镜像基于本地打包的 jar 构建（薄镜像，见 `deploy/jars` 与 `x-thin-build` 锚点）。
- 前端（`frontend` 服务，5173 端口）为可选，默认未构建运行。
