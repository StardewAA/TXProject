# txgy-cloud 微服务开发平台

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg" alt="Spring Boot 4.1.0" />
  <img src="https://img.shields.io/badge/Spring%20Cloud-2025.1.2-blue.svg" alt="Spring Cloud 2025.1.2" />
  <img src="https://img.shields.io/badge/Vue-3.2-brightgreen.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License" />
</p>

txgy-cloud 是一套基于 Spring Cloud 的企业级微服务快速开发平台（内部代号：OPENLAB BS 项目基础脚手架），内置系统管理、多租户、工作流、支付、商城、CRM、ERP、WMS、MES、IoT、IM、AI 大模型等业务模块，开箱即用。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端 | Java 25、Spring Boot 4.1.0、Spring Cloud 2025.1.2、Spring Cloud Alibaba（Nacos） |
| ORM | MyBatis-Plus 3.5.16、MyBatis-Plus-Join 1.5.7 |
| 中间件 | MySQL 8.x、Redis 7、Nacos 3.x、RabbitMQ（按需） |
| 工具 | Lombok 1.18.46、MapStruct 1.6.3、Knife4j 4.5.0、Redisson 4.6.1 |
| 前端 | Vue 3 + Vite（管理后台 vben / Element Plus）、uni-app（移动端）、go-view（数据大屏） |
| 构建 | Maven 3.9+ 多模块（约 200 个模块）、Docker Compose 一键部署 |

## 功能模块

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| txgy-gateway | API 网关（Spring Cloud Gateway，统一入口 `/admin-api`） | 48080 |
| txgy-server | 服务启动入口 | 48080 |
| txgy-module-system | 系统管理：用户、角色、权限、菜单、租户、字典等 | 48081 |
| txgy-module-infra | 基础设施：文件、配置、定时任务、代码生成、日志等 | 48082 |
| txgy-module-bpm | 工作流（Flowable） | 48083 |
| txgy-module-report | 报表与大屏 | 48084 |
| txgy-module-pay | 支付（微信、支付宝等） | 48085 |
| txgy-module-mp | 微信公众号 | 48086 |
| txgy-module-member | 会员中心 | 48087 |
| txgy-module-erp | ERP：采购、销售、库存、财务 | 48088 |
| txgy-module-crm | CRM：线索、客户、商机、合同 | 48089 |
| txgy-module-ai | AI 大模型：聊天、绘图、音乐、思维导图等 | 48090 |
| txgy-module-iot | IoT：设备接入与管理 | 48091 |
| txgy-module-mes | MES：生产制造执行 | 48091 |
| txgy-module-hrm | HRM：人力资源 | 48092 |
| txgy-module-wms | WMS：仓储管理 | 48092 |
| txgy-module-im | IM：即时通讯 | 48093 |
| txgy-module-fms | FMS：财务管理 | 48093 |
| txgy-module-mall | 商城：商品、交易、营销、统计 | 48100-48103 |

> 各业务模块均拆分为 `*-api`（RPC 接口）与 `*-server`（服务实现）两个子模块。

## 项目结构

```text
txgy-cloud-master-jdk25
├── txgy-dependencies/                # 依赖版本统一管理（BOM）
├── txgy-framework/                   # 基础框架（txgy-common + 20 个 spring-boot-starter）
├── txgy-gateway/                     # API 网关
├── txgy-server/                      # 服务启动入口
├── txgy-module-*/                    # 业务模块（api + server）
├── txgy-ui-admin-vben/               # 管理后台前端（Vue3 + vben）
├── txgy-ui-admin-uniapp-master/      # 移动端前端（uni-app）
├── txgy-ui-go-view-master/           # 数据大屏前端（go-view）
├── txgy-ui/                          # 其他前端工程说明（vue2/vue3/uniapp/mall）
├── sql/                              # 各模块数据库脚本（00-sql文件）
├── deploy/                           # Docker Compose 部署（backend/frontend/mysql）
├── docs/                             # 项目文档
└── pom.xml                           # 根 POM
```

## 环境要求

- JDK 25（必需）
- Maven 3.9+（建议 3.9.15）
- MySQL 8.x、Redis 7.x、Nacos 3.x
- Node.js 18+ / 22+、pnpm（前端）

> **注意**：如果本机 Maven 全局配置（`conf/settings.xml`）激活了 `jdk-17` profile，会把编译级别强制改为 Java 17，导致 `List.getFirst()` 等 Java 21+ API 编译失败。本项目请使用 JDK 25 编译，构建时可显式停用该 profile：
>
> ```bash
> mvn -P '!jdk-17' -DskipTests clean compile
> ```

## 快速开始

### 方式一：Docker Compose 一键部署

```bash
cd deploy
docker compose up -d
```

该方式会启动 MySQL、Redis、Nacos、网关、各业务服务及前端（前端地址 `http://localhost:5173`，网关 `http://localhost:48080`）。数据库初始化脚本位于 `deploy/mysql`，环境变量见 `deploy/.env.example`。

### 方式二：本地开发

1. 启动依赖中间件：MySQL、Redis、Nacos（默认地址见各服务 `application.yaml` / Nacos 配置）。
2. 初始化数据库：执行 `sql/00-sql文件` 下对应模块的建表与初始化脚本。
3. 编译：

   ```bash
   mvn -P '!jdk-17' -DskipTests clean compile
   ```

4. 启动服务：先在 IDEA 中运行 `TxgyGatewayApplication`，再按需运行各 `TxgyModuleXxxServerApplication`。
5. 启动前端：

   ```bash
   cd txgy-ui-admin-vben
   pnpm install
   pnpm dev
   ```

## 数据库脚本

- `sql/00-sql文件`：各业务模块按日期归档的建表/升级 SQL（部分为 zip 压缩包）。
- `deploy/mysql`：Docker Compose 初始化 MySQL 使用的脚本。
- `sql/mysql`：本地 MySQL 整库备份（不入库）。

## 常用命令

```bash
# 编译（主代码 + 测试代码）
mvn -P '!jdk-17' -DskipTests test-compile

# 打包（跳过测试）
mvn -P '!jdk-17' -DskipTests package

# 运行单元测试
mvn -P '!jdk-17' test
```

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
