# txgycloud 代码地图

本文档基于当前 `master` 分支源码、Maven 聚合结构、前端路由/API 目录、数据库脚本和 Docker Compose 配置整理。它用于快速定位后续开发位置；当前本地 Docker 验证重点是 HRM 人力资源与 BPM 流程审批链路。

## 1. 总体架构

```text
浏览器 / 移动端
        |
        v
Vben Admin (txgy-ui-admin-vben/apps/web-antd)
        |
        | /admin-api/** 或 /app-api/**
        v
Spring Cloud Gateway (gateway-server :48080)
        |
        | Nacos dev / DEFAULT_GROUP 服务发现
        +--> system-server  系统权限、用户、租户、菜单
        +--> infra-server   基础设施、文件、代码生成、日志
        +--> hrm-server     人力资源业务
        +--> bpm-server     Flowable 工作流
        +--> 其它业务服务（完整 Compose 定义，需单独构建镜像）
        |
        +--> MySQL ruoyi-vue-pro（业务表、Flowable 表、初始化菜单）
        +--> Redis（缓存、会话、分布式锁）
        +--> Nacos（配置中心和注册中心）
```

后端服务采用同一套运行时配置：容器内通过 `mysql:3306`、`redis:6379`、`nacos:8848` 访问基础设施，各业务服务在本地 profile 下注册到 Nacos 的 `dev` 命名空间和 `DEFAULT_GROUP`。每个服务的容器端口都为 48080，由网关通过服务名转发；对外只映射网关 48080 和前端 5173。

## 2. Maven 代码分层

根聚合文件为 `pom.xml`，Java 25、Spring Boot 4.1.0。主要层次如下：

| 层次 | 目录 | 职责 |
| --- | --- | --- |
| 依赖与基础框架 | `txgy-dependencies`、`txgy-framework` | 统一依赖版本；Web、Security、RPC、MyBatis-Plus、Redis、MQ、租户、数据权限、Excel、监控等 starter |
| 网关 | `txgy-gateway` | Spring Cloud Gateway 路由、鉴权过滤、灰度/负载均衡、跨域和 API 文档转发 |
| 启动聚合 | `txgy-server` | 将后端公共启动依赖和配置聚合，供各服务复用 |
| 业务模块 | `txgy-module-*/` | 每个模块按 `*-api` 和 `*-server` 分层；API 放 RPC 契约/枚举/DTO，server 放 Controller、Service、Mapper、DO 和配置 |
| 客户端 | `txgy-ui-admin-vben`、`txgy-ui-admin-uniapp-master`、`txgy-mall-uniapp-master`、`txgy-ui-go-view-master` | 管理后台、移动端/商城端和数据大屏 |

业务 server 的典型调用链为：

```text
Controller (VO 校验/权限)
  -> Service (事务、领域规则、状态变更)
    -> Mapper (MyBatis-Plus / XML 查询)
      -> DO (dal/dataobject)
        -> MySQL
```

跨模块调用通过 `*-api` 暴露的接口和 DTO 完成，通常由 `txgy-spring-boot-starter-rpc`/OpenFeign 发现 Nacos 服务。缓存、幂等、锁和消息等横切能力由 `txgy-framework` starter 提供。

## 3. 全量业务模块

下表是根 `pom.xml` 中定义的业务范围。`server` 是可部署服务；商城模块内部拆成四个服务。

| 模块 | 部署服务名/入口 | 业务职责 | 前端入口 |
| --- | --- | --- | --- |
| System | `system-server` | 登录认证、用户/部门/岗位/角色、菜单权限、租户、字典、通知、登录日志 | `src/router/routes/modules/system.ts` |
| Infra | `infra-server` | 文件存储、参数配置、代码生成、定时任务、API 错误/访问日志、数据字典支撑 | `infra.ts` |
| Member | `member-server` | 会员、等级、积分、标签、收货地址等会员中心能力 | `member.ts` |
| BPM | `bpm-server` | Flowable 流程模型/定义/表单、实例、任务、审批评论、监听器、用户组、OA 请假 | `bpm.ts`、`leave.ts` |
| Pay | `pay-server` | 支付渠道、订单、退款、回调和支付统计 | `pay.ts` |
| Report | `report-server` | 报表和积木报表集成 | `report.ts`（部分前端项目） |
| MP | `mp-server` | 微信公众号/模板消息/素材/自动回复等公众号运营 | `mp.ts` |
| Mall Product | `product-server` | 商品、SKU、分类、品牌、运费模板和商品管理 | `mall.ts` |
| Mall Promotion | `promotion-server` | 优惠券、秒杀、砍价、拼团、积分商城等营销活动 | `mall.ts` |
| Mall Trade | `trade-server` | 购物车、交易订单、售后、配送和结算 | `mall.ts` |
| Mall Statistics | `statistics-server` | 交易、商品、会员和营销数据统计 | `mall.ts` |
| CRM | `crm-server` | 客户、联系人、商机、合同、回款、跟进和 CRM 配置 | `crm.ts` |
| ERP | `erp-server` | 采购、销售、库存、产品、仓库和供应商等企业资源流程 | `erp.ts` |
| IoT | `iot-server` + `iot-gateway` | 设备、产品、物模型、规则、告警和设备接入；`iot-core` 是共享核心 | `iot.ts` |
| MES | `mes-server` | 制造执行、生产计划/工单、工艺、报工和生产追踪 | `mes.ts` |
| WMS | `wms-server` | 仓储、入库/出库、库存、盘点和库位 | `wms.ts` |
| IM | `im-server` | 即时通讯、会话、消息和在线状态 | `im.ts` |
| AI | `ai-server` | AI 对话、模型、知识库/检索和 MCP/SSE 相关能力 | `ai.ts` |
| HRM | `hrm-server` | 员工档案、招聘、考勤、绩效、薪酬、社保、公积金、员工门户和 HR 首页 | `hrm.ts` |
| FMS | `fms-server` | 财务/资金相关基础业务（以当前 FMS 源码和 SQL 为准） | `fms.ts` |

公共工程目录 `txgy-framework`、`txgy-dependencies` 和网关不是业务模块，但所有服务都会依赖其中的基础能力。

## 4. 前端地图

主管理后台是 `txgy-ui-admin-vben/apps/web-antd`：

```text
src/router/routes/modules/*.ts  -> 页面路由和权限标识
src/api/<module>/                -> Axios API 封装、请求/响应类型
src/views/<module>/              -> 页面、表格、表单和详情组件
src/store/                       -> 用户、权限、字典、租户等状态
src/                               -> Vben/Vue 入口、布局、通用组件和配置
```

当前仓库同时保留 `web-antd`、`web-antdv-next`、`web-ele`、`web-naive`、`web-tdesign` 多套 Vben 应用，以及 `txgy-ui-admin-uniapp-master`、`txgy-mall-uniapp-master` 移动端和 `txgy-ui-go-view-master` 大屏。后续 HRM 管理端修改优先落在 `web-antd` 对应的 `src/api/hrm`、`src/views/hrm` 和 `src/router/routes/modules/hrm.ts`。

网关路由集中在 `txgy-gateway/src/main/resources/application.yaml`，例如：

```text
/admin-api/hrm/**  -> lb://hrm-server
/admin-api/bpm/**  -> lb://bpm-server
/admin-api/system/** -> lb://system-server
/admin-api/infra/**  -> lb://infra-server
```

每个模块的 `v3/api-docs` 路由被重写到服务内的 `/v3/api-docs`，因此可以通过网关检查某个服务是否已经注册和可达。

## 5. HRM 人力资源代码地图

HRM 服务入口：

`txgy-module-hrm/txgy-module-hrm-server/src/main/java/cn/iocoder/txgy/module/hrm/HrmServerApplication.java`

### 5.1 业务分区

| 分区 | Controller/Service 目录 | 主要内容 |
| --- | --- | --- |
| 员工档案 | `controller/admin/employee`、`service/employee` | 基本档案、联系方式、教育/工作经历、证书、合同、培训、离职、工资卡、字段配置和变更记录 |
| 招聘 | `controller/admin/recruit`、`service/recruit` | 招聘岗位/类型、候选人、渠道、面试和招聘配置 |
| 考勤 | `controller/admin/attendance`、`service/attendance` | 打卡、考勤组、节假日、请假和考勤统计 |
| 绩效 | `controller/admin/performance`、`service/performance` | 绩效模板、计划、考核和结果模板 |
| 薪酬 | `controller/admin/salary`、`service/salary` | 薪资配置/分组、月度核算、员工薪资、薪资单、税率、变更和发放记录 |
| 社保/公积金 | `controller/admin/insurance`、`service/insurance` | 参保员工、方案、标准、月度记录和员工明细 |
| 员工门户 | `controller/admin/portal`、`service/portal` | 员工自助查看档案、考勤、请假、绩效、社保和工资单 |
| 首页与日志 | `controller/admin/home`、`controller/admin/operatelog` | HR 首页统计和 HR 操作日志 |

典型 HRM 持久化路径：`dal/dataobject/<domain>` 定义 DO，`dal/mysql/<domain>` 定义 Mapper，查询和更新由对应 Service 事务编排。SQL 初始化来源为 `sql/00-sql文件/hrm-2026-08-04.sql`，菜单补充为 `sql/hrm_menu.sql`；Docker 首次初始化时由 `deploy/mysql/init/` 中按编号脚本导入。

### 5.2 HRM 与 BPM 的请假链路

```text
HrmAttendanceLeaveController
  -> HrmAttendanceLeaveService
    -> BpmProcessInstanceApi (OpenFeign/RPC)
      -> bpm-server / Flowable RuntimeService
    -> HrmAttendanceLeaveMapper 更新 process_instance_id / approval_status

Flowable 审批事件
  -> BPM 事件监听/回调
    -> HRM AttendanceLeaveService
      -> 按实例 ID 更新请假单为 APPROVE/REJECT/CANCEL
```

HRM 的 `framework/rpc/config/RpcConfiguration.java` 显式启用 `BpmProcessInstanceApi`，同时依赖系统用户、部门、操作日志和通知 API。请假状态使用 BPM 枚举，数据库记录保存流程实例 ID，因此排查请假问题时要同时检查 HRM 表和 BPM Flowable 表/实例。

## 6. BPM 工作流代码地图

BPM 服务入口：

`txgy-module-bpm/txgy-module-bpm-server/src/main/java/cn/iocoder/txgy/module/bpm/BpmServerApplication.java`

| 分区 | 位置 | 内容 |
| --- | --- | --- |
| 流程定义 | `controller/admin/definition`、`service/definition` | 分类、表单、模型、流程定义、表达式、监听器、用户组 |
| 流程任务 | `controller/admin/task`、`service/task` | 发起、取消、审批、转办、抄送、待办/已办和实例详情 |
| 评论 | `controller/admin/comment`、`service/comment` | 审批意见和流程评论 |
| OA | `controller/admin/oa`、`service/oa` | OA 请假示例及其表单/流程绑定 |
| Flowable 集成 | `framework/flowable` | 引擎配置、候选人策略、监听器、事件发布、表达式和任务分配 |

BPM API 契约在 `txgy-module-bpm/txgy-module-bpm-api`，其中 `BpmProcessInstanceApi` 被 HRM server 作为远程依赖使用。Flowable 的数据库结构由 BPM 配置 `flowable.database-schema-update: true` 管理，初始化业务菜单和历史变更仍来自 `deploy/mysql/init/` 的 BPM SQL。

## 7. 数据与配置流向

1. 前端登录后把 `/admin-api/**` 请求发到网关；网关执行过滤器和鉴权，再依据静态路由与 Nacos 服务发现转发。
2. 服务启动时先读取 `application.yaml`，再加载本地 profile 和可选的 Nacos 配置 `${spring.application.name}-${spring.profiles.active}.yaml`。
3. Controller 完成参数校验、租户/权限注解和 VO 转换，Service 负责事务和业务状态，Mapper 访问 `ruoyi-vue-pro` 数据库。
4. Redis 承担缓存、登录会话、分布式锁等高频共享状态；消息和定时任务能力由 framework starter 按模块启用。
5. HRM 请假等需要审批的业务通过 BPM RPC 创建或取消流程；BPM 事件再回写 HRM 业务状态。

## 8. Docker 本地部署拓扑

基础 Compose 文件：`deploy/docker-compose.yml`；本地覆盖：`deploy/docker-compose.local.yml`。本机当前验证集合为：

```text
mysql、redis、nacos、system、infra、hrm、bpm、gateway、frontend
```

访问入口：

```text
前端：http://localhost:5173
网关：http://localhost:48080
Nacos：http://localhost:8848/nacos
```

网关健康检查为 `/actuator/health`；当前 Nacos `dev/DEFAULT_GROUP` 已确认 `gateway-server`、`system-server`、`infra-server`、`hrm-server`、`bpm-server` 均有 healthy/enabled 实例，且网关 HRM/BPM OpenAPI 文档返回 HTTP 200。

完整 Compose 还定义了 Member、Pay、Report、MP、商城四服务、CRM、ERP、IoT、MES、WMS、IM、AI、FMS 等服务。它们属于源码的全量业务范围，但本轮没有在本机从零构建并启动：前端依赖下载在约 1,700 个包处发生 npm 网络超时，因而不能把“当前 HRM/BPM 最小集合已验证”表述为“所有全量服务已部署”。

## 9. 常见修改定位

| 修改目标 | 首选位置 | 同步检查 |
| --- | --- | --- |
| HRM 员工字段/档案 | `txgy-module-hrm/.../employee` 的 Controller、Service、DO、Mapper | HRM SQL、`src/api/hrm`、`src/views/hrm`、菜单权限 |
| HRM 请假审批 | `.../attendance/record` 与 `framework/rpc/config/RpcConfiguration.java` | BPM API、流程定义/表单、Flowable 状态回写、请假单 SQL |
| HRM 薪资/社保 | `.../salary`、`.../insurance` | 月度记录和员工明细表、门户查询、导入导出 VO |
| BPM 审批节点 | `txgy-module-bpm/.../definition`、`framework/flowable` | Flowable 表、候选人策略、前端 `api/bpm` 和 `bpm.ts` |
| 新增外部 API | 模块 `*-api` 定义契约，server 的 `api` 实现或 Controller 暴露 | Nacos 服务名、网关路由、Feign 客户端和权限注解 |
| 初始化/升级数据 | `deploy/mysql/init/` 按编号新增脚本 | 只影响本地前先备份卷，核对幂等性和目标表 |
| 网关路由 | `txgy-gateway/src/main/resources/application.yaml` | 目标服务 Nacos 名称、OpenAPI 重写、鉴权和跨域 |

## 10. 开发与上传闸门

本地工作约束：修改仅在本地仓库进行；每次修改先运行针对性测试和 Docker/HTTP 验证；未经用户明确确认不得 `git push`。获得确认后，上传前必须再次执行 `git pull --ff-only`（或处理明确冲突）、重建/重启目标服务并复验，然后才能提交和推送。
