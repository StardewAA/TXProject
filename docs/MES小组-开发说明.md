# MES 小组开发说明

> 适用范围：天信管业防腐保温智慧平台 —— MES 小组负责模块
> 文档版本：2026-09-08（一组一份）
> 小组负责范围：MES 生产制造域（生产/质量/仓储/设备/排产/主数据/安全环保/工具/首页）+ AI 智能助手

---

## 一、技术架构

### 1.1 总体架构

平台采用 **Spring Cloud 微服务架构**，按业务域拆分服务，服务间通过 OpenFeign RPC 通信，前端为独立 SPA 应用经网关统一接入。

```
浏览器（Vue3 SPA，localhost:5173 / Nginx 容器）
        │  /admin-api/**  /app-api/**
        ▼
Gateway 网关（txgy-gateway，路由 + 鉴权 + 灰度负载均衡）
        │  按服务名 grayLb:// 负载均衡
        ▼
┌────────────────────────────────────────────────────┐
│ system  system-server   用户/租户/角色/菜单/字典      │
│ infra    infra-server    文件/代码生成/配置/监控       │
│ mes      mes-server      ★ MES 生产制造域（本组核心） │
│ ai       ai-server       AI 对话/模型/工具（本组集成） │
│ bpm / erp / wms / fms / im / hrm / report / iot …   │
└────────────────────────────────────────────────────┘
        │  MyBatis-Plus（自动租户过滤）
        ▼
MySQL(ruoyi-vue-pro 库)   Redis(缓存/会话)   Nacos(注册+配置中心)
```

- **注册/配置中心**：Nacos（namespace: dev），服务发现 + `application-{profile}.yaml` 远程配置
- **鉴权**：登录后颁发 accessToken（Redis 存储），网关透传，各服务 `TokenAuthenticationFilter` 校验；**多租户**贯穿全链路（请求头 tenant-id，MyBatis 租户插件自动过滤数据）
- **部署**：Docker Compose（deploy/docker-compose.yml），每个后端服务一个容器（deploy/backend/Dockerfile 按 MODULE_PATH 参数化构建），前端 Nginx 容器

### 1.2 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 25、Spring Boot、Spring Cloud、Spring AI（豆包/火山方舟）、MyBatis-Plus、Druid、Redisson |
| 前端 | Vue 3 + TypeScript、Vben Admin（Ant Design Vue）、Tailwind CSS 4、Vite + Turborepo（pnpm monorepo） |
| 数据 | MySQL 8.4（库：ruoyi-vue-pro）、Redis 7 |
| 中间件 | Nacos 3、XXL-Job（定时任务）、WebSocket（infra） |
| AI | Spring AI Function Calling、豆包 doubao-seed-2-1-turbo（OpenAI 兼容协议）、SSE 流式 |
| 工程 | Maven 多模块、Docker Compose、Gitee MR 工作流 |

### 1.3 MES 服务模块划分（txgy-module-mes）

```
txgy-module-mes/
├── txgy-module-mes-api        # 对外 RPC 接口 + DTO（供 ai/system 等服务 Feign 调用）
│   └── cn.iocoder.txgy.module.mes.api.*     # 如 MesProWorkOrderApi
└── txgy-module-mes-server     # 服务实现
    └── controller.admin       # 按业务域分包（下表），每域内 xxx/vo 子包
        ├── cal   排产日历（8 个 Controller）
        ├── dv    设备管理（12）
        ├── home  首页统计（1）
        ├── md    主数据（17）
        ├── pro   生产管理（18）★
        ├── qc    质量管理（16）
        ├── set   安全环保（2）
        ├── tm    工具管理（2）
        └── wm    仓储管理（55）★ 最大业务域
```

代码分层：`Controller（参数校验/VO）→ Service（业务）→ Mapper（MyBatis-Plus）→ DO（表实体）`；对外暴露的数据能力在 `api` 模块定义 `*Api` 接口 + `*RespDTO`，由 server 实现（如 AI 服务的查询工具即通过 `MesProWorkOrderApi` RPC 取数）。

### 1.4 AI 集成架构（跨模块）

```
前端悬浮助手（roleId=20）
   │ SSE POST /admin-api/ai/chat/message/send-stream
   ▼
ai-server  AiChatMessageServiceImpl
   │ Spring AI ChatModel（豆包，模型配置存 ai_model 表）
   │ + Function Calling 工具（角色 toolIds 绑定）
   ▼
4 个 MES 查询工具（ai-server/tool/function/mes/）
   │  mes_work_order_query / mes_task_query /
   │  mes_feedback_query / mes_qc_ipqc_query
   │  每次调用：ToolContext 取租户 → TenantUtils.execute(租户上下文)
   ▼
OpenFeign RPC ──▶ mes-server（*Api 接口）──▶ MySQL（租户隔离查询）
```

- 模型自主决定调用哪个工具；工具返回 JSON 数据后由模型组织中文回答
- 多租户安全：工具执行强绑定发起者的租户 ID，跨租户数据不可见
- 前端面板：悬浮球 + 可拖拽面板（位置 localStorage 持久化），深色标准对话框样式

---

## 二、业务模块说明

> 以下按 mes-server 的业务域分包说明。所有接口前缀 `/admin-api/mes/**`。

### 2.1 生产管理（pro）—— 核心业务域

| 子模块 | 说明 | 关键能力 |
|---|---|---|
| **workorder 工单** | 生产工单全生命周期 | 创建→确认→生产→完成/取消；支持按编号/编码/名称/状态/时间分页查询 |
| **task 任务** | 工单拆分的生产任务 | 任务分配、进度跟踪 |
| **feedback 报工** | 工人报工记录 | 报工数量/工时/不良品上报 |
| **process 工序 / route 工艺路线** | 工艺定义 | 工序编排、路线配置 |
| **andon 安灯** | 安灯呼叫与响应 | 异常呼叫、响应统计（首页看板数据源之一） |
| **card 生产卡** | 生产卡片管理 | — |
| **workrecord 生产记录** | 生产过程记录 | — |
| **project 项目管理**（P0 新增） | 项目维度的生产管理增强 | 成员/工期/优先级/负责人/状态机/Excel 导入（菜单 id 6750，权限 mes:pro-project:*） |

数据表前缀 `mes_pro_*`（mes_pro_work_order、mes_pro_task、mes_pro_project 等）。

### 2.2 质量管理（qc）

覆盖来料→制程→出货全链路质检：

- **iqc** 来料检验 / **ipqc** 制程检验（AI 助手支持查询）/ **oqc** 出货检验 / **rqc** 退货检验
- **pendinginspect** 待检任务池、**defect / defectrecord** 缺陷与缺陷记录
- **indicator / indicatorresult** 质检指标与结果、**template** 质检模板

数据表前缀 `mes_qc_*`。

### 2.3 仓储管理（wm）—— 最大业务域（22 个子模块、55 个 Controller）

- **入库线**：arrivalnotice 到货通知 → productreceipt 成品入库 / itemreceipt 采购入库 / outsourcereceipt 外协入库
- **出库线**：productissue 生产领料 / miscissue 杂发 / outsourceissue 外协领料 / returnsales 销售退货出库 / returnissue 退料 / returnvendor 采购退货
- **销售**：productsales 成品销售、salesnotice 销售通知
- **库存**：materialstock 库存、stocktaking 盘点、transfer 移库/调拨、batch 批次、sn 序列号
- **追溯与辅助**：barcode 条码、itemconsume 物料消耗、packages 包装、warehouse 仓库档案

数据表前缀 `mes_wm_*`。ERP 审批流（审批管理菜单，id 6761）与 WMS 单据审批按钮（其它入/出库、盘点、采购、销售等）挂接在工作流模块。

### 2.4 设备管理（dv）

machinery 设备台账、checkplan/checkrecord 点检计划与记录、maintenrecord 保养记录、repair 维修、subject 保养科目。支撑首页「设备总数/生产中/维修中/停机」统计。

### 2.5 排产日历（cal）

calendar 工厂日历、holiday 假期、plan 排班计划、team 班组。

### 2.6 主数据（md）

item 物料、client 客户、vendor 供应商、workstation 工位、unitmeasure 计量单位、autocode 自动编码规则。为生产/仓储/质检提供基础档案。

### 2.7 安全环保（set）—— 2026-09 新增（P0/P2）

- **pollutioncheck 污染判定**：四个环节（采购入库/生产领用/中间废弃物/成品）的环保污染判定；AI 初筛（CLEAN/POLLUTED/UNCERTAIN 三值）+ 人工复核收口（二值）；表 `mes_set_pollution_check`
- **pollutionledger 污染/危废暂存台账**：`mes_pollution_ledger`，批次环保戳（`mes_wm_batch` 4 列）触发门禁
- **全链路审计**：判定履历 `mes_pollution_check_log`、台账流转 `mes_pollution_ledger_log`

### 2.8 首页统计（home）

`/mes/home-statistics/summary` 聚合：设备状态（总数/生产/维修/停机）、安灯活跃数、维修单活跃数等，供 MES 首页 KPI 卡片与图表。

### 2.9 AI 智能助手（跨模块，AI 板块负责）

- **前端**：MES 首页右下角悬浮球 → 560px 可拖拽面板（深色标准对话框），位置记忆
- **后端**：ai-server 4 个 MES 查询工具（工单/任务/报工/IPQC），Function Calling + OpenFeign RPC，租户隔离
- **角色**：ai_chat_role id=20「MES 生产助手」（含角色 Prompt 与工具绑定），模型为豆包（ai_model id=65）
- **接口**：`/admin-api/ai/chat/message/send-stream`（SSE 流式）

---

## 三、产品操作说明

### 3.1 登录

1. 浏览器打开平台地址（本地 `http://localhost:5173`）
2. 选择**租户**（数据按租户隔离，选错租户看不到业务数据）
3. 输入账号密码登录（本地默认 admin / admin123，租户 1）

### 3.2 菜单导航总览

| 菜单位置 | 内容 |
|---|---|
| MES 系统 → 生产管理 | 项目管理（6750）等生产域菜单 |
| MES 系统 → 安全环保检测 | 污染判定（34301）、污染暂存台账（34302） |
| MES 系统（其余子菜单） | 工单、任务、报工、质检、仓储、设备、日历等 |
| ERP 系统 → 审批管理 | ERP 单据审批（6761） |
| CRM 系统 | 线索管理（含 AI 线索识别）、客户、商机、合同、回款 |
| 商城系统 | 商品/交易/促销/统计（依赖 mall 服务运行） |

### 3.3 生产管理操作

**项目管理**（MES 系统 → 生产管理 → 项目管理）：
1. 「新增」创建项目：填写名称、优先级（紧急/高/中/低）、负责人、责任部门、计划起止时间
2. 项目详情内管理**项目成员**与状态流转（状态机控制）
3. 支持 Excel 批量导入（需「项目管理导入」权限）
4. 支持按编码/名称/状态筛选与导出

**工单操作**（典型流程）：创建工单 → 确认 → 生产（车间报工）→ 完工入库 → 工单完成。列表页支持多条件筛选与分页。

### 3.4 质检操作

按检验阶段进入对应菜单（IQC/IPQC/OQC/RQC）→ 新建检验单（选择模板与指标）→ 录入检验结果 → 不合格品走缺陷记录。IPQC 数据同时供 AI 助手查询。

### 3.5 安全环保检测操作（污染判定流程）

1. 进入「MES 系统 → 安全环保检测 → 污染判定」
2. 系统对采购入库/生产领用/中间废弃物/成品四环节单据做 **AI 初筛**（结果：CLEAN 干净 / POLLUTED 污染 / UNCERTAIN 待定）
3. 人工**复核**确认最终二值（CLEAN / POLLUTED）——复核是收口动作，每次判定/复核均写入履历可追溯
4. 判定为污染的批次自动打环保戳并进入「污染暂存台账」，未处置完成会触发流转门禁
5. 台账支持登记、处置中、终态、自动解除（CLEARED）的状态流转，全程留痕

### 3.6 AI 智能助手使用

1. MES 首页**右下角悬浮球**（蓝色圆钮）点击展开对话面板
2. **拖拽**：按住标题栏可移动面板（自动限制在屏幕内）；位置自动记忆，下次打开保持
3. **提问示例**：
   - 「查询最近的生产工单」
   - 「工单 WO-20260902-001 的详情」
   - 「查一下 IPQC 检验记录」
4. **收起/关闭**：标题栏右侧 × 收起为悬浮球；输入区左下角可「新对话」「完整对话」
5. 注意：回答基于**当前登录租户**的数据；AI 只做查询，不做任何写操作

### 3.7 常见问题（FAQ）

| 现象 | 原因与处置 |
|---|---|
| 页面菜单缺失 | 菜单未授权给当前角色：系统管理→角色管理→勾选菜单；或数据库未执行对应升级 SQL |
| 接口 502/501/404 | 网关与容器网络异常：`docker restart deploy-frontend-1`；仍不行整栈 down/up（compose） |
| AI 回复长时间"思考中" | 推理模型思考链阶段属正常；超过 2 分钟检查 ai 服务日志与模型 API Key |
| AI 查不到业务数据 | 确认登录租户与数据所属租户一致（数据按租户隔离） |
| 本机 3306 被占 | 本机 MySQL 服务占用，容器库映射在 **13306** |
