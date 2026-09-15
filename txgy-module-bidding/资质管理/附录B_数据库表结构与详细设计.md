# 附录 B：数据库表结构与详细设计（v2.0 完整表格）

> 来源：《电子证照与模板管理库_设计方案_完善版.md》(v2.0) 全部表格与代码，叙述已删。

# 天信管业防腐保温智慧平台 — 电子证照管理库与模板管理库设计方案

| 项目 | 内容 |
| --- | --- |
| 所属系统 | 天信管业防腐保温智慧平台（yudao 微服务架构二次开发，基础包名 `cn.iocoder.txgy`） |
| 所属模块 | 招投标管理（新增微服务模块 `txgy-module-bid`，服务名 `bid-server`） |
| 功能范围 | 电子证照管理库、模板管理库、业绩证明库（企业管理员端） |
| 文档版本 | v2.0（设计完善版） |
| 上游文档 | v1.1《电子证照管理库与模板管理库设计报告》 |
| 编写日期 | 2026-09-07 |
| 当前状态 | v1.1 功能已实现并编译通过；本版补齐数据模型缺口、文件安全、到期提醒与验证口径，整改项见 §14 |

## 修订说明（v1.1 → v2.0）

1. **修正到期状态腐化缺陷** — 原设计声称"查询时实时计算"，但 `status` 字段落库且只在新增/更新时写入，导致无人编辑的证照状态永久失真。本版改为"落库 + 每日定时刷新 + 查询时重算覆盖"三重保障（§7）。
2. **附件由单字段改为子表** — 原 `file_url varchar(512)` 单字段只能存一份附件，无法满足"照片和原文件都要存进去"的要求。本版新增 `bid_certificate_file` 附件子表，支持一证多件、分类型、可排序（§4.4）。
3. **补齐业绩证明数据模型** — 需求明确要求 AI 从证照库调取"企业资质、人员证书、**业绩证明**"填充标书，v1.1 完全缺失。本版新增 `bid_performance` 表（§4.6）。
4. **新增文件存储与访问控制设计** — v1.1 走 `/infra/file/upload` 返回公开 URL，企业资质扫描件存在任意下载风险。本版改为受控下载接口 + 权限校验 + 审计日志（§6）。
5. **新增证照变更记录** — 支持换证、资质升级、年审等高频动作的历史留痕，保留"投标时点有效的资质版本"（§4.5）。
6. **新增关键设计决策与取舍章节** — 补充方案选型理由与备选方案对比，回应"有什么设计方案"的原始提问（§5）。
7. **修正验证情况口径** — v1.1 将"网关返回 401 账号未登录"标记为链路验证通过，实际无任何接口在登录态跑通。本版如实区分已验证/未验证项，并给出待验收用例清单（§13）。

---

## 目录

1. [需求来源与范围界定](#1-需求来源与范围界定)
2. [需求分析与设计目标](#2-需求分析与设计目标)
3. [总体架构设计](#3-总体架构设计)
4. [数据模型设计](#4-数据模型设计)
5. [关键设计决策与取舍](#5-关键设计决策与取舍)
6. [文件存储与安全设计](#6-文件存储与安全设计)
7. [到期提醒设计](#7-到期提醒设计)
8. [后端设计](#8-后端设计txgy-module-bid)
9. [前端设计](#9-前端设计txgy-ui-admin-vben)
10. [网关与部署配置](#10-网关与部署配置)
11. [系统运行与运维手册](#11-系统运行与运维手册)
12. [页面使用说明](#12-页面使用说明)
13. [验证情况与待验收清单](#13-验证情况与待验收清单)
14. [问题清单与整改计划](#14-问题清单与整改计划)
15. [后续规划](#15-后续规划)

---

## 1. 需求来源与范围界定

### 1.1 原始需求

> 你去看看管理员登录后的**电子证照管理库**已经**模板管理库**的设计。电子证照是包含**企业资质、功能证书、项目承包等级**等很多东西，怎么管理，还有就是这个证照的**照片或者原文件都要存进去**，看看有什么设计方案。

### 1.2 需求条目与本期范围

| 功能点 | 需求原文要点 | 本期范围 | 本版状态 |
| --- | --- | --- | --- |
| 核心 KPI 卡片 | 本年投标总数、中标率、进行中项目数、待处理保证金 | 后续阶段 | ➖ |
| **电子证照管理库** | 证照分类管理、录入与更新、到期提醒、批量操作 | ✅ 本期实现 | v1.1 已实现，本版补强 |
| **模板管理库** | 模板添加/修改/删除、模板分类、批量操作 | ✅ 本期实现 | v1.1 已实现 |
| **业绩证明库** | 投标人端 AI 需调取"业绩证明"填充标书（需求第 12 行） | ✅ 本期建表 + 基础 CRUD | 本版新增 |
| 团队成员管理 | 成员增删改查、权限管理 | 后续阶段 | ➖ |
| 投标人端标书制作 | 导入招标文件 → 智能解析 → 生成标书 → 合规自查 | 后续阶段 | ➖ |

> **范围说明**：投标人端（AI 检索证照库判断缺证照、AI 调取证照/模板/业绩生成标书、合规自查）为后续阶段。但本期的数据模型必须为其预留结构，否则后续阶段需重构表结构。这是本版补齐业绩表和人员关联字段的核心原因。

### 1.3 术语澄清

---

## 2. 需求分析与设计目标

### 2.1 电子证照管理域

| 分类 | 典型证照 | 颁发部门 | 有效期特征 |
| --- | --- | --- | --- |
| **企业资质** | 供热经营许可证 | 当地住建局/城市管理局 | 3-5 年，注明供热区域和面积 |
| | 市政公用工程施工总承包资质（一/二/三级） | 省级住建厅 | 5 年，需年审 |
| | 安全生产许可证 | 省级住建厅 | 3 年 |
| **功能证书** | 特种设备安装改造修理许可证（锅炉） | 市场监管局 | 4 年 |
| | ISO 质量/环境/职业健康体系认证 | 认证机构 | 3 年 |
| | 计量认证、AAA 信用等级 | 相应机构 | 1-3 年 |
| **人员资质** | 注册建造师（市政/机电专业） | 住建部 | 3 年，需继续教育 |
| | 热力工程师、安全员、司炉工证 | 住建部/市场监管局 | 因证而异 |
| **项目承包等级** | 项目承包资质等级证书 | 相应主管部门 | 随资质等级变动 |

1. **有效期差异极大** — 从 1 年到长期有效不等，预警周期不能用单一阈值（供热经营许可证到期影响整个经营资格，需提前 180 天预警；人员证书年审提前 30 天即可）。
2. **持有人分企业与人员** — 人员证书必须可关联到具体人员，才能回答"我们有几个市政专业一级建造师"这类招标响应问题。
3. **存在等级变动** — 二级升一级、旧证换新证是高频动作，直接 update 覆盖会丢失投标时点的资质版本证据。
4. **附件形态多样** — 一张证照通常同时有：证书照片、扫描件 PDF、年审记录、变更记录，单附件字段无法承载。
5. **敏感材料** — 企业资质扫描件含统一社会信用代码、法人信息、资质范围，泄露有商业风险。

### 2.2 管理要求（含本版补强）

| # | 要求 | v1.1 | v2.0 |
| --- | --- | --- | --- |
| 1 | 分类管理，支持自定义 | 单层平铺 3 分类 | 树形分类（`parent_id`），初始 4 个一级分类 |
| 2 | 录入与更新 | 8 个业务字段 | 增加 `validity_type`（区分长期有效/漏填）、`holder_id`、`warn_days` |
| 3 | **照片/原文件存档（硬性要求）** | 单字段 `file_url` | 附件子表，支持一证多件 + 类型区分 + **必填校验** |
| 4 | 到期提醒 | 列表状态标签（有腐化缺陷） | 状态落库 + 每日定时刷新 + 查询重算 + 站内信主动通知 |
| 5 | 批量操作 | 批量删除、导出 Excel | 保留，增加批量导入（P2） |
| 6 | **文件访问控制** | ❌ 未设计（公开 URL） | ✅ 受控下载接口 + 权限校验 + 审计日志 |
| 7 | **换证/升级历史** | ❌ 未设计 | ✅ 变更记录表 |
| 8 | **证照编号唯一性** | ❌ 无约束 | ✅ 唯一索引 + 业务校验 |

### 2.3 模板管理库

| 要求 | 设计 |
| --- | --- |
| 模板分类 | 技术标/商务标（可扩展行业分类），树形结构 |
| 增删改查 | 模板名称、适用行业、项目类型、说明 |
| 文件管理 | Word 模板文件上传存档、受控下载 |
| 批量操作 | 批量删除、导出 Excel |
| **智能推荐预留** | 增加 `industry` + `project_type` 字段作为后续 AI 模板匹配的检索维度 |

### 2.4 设计原则

- **遵循项目规范**：前后端严格对齐 fms/crm 既有模块写法（Controller/Service/DAL 分层、VO 规范、`BaseMapperX`、`useVbenForm`）。
- **租户隔离**：所有业务表带 `tenant_id`，继承 `TenantBaseDO`。
- **逻辑删除**：所有表带 `deleted` 字段，软删除。
- **可追溯**：`creator/create_time/updater/update_time` 审计字段 + 关键业务动作独立留痕。
- **面向下游设计**：数据模型为投标人端 AI 检索预留结构，避免后续重构。
- **安全优先**：敏感附件不走公开 URL。

---

## 3. 总体架构设计

### 3.1 架构定位

```
┌──────────────────────────────────────────────────────────┐
│  前端 txgy-ui-admin-vben (Vue3 + Vben Admin + Vite)      │
│  apps/web-antd → src/{api,views}/bid                     │
└────────────────────────┬─────────────────────────────────┘
                         │ http://localhost:5666
                         ▼
┌──────────────────────────────────────────────────────────┐
│  网关 txgy-gateway (48080)                               │
│  路由: /admin-api/bid/** → grayLb://bid-server           │
└────────────────────────┬─────────────────────────────────┘
                         ▼
┌──────────────────────────────────────────────────────────┐
│  bid-server (48095)  ← txgy-module-bid                   │
│  controller → service → dal (MyBatis-Plus)               │
│  job（到期状态刷新 / 到期提醒）                            │
└───────┬──────────────┬───────────────┬───────────────────┘
        │              │               │
        ▼              ▼               ▼
   MySQL(3306)    Nacos(8848)     文件存储（infra 文件服务
   ruoyi-vue-pro  namespace=dev    / MinIO 私有桶）
   9 张 bid_* 表  Redis(6379)
                         │
                         ▼
                  system-server (48081)  — 站内信通知（Feign 调用）
                  infra-server  (48082)  — 文件上传
```

### 3.2 依赖与配置

| 项 | 内容 |
| --- | --- |
| 服务名 | `bid-server` |
| 端口 | 48095 |
| 数据库 | MySQL `ruoyi-vue-pro`，连接串与 system/infra 一致 |
| 注册中心 | Nacos `127.0.0.1:8848`，namespace `dev` |
| 网关路由 | `bid-admin-api`：`Path=/admin-api/bid/**` → `grayLb://bid-server` |
| 关键依赖 | starter-env / security / mybatis / rpc / excel / monitor / **websocket**（启动必需，见 §14 记录）/ **job**（定时任务，本版新增） |

---

## 4. 数据模型设计

### 4.1 实体关系

```
bid_certificate_category (树形分类)
        │ 1
        │
        │ N
bid_certificate (证照主表) ────┬──── N ──── bid_certificate_file (附件子表)
        │                     │
        │ 1                   │ 1
        │                     │
        │ N                   │ N
        │              bid_certificate_change_log (变更记录)
        │
        └── holder_id ──→ (预留) 人员表

bid_performance (业绩证明) ──── N ──── bid_certificate_file (复用附件表，biz_type 区分)

bid_template_category (树形分类)
        │ 1
        │ N
bid_template (标书模板)
```

### 4.2 表清单（库：`ruoyi-vue-pro`）

| # | 表名 | 说明 | v1.1 | v2.0 变化 |
| --- | --- | --- | --- | --- |
| 1 | `bid_certificate_category` | 电子证照分类 | ✅ | 增加 `parent_id`、`default_warn_days` |
| 2 | `bid_certificate` | 电子证照主表 | ✅ | 增加 `validity_type`、`holder_id`、`warn_days`；`code` 加唯一约束；移除 `file_url`/`file_name` |
| 3 | `bid_certificate_file` | **证照/业绩附件子表** | ❌ | **本版新增** |
| 4 | `bid_certificate_change_log` | **证照变更记录** | ❌ | **本版新增** |
| 5 | `bid_performance` | **业绩证明** | ❌ | **本版新增** |
| 6 | `bid_template_category` | 标书模板分类 | ✅ | 增加 `parent_id` |
| 7 | `bid_template` | 标书模板 | ✅ | 附件迁移至 `bid_certificate_file`（统一附件表） |
| 8 | `bid_config` | **业务参数配置**（预警阈值等） | ❌ | **本版新增** |

> 所有表遵循项目规范：包含 `creator` / `create_time` / `updater` / `update_time` / `deleted` / `tenant_id` 公共字段。

### 4.3 电子证照分类表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| **parent_id** | bigint DEFAULT 0 | **父分类编号（0 为一级），本版新增，支持树形** |
| name | varchar(100) NOT NULL | 分类名称 |
| **default_warn_days** | int DEFAULT NULL | **该分类默认预警天数，本版新增（空则用全局配置）** |
| sort | int DEFAULT 0 | 排序 |
| status | tinyint DEFAULT 0 | 状态（0 正常 1 停用） |
| remark | varchar(500) | 备注 |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

### 4.4 电子证照主表（核心）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| category_id | bigint NOT NULL | 证照分类编号 |
| name | varchar(200) NOT NULL | 证照名称（如：市政公用工程施工总承包资质） |
| code | varchar(100) | 证照编号 |
| holder_type | tinyint DEFAULT 1 | 持有人类型（1 企业 2 人员） |
| holder_name | varchar(100) | 持有人名称（企业名称/人员姓名） |
| **holder_id** | bigint DEFAULT NULL | **持有人 ID（人员证书关联人员表，本版新增，当前预留）** |
| level | varchar(50) | 资质等级（自由文本，须同时容纳"特级/一级/二级/三级"与"综合/甲级/乙级"两套口径，见下方说明） |
| **level_standard** | tinyint DEFAULT 1 | **等级所属标准版本（1 建市〔2014〕159号 2 资质改革后），本版新增** |
| **business_scope** | varchar(1000) | **许可范围/承包范围（供热区域与面积、锅炉许可参数 A级≥3.8MPa/B级<3.8MPa、合同额上限等），本版新增** |
| **major** | varchar(100) | **专业（如市政/机电，本版新增，人员证书检索必需）** |
| **cert_sub_type** | varchar(50) | **证书细分类型（C1/C2/C3、G1/G2/G3、RT/UT Ⅱ级等），本版新增** |
| **registration_no** | varchar(100) | **注册号/执业印章号（注册建造师、特种设备作业人员证），本版新增** |
| **register_unit** | varchar(200) | **注册单位（与实际聘用企业比对可识别挂靠风险），本版新增** |
| **risk_flag** | tinyint DEFAULT 0 | **风险标记（0 正常 1 黄色预警 2 红色预警 3 失效预警 4 异常标注），本版新增，承载非日期型失效状态** |
| **verify_platform** | varchar(50) | **官方查验平台标识（jzsc/zlaq/cnse/permit/cnca/coc/sxdzzs），本版新增** |
| **biz_status** | tinyint DEFAULT 0 | **业务状态（0 未发起 1 个人已申请 2 待企业确认 3 已行政许可 4 已发证），人员证书专用，承载"待企业确认"中间态（P2-9 落库，2026-09-07 综合评审补入）** |
| **annual_review_date** | date | **年度审核/复审日期（ISO体系认证年审、特种设备作业人员证复审），本版新增，与 expire_date 是两个独立时间维度** |
| issuer | varchar(200) | 发证机关 |
| issue_date | date | 发证日期 |
| **validity_type** | tinyint DEFAULT 2 | **有效期类型（1 长期有效 2 有固定期限 3 待确认），本版新增** |
| expire_date | date | 到期日期（`validity_type=2` 时必填） |
| **warn_days** | int DEFAULT NULL | **单证照自定义预警天数（空则取分类默认，再空取全局），本版新增** |
| status | tinyint DEFAULT 0 | 状态（0 有效 1 即将到期 2 已过期 3 已作废），由定时任务刷新 |
| **file_count** | int DEFAULT 0 | **附件数量（冗余字段，列表展示用），本版新增** |
| remark | varchar(500) | 备注 |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_code (code, tenant_id, deleted)` — **防止同一证照重复录入（本版新增）**
- `KEY idx_category_id (category_id)`
- `KEY idx_expire_date (expire_date)` — 到期提醒扫描
- `KEY idx_status (status)` — 列表按状态过滤
- `KEY idx_holder (holder_type, holder_id)`

- `validity_type = 2` 时 `expire_date` 不能为空（错误码 `CERTIFICATE_EXPIRE_DATE_REQUIRED`）。
- `holder_type = 2`（人员）时 `holder_name` 必填。
- 新增时 `code` 若已存在则报 `CERTIFICATE_CODE_DUPLICATE`（不静默覆盖）。
- **附件至少 1 个**（落实"照片或原文件都要存进去"的硬性要求，错误码 `CERTIFICATE_FILE_REQUIRED`）。

### 4.5 附件子表（本版新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| **biz_type** | tinyint NOT NULL | 业务类型（1 电子证照 2 标书模板 3 业绩证明） |
| **biz_id** | bigint NOT NULL | 业务主键（对应证照/模板/业绩的 id） |
| file_type | tinyint DEFAULT 1 | 文件类型（1 证照照片 2 扫描件 PDF 3 原件 4 年审记录 5 变更证明 9 其他） |
| file_name | varchar(255) NOT NULL | 原始文件名 |
| **file_key** | varchar(512) NOT NULL | **文件存储键（私有路径，不直接暴露 URL），本版安全设计核心** |
| file_ext | varchar(20) | 扩展名（jpg/png/pdf/docx 等） |
| file_size | bigint | 文件大小（字节） |
| **file_hash** | varchar(64) | **文件 SHA256 哈希（上传后计算存库，下载/核验时比对防篡改；投标关键证据完整性校验，2026-09-07 综合评审补入）** |
| sort | int DEFAULT 0 | 排序（0 为封面/主附件） |
| remark | varchar(500) | 备注 |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

> **⚠️ ofd 为硬性要求**：陕西省住建领域企业资质电子证书（安全生产许可证、建筑业企业资质、工程勘察/设计/监理资质等）**以加盖"陕西省住房和城乡建设厅"电子签章的 OFD 格式文件为载体**，A4 竖版。白名单不含 ofd 会导致企业无法上传本省核发的电子资质证书。前端需提供 OFD 预览方案（ofd.js 等开源库，或提示用户下载后本地打开）。

### 4.6 证照变更记录表（本版新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| certificate_id | bigint NOT NULL | 证照编号 |
| change_type | tinyint NOT NULL | 变更类型（1 新办 2 换证 3 等级升级 4 等级降级 5 年审 6 信息变更 7 作废） |
| before_level | varchar(50) | 变更前等级 |
| after_level | varchar(50) | 变更后等级 |
| before_expire_date | date | 变更前到期日期 |
| after_expire_date | date | 变更后到期日期 |
| effective_date | date | 生效日期 |
| change_reason | varchar(500) | 变更原因/说明 |
| operator | varchar(64) | 操作人 |
| operate_time | datetime | 操作时间 |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

### 4.7 业绩证明表（本版新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| project_name | varchar(300) NOT NULL | 项目名称 |
| owner_name | varchar(200) | 业主单位/发包方 |
| project_type | varchar(100) | 项目类型（新建/改造升级/运维服务） |
| industry | varchar(100) | 所属行业（防腐/保温/市政热力） |
| contract_amount | decimal(18,2) | 合同金额（万元） |
| sign_date | date | 合同签订日期 |
| start_date | date | 开工日期 |
| finish_date | date | 竣工日期 |
| role | varchar(100) | 承担角色（总承包/专业分包/联合体成员） |
| scale_desc | varchar(500) | 规模描述（如 DN800 主干线 3.2km） |
| contact_person | varchar(100) | 项目联系人 |
| contact_phone | varchar(50) | 联系电话 |
| description | varchar(1000) | 业绩说明 |
| status | tinyint DEFAULT 0 | 状态（0 有效 1 停用） |
| file_count | int DEFAULT 0 | 附件数量（冗余） |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

### 4.8 标书模板分类与模板表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| category_id | bigint NOT NULL | 模板分类编号 |
| name | varchar(200) NOT NULL | 模板名称 |
| industry | varchar(100) | 适用行业（防腐/保温/市政热力） |
| project_type | varchar(100) | 项目类型（新建/改造/运维） |
| description | varchar(500) | 模板说明 |
| **match_keywords** | varchar(500) | **匹配关键词（本版新增，供后续 AI 智能推荐模板检索）** |
| **use_count** | int DEFAULT 0 | **使用次数（本版新增，推荐排序依据）** |
| status | tinyint DEFAULT 0 | 状态（0 启用 1 停用） |
| file_count | int DEFAULT 0 | 附件数量（冗余） |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

### 4.9 业务参数配置表（本版新增）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK AUTO_INCREMENT | 编号 |
| config_key | varchar(100) NOT NULL | 配置键 |
| config_value | varchar(500) | 配置值 |
| name | varchar(100) | 配置名称 |
| remark | varchar(500) | 备注 |
| 公共字段 | — | creator/create_time/updater/update_time/deleted/tenant_id |

| config_key | 默认值 | 说明 |
| --- | --- | --- |
| `bid.cert.warn.days.default` | 90 | 全局默认预警天数（三级取值的兜底） |
| `bid.cert.remind.enable` | true | 是否开启到期主动提醒 |
| `bid.cert.remind.cron` | `0 0 9 * * ?` | 提醒任务执行时间 |
| `bid.cert.status.refresh.cron` | `0 0 1 * * ?` | 状态刷新任务执行时间 |
| `bid.file.max.size.mb` | 20 | 单文件大小上限 |
| `bid.file.allow.ext` | jpg,jpeg,png,bmp,pdf,ofd,doc,docx,xls,xlsx | 允许扩展名白名单（含 ofd，陕西住建电子证书格式） |
| `bid.cert.verify.url.whitelist` | 见《陕西热力防腐保温企业证照清单与官方查验渠道.md》§5 | 官方查验平台地址白名单（禁止用户自行填写） |

### 4.10 建表 SQL（可直接执行）

> v1.1 报告中的 SQL 代码块存在转义污染（`\--`、`bid\_certificate`），无法直接执行。本节为清理后的可执行版本。

```sql
-- ============================================================
-- 天信管业防腐保温智慧平台 — 招投标模块建表脚本
-- 数据库：ruoyi-vue-pro    字符集：utf8mb4
-- ============================================================

-- 1. 电子证照分类
CREATE TABLE IF NOT EXISTS bid_certificate_category (
    id                bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    parent_id         bigint       NOT NULL DEFAULT 0      COMMENT '父分类编号，0为一级',
    name              varchar(100) NOT NULL                COMMENT '分类名称',
    default_warn_days int                   DEFAULT NULL   COMMENT '默认预警天数',
    sort              int          NOT NULL DEFAULT 0      COMMENT '排序',
    status            tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0正常 1停用',
    remark            varchar(500)          DEFAULT NULL   COMMENT '备注',
    creator           varchar(64)           DEFAULT ''     COMMENT '创建者',
    create_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater           varchar(64)           DEFAULT ''     COMMENT '更新者',
    update_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id         bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name, parent_id, deleted, tenant_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子证照分类';

-- 2. 电子证照主表
-- 注意：本语句与 §4.4 字段表严格对应，含陕西核实后新增的 9 个字段
CREATE TABLE IF NOT EXISTS bid_certificate (
    id                 bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    category_id        bigint        NOT NULL                COMMENT '证照分类编号',
    name               varchar(200)  NOT NULL                COMMENT '证照名称',
    code               varchar(100)           DEFAULT NULL   COMMENT '证照编号',
    holder_type        tinyint       NOT NULL DEFAULT 1      COMMENT '持有人类型：1企业 2人员',
    holder_name        varchar(100)           DEFAULT NULL   COMMENT '持有人名称',
    holder_id          bigint                 DEFAULT NULL   COMMENT '持有人ID（人员关联，预留）',
    level              varchar(50)            DEFAULT NULL   COMMENT '资质等级（自由文本，须容纳特/一/二/三级与综合/甲/乙级两套口径）',
    level_standard     tinyint       NOT NULL DEFAULT 1      COMMENT '等级所属标准版本：1建市〔2014〕159号 2资质改革后',
    business_scope     varchar(1000)          DEFAULT NULL   COMMENT '许可范围/承包范围（供热区域面积、锅炉许可参数、合同额上限）',
    major              varchar(100)           DEFAULT NULL   COMMENT '专业（市政/机电等）',
    cert_sub_type      varchar(50)            DEFAULT NULL   COMMENT '证书细分类型（C1/C2/C3、G1/G2/G3、RT/UT Ⅱ级等）',
    registration_no    varchar(100)           DEFAULT NULL   COMMENT '注册号/执业印章号（注册建造师、特种设备作业人员证）',
    register_unit      varchar(200)           DEFAULT NULL   COMMENT '注册单位（与实际聘用企业比对可识别挂靠风险）',
    issuer             varchar(200)           DEFAULT NULL   COMMENT '发证机关',
    issue_date         date                   DEFAULT NULL   COMMENT '发证日期',
    validity_type      tinyint       NOT NULL DEFAULT 2      COMMENT '有效期类型：1长期有效 2固定期限 3待确认',
    expire_date        date                   DEFAULT NULL   COMMENT '到期日期',
    annual_review_date date                   DEFAULT NULL   COMMENT '年度审核/复审日期（ISO年审、司炉证复审，与到期日独立）',
    warn_days          int                    DEFAULT NULL   COMMENT '自定义预警天数（受法定下限约束，见 §7.2）',
    status             tinyint       NOT NULL DEFAULT 0      COMMENT '状态：0有效 1即将到期 2已过期 3已作废 4已失效',
    risk_flag          tinyint       NOT NULL DEFAULT 0      COMMENT '风险标记：0正常 1黄色预警 2红色预警 3失效预警 4异常标注',
    verify_platform    varchar(50)            DEFAULT NULL   COMMENT '官方查验平台标识（jzsc/zlaq/cnse/permit/cnca/coc/sxdzzs）',
    biz_status         tinyint       NOT NULL DEFAULT 0      COMMENT '业务状态：0未发起 1个人已申请 2待企业确认 3已行政许可 4已发证（人员证书专用，P2-9落库）',
    file_count         int           NOT NULL DEFAULT 0      COMMENT '附件数量',
    remark             varchar(500)           DEFAULT NULL   COMMENT '备注',
    creator            varchar(64)            DEFAULT ''     COMMENT '创建者',
    create_time        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater            varchar(64)            DEFAULT ''     COMMENT '更新者',
    update_time        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            bit(1)        NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id          bigint        NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code, tenant_id, deleted),
    KEY idx_category_id (category_id),
    KEY idx_expire_date (expire_date),
    KEY idx_annual_review (annual_review_date),
    KEY idx_status (status),
    KEY idx_risk_flag (risk_flag),
    KEY idx_holder (holder_type, holder_id),
    KEY idx_registration (registration_no),
    KEY idx_level (level, level_standard)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子证照';

-- 3. 附件子表（证照/模板/业绩统一）
CREATE TABLE IF NOT EXISTS bid_certificate_file (
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    biz_type    tinyint      NOT NULL                COMMENT '业务类型：1证照 2模板 3业绩',
    biz_id      bigint       NOT NULL                COMMENT '业务主键',
    file_type   tinyint      NOT NULL DEFAULT 1      COMMENT '文件类型：1照片 2扫描件 3原件 4年审 5变更证明 9其他',
    file_name   varchar(255) NOT NULL                COMMENT '原始文件名',
    file_key    varchar(512) NOT NULL                COMMENT '文件存储键（私有）',
    file_ext    varchar(20)           DEFAULT NULL   COMMENT '扩展名',
    file_size   bigint                DEFAULT NULL   COMMENT '文件大小（字节）',
    file_hash   varchar(64)           DEFAULT NULL   COMMENT '文件SHA256哈希（上传计算，下载核验比对，防篡改）',
    sort        int          NOT NULL DEFAULT 0      COMMENT '排序，0为主附件',
    remark      varchar(500)          DEFAULT NULL   COMMENT '备注',
    creator     varchar(64)           DEFAULT ''     COMMENT '创建者',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater     varchar(64)           DEFAULT ''     COMMENT '更新者',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id   bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_file_key (file_key(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招投标业务附件';

-- 4. 证照变更记录
CREATE TABLE IF NOT EXISTS bid_certificate_change_log (
    id                 bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
    certificate_id     bigint      NOT NULL                COMMENT '证照编号',
    change_type        tinyint     NOT NULL                COMMENT '变更类型：1新办 2换证 3升级 4降级 5年审 6信息变更 7作废',
    before_level       varchar(50)          DEFAULT NULL   COMMENT '变更前等级',
    after_level        varchar(50)          DEFAULT NULL   COMMENT '变更后等级',
    before_expire_date date                 DEFAULT NULL   COMMENT '变更前到期日期',
    after_expire_date  date                 DEFAULT NULL   COMMENT '变更后到期日期',
    effective_date     date                 DEFAULT NULL   COMMENT '生效日期',
    change_reason      varchar(500)         DEFAULT NULL   COMMENT '变更原因',
    operator           varchar(64)          DEFAULT NULL   COMMENT '操作人',
    operate_time       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    creator            varchar(64)          DEFAULT ''     COMMENT '创建者',
    create_time        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater            varchar(64)          DEFAULT ''     COMMENT '更新者',
    update_time        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id          bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_certificate (certificate_id, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子证照变更记录';

-- 5. 业绩证明
CREATE TABLE IF NOT EXISTS bid_performance (
    id              bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    project_name    varchar(300)  NOT NULL                COMMENT '项目名称',
    owner_name      varchar(200)           DEFAULT NULL   COMMENT '业主单位',
    project_type    varchar(100)           DEFAULT NULL   COMMENT '项目类型：新建/改造升级/运维服务',
    industry        varchar(100)           DEFAULT NULL   COMMENT '所属行业',
    contract_amount decimal(18,2)          DEFAULT NULL   COMMENT '合同金额（万元）',
    sign_date       date                   DEFAULT NULL   COMMENT '合同签订日期',
    start_date      date                   DEFAULT NULL   COMMENT '开工日期',
    finish_date     date                   DEFAULT NULL   COMMENT '竣工日期',
    role            varchar(100)           DEFAULT NULL   COMMENT '承担角色：总承包/专业分包/联合体',
    scale_desc      varchar(500)           DEFAULT NULL   COMMENT '规模描述',
    contact_person  varchar(100)           DEFAULT NULL   COMMENT '项目联系人',
    contact_phone   varchar(50)            DEFAULT NULL   COMMENT '联系电话',
    description     varchar(1000)          DEFAULT NULL   COMMENT '业绩说明',
    status          tinyint       NOT NULL DEFAULT 0      COMMENT '状态：0有效 1停用',
    file_count      int           NOT NULL DEFAULT 0      COMMENT '附件数量',
    creator         varchar(64)            DEFAULT ''     COMMENT '创建者',
    create_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater         varchar(64)            DEFAULT ''     COMMENT '更新者',
    update_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         bit(1)        NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id       bigint        NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_industry_type (industry, project_type),
    KEY idx_finish_date (finish_date),
    KEY idx_amount (contract_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业绩证明';

-- 6. 标书模板分类
CREATE TABLE IF NOT EXISTS bid_template_category (
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    parent_id   bigint       NOT NULL DEFAULT 0      COMMENT '父分类编号，0为一级',
    name        varchar(100) NOT NULL                COMMENT '分类名称',
    sort        int          NOT NULL DEFAULT 0      COMMENT '排序',
    status      tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0正常 1停用',
    remark      varchar(500)          DEFAULT NULL   COMMENT '备注',
    creator     varchar(64)           DEFAULT ''     COMMENT '创建者',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater     varchar(64)           DEFAULT ''     COMMENT '更新者',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id   bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name, parent_id, deleted, tenant_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书模板分类';

-- 7. 标书模板
CREATE TABLE IF NOT EXISTS bid_template (
    id             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    category_id    bigint       NOT NULL                COMMENT '模板分类编号',
    name           varchar(200) NOT NULL                COMMENT '模板名称',
    industry       varchar(100)          DEFAULT NULL   COMMENT '适用行业',
    project_type   varchar(100)          DEFAULT NULL   COMMENT '项目类型',
    description    varchar(500)          DEFAULT NULL   COMMENT '模板说明',
    match_keywords varchar(500)          DEFAULT NULL   COMMENT '匹配关键词（AI推荐用）',
    use_count      int          NOT NULL DEFAULT 0      COMMENT '使用次数',
    status         tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0启用 1停用',
    file_count     int          NOT NULL DEFAULT 0      COMMENT '附件数量',
    creator        varchar(64)           DEFAULT ''     COMMENT '创建者',
    create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater        varchar(64)           DEFAULT ''     COMMENT '更新者',
    update_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id      bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_industry_type (industry, project_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书模板';

-- 8. 业务参数配置
CREATE TABLE IF NOT EXISTS bid_config (
    id           bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    config_key   varchar(100) NOT NULL                COMMENT '配置键',
    config_value varchar(500)          DEFAULT NULL   COMMENT '配置值',
    name         varchar(100)          DEFAULT NULL   COMMENT '配置名称',
    remark       varchar(500)          DEFAULT NULL   COMMENT '备注',
    creator      varchar(64)           DEFAULT ''     COMMENT '创建者',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater      varchar(64)           DEFAULT ''     COMMENT '更新者',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
    tenant_id    bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key, tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招投标业务参数配置';
```

### 4.11 初始数据 SQL

> **⚠️ 分类结构已按陕西官方口径修正**。联网核实发现两处问题：①"项目承包等级"不是独立证照类型，而是各类资质证书上的等级标注（特/一/二/三级，或改革后综合/甲/乙级），与"企业资质"分类语义重叠，应取消并改由 `level` + `level_standard` 字段承载；②遗漏了防水防腐保温工程专业承包（主业资质）、特种设备使用登记证、排污许可证、外省企业进陕承揽业务登记等必需证照。完整依据见配套文档《陕西热力防腐保温企业证照清单与官方查验渠道.md》。

```sql
-- 证照分类：一级分类（6 个）
INSERT INTO bid_certificate_category (parent_id, name, default_warn_days, sort, status, remark) VALUES
(0, '企业行政许可',   180, 1, 0, '供热经营许可、安全生产许可、排污许可'),
(0, '建筑业企业资质', 120, 2, 0, '施工总承包与专业承包资质，等级口径见配套调研文档'),
(0, '特种设备许可',    90, 3, 0, '生产许可证与使用登记证，按台套管理'),
(0, '功能证书',        90, 4, 0, '管理体系认证、计量认证、信用等级'),
(0, '人员证书',        60, 5, 0, '安管人员ABC证、注册建造师、特种设备作业人员证等'),
(0, '跨区域经营备案',  90, 6, 0, '外省企业进陕承揽业务登记，含项目备案');

-- 证照分类：二级分类（parent_id 需替换为上述实际生成的一级分类 ID，以下以 1~6 示意）
INSERT INTO bid_certificate_category (parent_id, name, default_warn_days, sort, status, remark) VALUES
-- 一、企业行政许可
(1, '供热经营许可证',             180, 1, 0, '陕西由市/区县/开发区城管部门分级发证，法定办结20个工作日'),
(1, '安全生产许可证',              90, 2, 0, '省住建厅核发，OFD电子证书；2026-03-26起动态核查ABC三类人员配备，需联动校验'),
(1, '排污许可证',                  90, 3, 0, '热力生产和供应属发证行业；法定须在有效期届满前60日申请延续，逾期自动作废'),
-- 二、建筑业企业资质
(2, '防水防腐保温工程专业承包',   120, 1, 0, '★主业资质。分一级、二级，原三级已取消'),
(2, '市政公用工程施工总承包',     120, 2, 0, '热力管道铺设、换热站建设对应'),
(2, '建筑机电安装工程专业承包',   120, 3, 0, '换热站设备、线路、管道安装对应'),
(2, '其他专业承包资质',           120, 4, 0, NULL),
-- 三、特种设备许可
(3, '特种设备生产许可证',          90, 1, 0, '锅炉安装(含修理、改造)A级/B级，编号格式TS3+数字'),
(3, '特种设备使用登记证',          90, 2, 0, '锅炉等按台(套)登记，数量多，建议后续独立设备台账表'),
-- 四、功能证书
(4, '管理体系认证',                90, 1, 0, 'ISO9001/14001/45001，证书3年但需每12个月监督审核，年审日期须独立管理'),
(4, '计量认证',                    90, 2, 0, 'CMA，涉及热计量服务业务'),
(4, '信用等级证书',                90, 3, 0, 'AAA等，无全国统一官方查验平台，发证机构性质须标注'),
-- 五、人员证书
(5, '安管人员安全生产考核合格证书', 60, 1, 0, 'A企业负责人/B项目负责人/C专职安全员(C1机械C2土建C3综合)；陕西按"个人申请、企业确认、行政许可"办理'),
(5, '特种作业人员证书',            60, 2, 0, '住建口径，陕建质发〔2025〕43号'),
(5, '注册建造师',                  90, 3, 0, '一级已全面电子化(2022-01-01起纸质证作废)，须记录注册号与注册单位'),
(5, '特种设备作业人员证',          60, 4, 0, 'G1工业锅炉司炉/G2电站锅炉司炉/G3锅炉水处理；有效期4年，届满前1个月复审'),
(5, '现场管理人员岗位证书',        60, 5, 0, '施工员/质量员/机械员/造价员/劳务员，资质标准硬指标'),
-- 六、跨区域经营备案
(6, '进陕承揽业务登记',            90, 1, 0, '电子登记表无纸质证；分企业入库与项目备案两阶段，须法人实名认证');

-- 模板分类
INSERT INTO bid_template_category (parent_id, name, sort, status) VALUES
(0, '技术标', 1, 0),
(0, '商务标', 2, 0);

-- 业务参数配置
INSERT INTO bid_config (config_key, config_value, name, remark) VALUES
('bid.cert.warn.days.default', '90',      '证照默认预警天数', '证照未设置且分类未设置时使用'),
('bid.cert.remind.enable',     'true',    '到期主动提醒开关', '控制站内信提醒是否发送'),
('bid.cert.remind.cron',       '0 0 9 * * ?',  '提醒任务执行时间', '每日09:00扫描'),
('bid.cert.status.refresh.cron','0 0 1 * * ?', '状态刷新任务执行时间', '每日01:00全量刷新'),
('bid.file.max.size.mb',       '20',      '单文件大小上限(MB)', '超出拒绝上传'),
-- 注意：白名单必须含 ofd，陕西省住建领域企业资质电子证书以 OFD 格式为载体
('bid.file.allow.ext',         'jpg,jpeg,png,bmp,pdf,ofd,doc,docx,xls,xlsx', '允许的文件扩展名', '含ofd以支持陕西住建电子证书'),
-- 官方查验平台白名单：禁止用户自行填写URL，防止员工被引导至假冒钓鱼网站
('bid.cert.verify.whitelist',  '{"jzsc":"https://jzsc.mohurd.gov.cn","zlaq":"https://zlaq.mohurd.gov.cn","cnse":"https://cnse.samr.gov.cn/info-pub/pub","permit":"https://permit.mee.gov.cn","cnca":"http://cx.cnca.cn","coc":"https://www.coc.gov.cn","sxdzzs":"http://sxdzzs.zhitaosoft.com/#/login"}', '官方查验平台白名单', '特种设备查询仅认cnse.samr.gov.cn与cnse.e-cqs.cn，其余为假冒');
```

> **市场监管总局已声明的假冒网站**（`www.cnsx-gov.cn`、`www.cnse.org`、`www.zjcnse-gov.cn`、`cnvse-gov.cn/chaxun` 等）——这是查验地址必须白名单化、禁止自由填写的直接原因。

### 4.12 菜单权限设计

> v1.1 硬编码 ID `6735~6759`，多环境同步易撞号。本版建议改用 `SELECT max(id)` 动态生成，或在迁移脚本中显式声明 ID 区间并登记到项目 ID 分配表。以下 ID 仅为 dev 环境示例。

```
招投标管理 (/bid)
├── 电子证照管理库 (certificate)
│   ├── 证照分类 (category)         按钮: query/create/update/delete
│   ├── 电子证照 (list)             按钮: query/create/update/delete/export/import
│   └── 变更记录 (change-log)       按钮: query/create
├── 业绩证明库 (performance)
│   └── 业绩管理 (list)             按钮: query/create/update/delete/export
├── 模板管理库 (template)
│   ├── 模板分类 (category)         按钮: query/create/update/delete
│   └── 标书模板 (list)             按钮: query/create/update/delete/export
└── 参数配置 (config)               按钮: query/update
```

---

## 5. 关键设计决策与取舍

### 5.1 附件存储：单字段 vs 子表

| 方案 | 说明 | 优点 | 缺点 |
| --- | --- | --- | --- |
| A. 单字段（v1.1 采用） | 证照表直接存 `file_url varchar(512)` | 实现简单，一次查询出全部数据 | **只能存一份附件**；无法满足"照片 + 原文件都要存"；无法区分文件类型；扩展需改表 |
| **B. 附件子表（本版采用）** | 独立 `bid_certificate_file`，`biz_type` + `biz_id` 关联 | 一证多件；可按类型区分（照片/扫描件/年审记录）；证照、模板、业绩三业务复用同一张表；主表查询轻量 | 列表展示附件需二次查询（用 `file_count` 冗余字段规避） |

### 5.2 到期状态：落库 vs 实时计算

| 方案 | 说明 | 优点 | 缺点 |
| --- | --- | --- | --- |
| A. 纯实时计算 | 不存 `status`，RespVO 组装时按 `expire_date` 计算 | 永不过期失真 | **无法用 SQL 按状态过滤/分页**（"只看已过期"需在内存过滤，数据量大时不可行）；导出 Excel 需全量加载 |
| B. 纯落库（v1.1 实际行为） | `status` 存库，仅 create/update 时写入 | 可 SQL 过滤 | **状态腐化**：无人编辑的证照状态永久失真，8 月录入的证照 10 月过期后库里仍是"有效" |
| **C. 落库 + 定时刷新 + 查询重算（本版采用）** | `status` 落库；每日 01:00 XXL-Job 全量刷新；分页查询返回前对当页数据按当前日期重算覆盖 | 可 SQL 过滤；单页展示永不失真；定时任务兜底全量一致性 | 实现复杂度略高 |

```java
// 分页查询返回前，对当页数据重算（防止定时任务未执行或跨日查询导致失真）
// 注意：作废判定已在 calculateStatus 内部按 §7.1 顺序1 处理，此处无需重复判断
private void refreshStatusInMemory(List<BidCertificateDO> list) {
    for (BidCertificateDO cert : list) {
        Integer newStatus = calculateStatus(cert, resolveWarnDays(cert));
        if (!Objects.equals(newStatus, cert.getStatus())) {
            cert.setStatus(newStatus); // 内存覆盖，不回写（避免读操作产生写压力）
        }
    }
}
```

### 5.3 业绩证明：复用证照表 vs 独立表

| 方案 | 说明 | 评估 |
| --- | --- | --- |
| A. 复用 `bid_certificate` + 新增"业绩"分类 | 把业绩当作一种"证照" | ❌ 字段严重不匹配：业绩需要合同金额、工期、业主单位、承担角色、规模描述；证照需要发证机关、资质等级、有效期。强行复用会导致 10+ 字段对一半业务恒为空，检索和统计都困难 |
| **B. 独立 `bid_performance` 表（本版采用）** | 业绩独立建模，附件复用统一附件表 | ✅ 字段语义清晰；可按金额/工期/行业做多维检索（招标评分核心维度）；与证照共享附件存储机制，不重复造轮子 |

### 5.4 人员关联：字符串 vs 外键

- `major` 字段本期即启用，配合 `level` 可支持"一级建造师 + 市政专业"的组合检索。
- `holder_id` 待团队成员管理模块（后续阶段）落地后回填，届时可做证书到期提醒定向推送给持证人本人。

### 5.5 分类结构：平铺 vs 树形

### 5.6 文件访问：公开 URL vs 受控下载

---

## 6. 文件存储与安全设计

### 6.1 问题背景

### 6.2 方案选型

| 方案 | 说明 | 优点 | 缺点 | 采用 |
| --- | --- | --- | --- | --- |
| A. 公开 URL（v1.1） | infra 文件服务直接返回可访问 URL | 实现最简单，前端 `<img src>` 直接用 | **无鉴权，敏感材料裸奔** | ❌ |
| **B. 受控下载接口** | 附件表只存 `file_key`（存储路径），下载走 bid-server 自定义接口，校验权限 + 租户 + 记录审计日志 | 权限可控、可审计、可按角色限制、改造量适中 | 前端预览需带 token 请求，不能用原生 `<img src>` | ✅ **本期采用** |
| C. 私有桶 + 预签名 URL | MinIO 私有桶，后端签发有效期 5 分钟的临时 URL | 安全性最高，支持大文件直传/CDN | 需改造文件存储配置，运维复杂度上升 | 🔜 后续优化 |

### 6.3 受控下载接口设计

```java
@GetMapping("/file/download")
@Operation(summary = "下载证照附件")
@Parameter(name = "id", description = "附件编号", required = true)
@PreAuthorize("@ss.hasPermission('bid:certificate:query')")
public void downloadFile(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
    // 1. 查询附件（自动带租户过滤）
    BidCertificateFileDO file = certificateFileService.getCertificateFile(id);
    if (file == null) {
        throw exception(CERTIFICATE_FILE_NOT_EXISTS);
    }
    // 2. 校验业务对象的访问权限（防止越权访问其他租户/其他业务的数据）
    certificateFileService.validateBizAccess(file.getBizType(), file.getBizId());
    // 3. 记录下载审计日志
    certificateFileService.recordDownloadLog(id, SecurityFrameworkUtils.getLoginUserId());
    // 4. 从文件服务拉取字节流并输出
    byte[] content = fileApi.getFileContent(file.getFileKey());
    ServletUtils.writeAttachment(response, file.getFileName(), content);
}
```

### 6.4 上传约束

| 约束项 | 规则 | 实现位置 |
| --- | --- | --- |
| 扩展名白名单 | jpg/jpeg/png/bmp/pdf/doc/docx/xls/xlsx（可配置） | 前端 accept + 后端二次校验 |
| 单文件大小 | ≤ 20MB（可配置） | 前端拦截 + 后端校验 |
| 单对象附件数 | ≤ 20 | 后端校验 |
| 必填校验 | **证照新增/编辑保存时，附件数 ≥ 1** | 后端 `CERTIFICATE_FILE_REQUIRED` |
| 文件名处理 | 存储用 UUID 重命名，原名保留在 `file_name` 字段 | 防止路径穿越与中文名编码问题 |
| 存储路径组织 | `bid/{tenant_id}/{biz_type}/{yyyy}/{MM}/{uuid}.{ext}` | 便于按租户隔离与归档清理 |
| 文件哈希 | 上传后计算 SHA256 存 `bid_certificate_file.file_hash`；下载/预览时比对，不一致则拒绝并告警 | 防篡改；证照原件为投标关键证据，哈希不一致可直接判定材料异常（2026-09-07 综合评审补入） |

### 6.5 审计日志

---

## 7. 到期提醒设计

### 7.1 状态计算规则

> **⚠️ 本节已按陕西核实结果重写**。原规则只按 `expire_date` 判定，会漏判"到期日之前就已失效"的情形（安许证人员预警、司炉证作业中断、ISO 年审逾期），导致已失效证照显示为"🟢有效"、直接造成投标资格误判。

| 顺序 | 条件 | status | 展示 | 依据 |
| --- | --- | --- | --- | --- |
| 1 | `status = 3`（人工作废） | 3 | ⚫ 已作废 | 不参与任何自动计算 |
| 2 | `risk_flag = 3`（失效预警） | **4** | ⛔ 已失效 | ✅ 陕西：安许证三类人员数量全部为 0 时平台标注"失效预警" |
| 3 | `risk_flag = 2`（红色预警） | **4** | ⛔ 已失效 | ✅ 陕西：A/B/C 任一类人员数量为 0（劳务分包 B 证除外） |
| 4 | `annual_review_date < today` | **4** | ⛔ 已失效 | ✅ ISO 年审逾期证书被暂停/撤销；✅ 司炉证逾期未复审证书自动失效 |
| 5 | `expire_date < today` | 2 | 🔴 已过期 | — |
| 6 | `expire_date <= today + warn_days` | 1 | 🟡 即将到期(剩 N 天) | — |
| 7 | `annual_review_date <= today + 30` | 1 | 🟡 年审临近 | ✅ 年审是独立时间维度，须单独提醒 |
| 8 | `validity_type = 1`（长期有效） | 0 | 🟢 长期有效 | — |
| 9 | `validity_type = 3`（待确认） | 0 | ⚪ 待确认（提示补充有效期） | — |
| 10 | `risk_flag = 1`（黄色预警）或 `4`（异常标注） | 0 | 🟢 有效 ⚠️ 带风险标记 | ✅ 黄色预警仍可投标但需整改；人员 12 个月内变更≥4 次标注"异常" |
| 11 | 其他 | 0 | 🟢 有效 | — |

- **新增 status = 4「已失效」**，与"已过期"严格区分：过期是时间到了（可续期挽回），失效是条件不满足（需先补齐人员/完成年审才能恢复）。两者对投标的影响和处置动作完全不同，混为一谈会误导决策。
- `risk_flag` 为**人工维护或后续对接接口回填**，不由系统自动计算（安许证预警状态目前只有陕西省住建厅平台掌握，无公开查询接口）。前端需在安许证详情页提示管理员定期登录平台核对。
- **v1.1 缺陷修正保留**：原设计"`expire_date` 为空即判有效"，把"长期有效"和"漏填日期"混为一谈。本版用 `validity_type` 显式区分，漏填日期时前端标黄提示补录。

### 7.2 预警天数三级取值 + 法定下限保护

```java
/**
 * 解析生效的预警天数。
 * 三级取值：证照 warn_days → 分类 default_warn_days → 全局配置。
 * 最后叠加法定下限保护，防止管理员手工调低导致错过法定申请窗口。
 */
private Integer resolveWarnDays(BidCertificateDO cert) {
    Integer days;
    // 优先级 1：证照自身配置
    if (cert.getWarnDays() != null) {
        days = cert.getWarnDays();
    } else {
        // 优先级 2：所属分类配置
        BidCertificateCategoryDO category = categoryService.getCategory(cert.getCategoryId());
        if (category != null && category.getDefaultWarnDays() != null) {
            days = category.getDefaultWarnDays();
        } else {
            // 优先级 3：全局配置
            days = configService.getIntValue("bid.cert.warn.days.default", 90);
        }
    }
    // 法定下限保护（见下方说明）
    Integer floor = resolveLegalFloorDays(cert.getCategoryId());
    return floor != null ? Math.max(days, floor) : days;
}
```

| 证照 | 法定时限 | 下限 | 逾期后果 |
| --- | --- | --- | --- |
| **排污许可证** | ✅《排污许可管理条例》第十四条：须于有效期**届满 60 日前**申请延续 | 60 | 核发部门依法办理注销并在全国平台公告，**原许可证自动作废**，注销信息公开可见 |
| **特种设备作业人员证**（G1/G2/G3） | ✅ 有效期 4 年，须于**届满前 1 个月**申请复审 | 60（留办理余量） | 逾期未复审或复审不合格，**证书自动失效**，需重新申请取证 |

> ⚠️ 若不做下限保护，管理员在 `warn_days` 上手工填 30 天就能绕过法定窗口——排污许可证被注销是**不可逆的合规事故**。因此下限校验必须在 Service 层强制生效，且**保存时就拦截**（`warn_days < 法定下限` 直接拒绝并提示法定时限），不能只在计算时兜底。

### 7.3 定时任务

| 任务 | Cron | 作用 |
| --- | --- | --- |
| `CertificateStatusRefreshJob` | `0 0 1 * * ?` | 全量重算所有证照 `status`（按 §7.1 完整规则，含 `risk_flag` 与 `annual_review_date`）并回写，解决状态腐化 |
| `CertificateExpireRemindJob` | `0 0 9 * * ?` | 扫描 `status IN (1,2,4)` 的证照，通过 Feign 调用 system-server 站内信接口推送给企业管理员 |
| **`CertificateAnnualReviewRemindJob`** | `0 0 9 * * ?` | **本版新增**：扫描 `annual_review_date` 临近的记录（ISO 年审、司炉证复审），独立于到期提醒推送。因年审周期（一般 12 个月，部分机构 6 或 10 个月）与证书有效期（约 3 年）是两个独立时间维度 |
| **`CertificateSafetyCertCheckJob`** | `0 0 8 ? * MON` | **本版新增（P1，可后置）**：每周一统计各企业 A/B/C 三类人员证书的有效数量，对照安全生产许可证要求，数量不足时在证照详情页展示风险提示并推送管理员。依据：陕西自 2026-03-26 起动态核查安管人员配备 |

```
【证照到期提醒】您的证照「{name}」（编号：{code}）将于 {expire_date} 到期，
剩余 {days} 天。请及时办理续期，避免影响投标资格。
```

```
【证照失效预警】您的「{name}」（编号：{code}）当前状态为已失效，
原因：{failReason}。该证照不可用于投标，请先{action}后恢复有效性。
```

```
【年度审核提醒】您的「{name}」年度监督审核/复审日期为 {annualReviewDate}，
剩余 {days} 天。逾期未完成审核将导致证书暂停或失效。
```

### 7.4 前端展示

- 列表页：状态列用 Tag 组件，即将到期显示"🟡 即将到期(剩 12 天)"。
- 列表页顶部增加统计条：`有效 42 | 即将到期 5 | 已过期 2`，点击可快速筛选。
- 首页 KPI 卡片（后续阶段）复用该统计接口 `/certificate/status-summary`。

---

## 8. 后端设计（txgy-module-bid）

### 8.1 模块结构

```
txgy-module-bid/
├── txgy-module-bid-api/                    # API 模块（供其他服务 Feign 调用）
│   └── cn.iocoder.txgy.module.bid
│       ├── api/
│       │   ├── certificate/BidCertificateApi.java      # 供投标人端 AI 检索证照
│       │   ├── performance/BidPerformanceApi.java      # 供投标人端 AI 检索业绩
│       │   └── file/BidFileApi.java                    # 文件下载
│       └── enums/
│           ├── ApiConstants.java                       # 服务名 bid-server
│           ├── CertificateStatusEnum.java
│           ├── ValidityTypeEnum.java
│           ├── FileBizTypeEnum.java
│           └── ChangeTypeEnum.java
└── txgy-module-bid-server/
    └── cn.iocoder.txgy.module.bid
        ├── BidServerApplication.java                   # 启动类（端口 48095）
        ├── controller/admin/
        │   ├── BidCertificateCategoryController.java
        │   ├── BidCertificateController.java
        │   ├── BidCertificateFileController.java       # 附件上传/下载/删除
        │   ├── BidCertificateChangeLogController.java  # 变更记录
        │   ├── BidPerformanceController.java           # 业绩证明
        │   ├── BidTemplateCategoryController.java
        │   ├── BidTemplateController.java
        │   ├── BidConfigController.java                # 参数配置
        │   └── vo/                                     # 每实体 PageReq/Resp/SaveReq
        ├── dal/
        │   ├── dataobject/                             # 8 个 DO（继承 TenantBaseDO）
        │   └── mysql/                                  # 8 个 Mapper（继承 BaseMapperX）
        ├── service/
        │   ├── ErrorCodeConstants.java
        │   └── 8 组 Service / ServiceImpl
        └── job/                                        # 本版新增
            ├── CertificateStatusRefreshJob.java
            └── CertificateExpireRemindJob.java
```

### 8.2 接口清单（网关前缀 `/admin-api/bid`）

#### 电子证照分类

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/certificate-category/list` | query | 分类列表（下拉/树用） |
| GET | `/certificate-category/tree` | query | **树形结构（本版新增）** |
| GET | `/certificate-category/page` | query | 分页查询 |
| POST | `/certificate-category/create` | create | 新增（名称唯一 + 父级存在校验） |
| PUT | `/certificate-category/update` | update | 修改（禁止将父级设为自身子孙） |
| DELETE | `/certificate-category/delete?id=` | delete | 删除（有子分类或证照引用时保护） |
| DELETE | `/certificate-category/delete-list?ids=` | delete | 批量删除（带引用保护） |

#### 电子证照

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/certificate/page` | query | 分页查询（拼分类名 + 状态重算） |
| GET | `/certificate/get?id=` | query | 详情（含附件列表） |
| GET | `/certificate/status-summary` | query | **状态统计（本版新增）** |
| POST | `/certificate/create` | create | 新增（校验分类/编号唯一/**附件必填**/算状态） |
| PUT | `/certificate/update` | update | 更新（重算状态 + **写变更记录**） |
| DELETE | `/certificate/delete?id=` | delete | 删除（级联软删附件与变更记录） |
| DELETE | `/certificate/delete-list?ids=` | delete | 批量删除 |
| GET | `/certificate/export-excel` | export | 导出 Excel（≤1000 条） |

#### 证照附件

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/certificate-file/list?bizType=&bizId=` | query | 附件列表 |
| POST | `/certificate-file/upload` | create | **上传附件（校验扩展名/大小/数量）** |
| GET | `/certificate-file/download?id=` | query | **受控下载（鉴权 + 审计）** |
| DELETE | `/certificate-file/delete?id=` | delete | 删除附件 |

#### 变更记录 / 业绩证明 / 模板 / 配置

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/certificate-change-log/page` | query | 变更记录分页（按证照过滤） |
| POST | `/certificate-change-log/create` | create | 手工登记变更（年审等） |
| GET/POST/PUT/DELETE | `/performance/*` | query/create/update/delete | 业绩 CRUD + `delete-list` + `export-excel` |
| GET/POST/PUT/DELETE | `/template-category/*`、`/template/*` | 同上 | 模式与证照侧一致 |
| GET/PUT | `/config/*` | query/update | 参数配置查询与修改 |

### 8.3 关键实现代码

#### 8.3.1 状态计算（修正版）

```java
/** 状态：0 有效 */
private static final int STATUS_VALID = 0;
/** 状态：1 即将到期 */
private static final int STATUS_EXPIRING = 1;
/** 状态：2 已过期（时间届满，可通过续期挽回） */
private static final int STATUS_EXPIRED = 2;
/** 状态：3 已作废（人工设置，不参与自动计算） */
private static final int STATUS_INVALID = 3;
/** 状态：4 已失效（条件不满足，须先补齐人员或完成年审才能恢复，本版新增） */
private static final int STATUS_REVOKED = 4;

/** 风险标记：2 红色预警（陕西安许证：ABC三类中任一类人员数量为0） */
private static final int RISK_FLAG_RED = 2;
/** 风险标记：3 失效预警（陕西安许证：ABC三类人员数量全部为0） */
private static final int RISK_FLAG_REVOKED = 3;

/** 年审临近提醒天数（固定30天，不走 warn_days 三级取值） */
private static final int ANNUAL_REVIEW_WARN_DAYS = 30;

/**
 * 计算证照状态。判定顺序严格对应 §7.1 规则表，自上而下短路。
 *
 * @param cert     证照对象（需含 status/riskFlag/validityType/expireDate/annualReviewDate）
 * @param warnDays 预警天数（已按 §7.2 三级取值 + 法定下限解析）
 */
private Integer calculateStatus(BidCertificateDO cert, Integer warnDays) {
    // 顺序1：人工作废不参与任何自动计算
    if (Objects.equals(cert.getStatus(), STATUS_INVALID)) {
        return STATUS_INVALID;
    }
    // 顺序2、3：陕西安许证红色/失效预警 —— 人员数量不达标，证照在到期日前即已失效
    if (Objects.equals(cert.getRiskFlag(), RISK_FLAG_REVOKED)
            || Objects.equals(cert.getRiskFlag(), RISK_FLAG_RED)) {
        return STATUS_REVOKED;
    }
    LocalDate today = LocalDate.now();
    // 顺序4：年审/复审逾期 —— ISO年审逾期证书被暂停或撤销，司炉证逾期未复审自动失效
    if (cert.getAnnualReviewDate() != null && cert.getAnnualReviewDate().isBefore(today)) {
        return STATUS_REVOKED;
    }
    // 顺序5：到期日期已过
    if (cert.getExpireDate() != null && cert.getExpireDate().isBefore(today)) {
        return STATUS_EXPIRED;
    }
    // 顺序6：进入到期预警窗口
    if (cert.getExpireDate() != null
            && !cert.getExpireDate().isAfter(today.plusDays(warnDays))) {
        return STATUS_EXPIRING;
    }
    // 顺序7：年审临近（与到期日独立的时间维度，须单独提醒）
    if (cert.getAnnualReviewDate() != null
            && !cert.getAnnualReviewDate().isAfter(today.plusDays(ANNUAL_REVIEW_WARN_DAYS))) {
        return STATUS_EXPIRING;
    }
    // 顺序8、9：长期有效 / 待确认，均视为有效
    if (Objects.equals(cert.getValidityType(), VALIDITY_TYPE_PERMANENT)
            || Objects.equals(cert.getValidityType(), VALIDITY_TYPE_UNCONFIRMED)) {
        return STATUS_VALID;
    }
    // 固定期限但日期缺失：数据异常，按有效处理，由前端标黄提示补录
    if (cert.getExpireDate() == null) {
        return STATUS_VALID;
    }
    // 顺序10、11：黄色预警或人员异常标注仍可投标但需整改，status 保持有效，
    // 风险信息由 risk_flag 字段独立承载，在列表页以角标提示
    return STATUS_VALID;
}
```

> **⚠️ 与 §7.1 的对应关系**：本方法完整落地规则表的 11 行判定。两处易错点：
> 1. **"黄色预警（`risk_flag=1`）"与"人员异常标注（`risk_flag=4`）"不改变 status** —— 依据是陕西规则中黄色预警仅要求"整改"，企业仍可参与投标。若把它算成失效会造成误判、错失投标机会。风险信息通过独立的 `risk_flag` 字段在列表页以角标呈现。
> 2. **顺序 2/3 必须排在日期判断之前** —— 安许证的失效与到期日期无关，若先判日期会把"红色预警但日期未过"的证照误判为"🟢有效"。

```java
certificate.setStatus(calculateStatus(certificate, resolveWarnDays(certificate)));
```

#### 8.3.2 创建证照（含完整校验）

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createCertificate(BidCertificateSaveReqVO createReqVO) {
    // 1. 校验分类存在且启用
    validateCategory(createReqVO.getCategoryId());
    // 2. 校验证照编号唯一（同租户内）
    validateCodeUnique(createReqVO.getCode(), null);
    // 3. 校验有效期类型与到期日期的匹配关系
    validateExpireDate(createReqVO.getValidityType(), createReqVO.getExpireDate());
    // 4. 校验预警天数不得低于法定下限（排污许可证60日、特种设备作业人员证等，见 §7.2）
    validateWarnDaysFloor(createReqVO.getCategoryId(), createReqVO.getWarnDays());
    // 5. 校验附件必填（落实"照片或原文件都要存进去"的硬性要求）
    if (CollUtil.isEmpty(createReqVO.getFiles())) {
        throw exception(CERTIFICATE_FILE_REQUIRED);
    }
    // 6. 组装 DO 并计算状态（calculateStatus 内部已处理作废与风险标记，见 §8.3.1）
    BidCertificateDO certificate = BeanUtils.toBean(createReqVO, BidCertificateDO.class).setId(null);
    certificate.setStatus(calculateStatus(certificate, resolveWarnDays(certificate)));
    certificate.setFileCount(createReqVO.getFiles().size());
    certificateMapper.insert(certificate);
    // 7. 保存附件（biz_type=1）
    certificateFileService.saveFiles(FILE_BIZ_CERTIFICATE, certificate.getId(), createReqVO.getFiles());
    // 8. 写入变更记录（类型：新办）
    changeLogService.record(certificate.getId(), CHANGE_TYPE_NEW, null, certificate);
    return certificate.getId();
}
```

> **⚠️ 修正说明**：本节原稿第 4 步校验 `createReqVO.getFileKeys()`、第 6 步却使用 `createReqVO.getFiles()`，两个不同的 getter 会导致校验与实际保存的数据源不一致（校验通过但保存为空，或反之）。已统一为 `getFiles()`。同时 `fileCount` 改为 insert 前赋值，避免原稿"insert 后再 updateById 一次"的多余写操作。

#### 8.3.3 更新证照（含变更记录）

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateCertificate(BidCertificateSaveReqVO updateReqVO) {
    // 1. 校验存在
    BidCertificateDO oldCert = validateCertificateExists(updateReqVO.getId());
    // 2. 校验编号唯一（排除自身）
    validateCodeUnique(updateReqVO.getCode(), updateReqVO.getId());
    // 3. 校验分类、有效期、预警天数法定下限
    validateCategory(updateReqVO.getCategoryId());
    validateExpireDate(updateReqVO.getValidityType(), updateReqVO.getExpireDate());
    validateWarnDaysFloor(updateReqVO.getCategoryId(), updateReqVO.getWarnDays());
    // 4. 更新
    BidCertificateDO updateObj = BeanUtils.toBean(updateReqVO, BidCertificateDO.class);
    updateObj.setStatus(calculateStatus(updateObj, resolveWarnDays(updateObj)));
    certificateMapper.updateById(updateObj);
    // 5. 同步附件
    certificateFileService.syncFiles(FILE_BIZ_CERTIFICATE, updateReqVO.getId(), updateReqVO.getFiles());
    // 6. 检测关键字段变化并写入变更记录
    changeLogService.recordIfChanged(oldCert, updateObj);
}
```

#### 8.3.4 变更记录自动检测

```java
public void recordIfChanged(BidCertificateDO oldCert, BidCertificateDO newCert) {
    Integer changeType = null;
    // 等级变化 → 升级/降级
    if (!Objects.equals(oldCert.getLevel(), newCert.getLevel())) {
        changeType = compareLevel(newCert.getLevel(), oldCert.getLevel()) >= 0
                     ? CHANGE_TYPE_UPGRADE : CHANGE_TYPE_DOWNGRADE;
    }
    // 编号变化 → 换证
    else if (!Objects.equals(oldCert.getCode(), newCert.getCode())) {
        changeType = CHANGE_TYPE_RENEW;
    }
    // 到期日期变化 → 年审/续期
    else if (!Objects.equals(oldCert.getExpireDate(), newCert.getExpireDate())) {
        changeType = CHANGE_TYPE_ANNUAL_REVIEW;
    }
    // 状态变为作废
    else if (Objects.equals(newCert.getStatus(), STATUS_INVALID)
             && !Objects.equals(oldCert.getStatus(), STATUS_INVALID)) {
        changeType = CHANGE_TYPE_INVALID;
    }
    if (changeType != null) {
        record(newCert.getId(), changeType, oldCert, newCert);
    }
}
```

#### 8.3.5 定时任务

```java
@Component
public class CertificateStatusRefreshJob {

    @Resource
    private BidCertificateService certificateService;

    /**
     * 每日 01:00 全量刷新证照到期状态。
     * 解决"状态仅在写入时计算导致腐化"的问题。
     */
    @XxlJob("certificateStatusRefreshJob")
    public void execute() {
        int count = certificateService.refreshAllStatus();
        XxlJobHelper.log("证照状态刷新完成，处理 {} 条", count);
    }
}
```

```java
// ServiceImpl 中的批量刷新实现（分批处理，避免一次性加载全表）
@Override
public int refreshAllStatus() {
    int total = 0;
    int pageNo = 1;
    final int batchSize = 500;
    while (true) {
        Page<BidCertificateDO> page = certificateMapper.selectPage(
                new Page<>(pageNo, batchSize), new LambdaQueryWrapperX<>());
        List<BidCertificateDO> list = page.getRecords();
        if (CollUtil.isEmpty(list)) {
            break;
        }
        for (BidCertificateDO cert : list) {
            if (Objects.equals(cert.getStatus(), STATUS_INVALID)) {
                continue; // 已作废不自动计算
            }
            Integer newStatus = calculateStatus(cert.getValidityType(), cert.getExpireDate(),
                                                resolveWarnDays(cert));
            if (!Objects.equals(newStatus, cert.getStatus())) {
                certificateMapper.updateById(new BidCertificateDO()
                        .setId(cert.getId()).setStatus(newStatus));
                total++;
            }
        }
        if (list.size() < batchSize) {
            break;
        }
        pageNo++;
    }
    return total;
}
```

#### 8.3.6 导出 Excel

```java
@GetMapping("/export-excel")
@Operation(summary = "导出电子证照 Excel")
@PreAuthorize("@ss.hasPermission('bid:certificate:export')")
public void exportCertificateExcel(@Valid BidCertificatePageReqVO pageReqVO,
                                   HttpServletResponse response) throws IOException {
    pageReqVO.setPageSize(PAGE_SIZE_NONE); // 导出不分页，由 Service 内部限流
    List<BidCertificateDO> list = certificateService.getCertificatePage(pageReqVO).getList();
    if (list.size() > 1000) {
        list = list.subList(0, 1000); // 超出上限截断并在文件名标注
    }
    // 拼接分类名称
    Map<Long, BidCertificateCategoryDO> categoryMap = convertMap(
            certificateCategoryService.getCategoryList(null), BidCertificateCategoryDO::getId);
    List<BidCertificateRespVO> data = convertList(list, cert -> {
        BidCertificateRespVO vo = BeanUtils.toBean(cert, BidCertificateRespVO.class);
        findAndThen(categoryMap, cert.getCategoryId(), c -> vo.setCategoryName(c.getName()));
        return vo;
    });
    ExcelUtils.write(response, "电子证照.xls", "数据", BidCertificateRespVO.class, data);
}
```

> **导出安全说明**：Excel 中**不包含附件下载链接**（防止敏感 URL 随文件外传）。如需导出附件，走独立的"打包下载"接口，该接口记录审计日志。

#### 8.3.7 错误码定义

```java
public interface ErrorCodeConstants {

    // ========== 电子证照分类 1-200-000-000 ==========
    ErrorCode CERTIFICATE_CATEGORY_NOT_EXISTS      = new ErrorCode(1_200_000_000, "电子证照分类不存在");
    ErrorCode CERTIFICATE_CATEGORY_NAME_DUPLICATE  = new ErrorCode(1_200_000_001, "同级别下分类名称已存在");
    ErrorCode CERTIFICATE_CATEGORY_HAS_CERTIFICATE = new ErrorCode(1_200_000_002, "该分类下存在证照，无法删除");
    ErrorCode CERTIFICATE_CATEGORY_HAS_CHILDREN    = new ErrorCode(1_200_000_003, "该分类下存在子分类，无法删除");
    ErrorCode CERTIFICATE_CATEGORY_PARENT_ERROR    = new ErrorCode(1_200_000_004, "不能将自己或子孙分类设为父级");

    // ========== 电子证照 1-200-000-100 ==========
    ErrorCode CERTIFICATE_NOT_EXISTS               = new ErrorCode(1_200_000_100, "电子证照不存在");
    ErrorCode CERTIFICATE_CODE_DUPLICATE           = new ErrorCode(1_200_000_101, "证照编号已存在，请勿重复录入");
    ErrorCode CERTIFICATE_FILE_REQUIRED            = new ErrorCode(1_200_000_102, "请至少上传一份证照照片或原文件");
    ErrorCode CERTIFICATE_EXPIRE_DATE_REQUIRED     = new ErrorCode(1_200_000_103, "有效期类型为固定期限时，到期日期不能为空");
    ErrorCode CERTIFICATE_HOLDER_NAME_REQUIRED     = new ErrorCode(1_200_000_104, "持有人类型为人员时，人员姓名不能为空");

    // ========== 证照附件 1-200-000-150 ==========
    ErrorCode CERTIFICATE_FILE_NOT_EXISTS          = new ErrorCode(1_200_000_150, "附件不存在");
    ErrorCode FILE_EXT_NOT_ALLOWED                 = new ErrorCode(1_200_000_151, "不支持的文件类型，仅允许：{}");
    ErrorCode FILE_SIZE_EXCEEDED                   = new ErrorCode(1_200_000_152, "文件大小超过限制（{}MB）");
    ErrorCode FILE_COUNT_EXCEEDED                  = new ErrorCode(1_200_000_153, "附件数量超过上限（{}个）");
    ErrorCode FILE_ACCESS_DENIED                   = new ErrorCode(1_200_000_154, "无权访问该附件");

    // ========== 变更记录 1-200-000-180 ==========
    ErrorCode CHANGE_LOG_NOT_EXISTS                = new ErrorCode(1_200_000_180, "变更记录不存在");

    // ========== 标书模板分类 1-200-000-200 ==========
    ErrorCode TEMPLATE_CATEGORY_NOT_EXISTS         = new ErrorCode(1_200_000_200, "标书模板分类不存在");
    ErrorCode TEMPLATE_CATEGORY_NAME_DUPLICATE     = new ErrorCode(1_200_000_201, "同级别下模板分类名称已存在");
    ErrorCode TEMPLATE_CATEGORY_HAS_TEMPLATE       = new ErrorCode(1_200_000_202, "该分类下存在模板，无法删除");
    ErrorCode TEMPLATE_CATEGORY_HAS_CHILDREN       = new ErrorCode(1_200_000_203, "该分类下存在子分类，无法删除");

    // ========== 标书模板 1-200-000-300 ==========
    ErrorCode TEMPLATE_NOT_EXISTS                  = new ErrorCode(1_200_000_300, "标书模板不存在");

    // ========== 业绩证明 1-200-000-400 ==========
    ErrorCode PERFORMANCE_NOT_EXISTS               = new ErrorCode(1_200_000_400, "业绩证明不存在");
    ErrorCode PERFORMANCE_DATE_ORDER_ERROR         = new ErrorCode(1_200_000_401, "竣工日期不能早于开工日期");

    // ========== 参数配置 1-200-000-500 ==========
    ErrorCode CONFIG_NOT_EXISTS                    = new ErrorCode(1_200_000_500, "配置项不存在");
    ErrorCode CONFIG_KEY_DUPLICATE                 = new ErrorCode(1_200_000_501, "配置键已存在");
}
```

### 8.4 实现要点

- **分页**：`BaseMapperX.selectPage(PageParam, LambdaQueryWrapperX)`，默认每页 10 条。
- **批量删除**：`@DeleteMapping("/delete-list")` + `@RequestParam("ids") List<Long> ids`；分类删除前校验子分类与引用；证照删除时级联软删附件与变更记录。
- **导出**：RespVO 独立类（不继承 SaveReqVO），每字段标注 `@ExcelProperty`；文件名「电子证照.xls」「标书模板.xls」「业绩证明.xls」。
- **权限注解**：每个接口 `@PreAuthorize("@ss.hasPermission('bid:xxx:yyy')")` 与菜单按钮权限串一一对应。
- **事务**：涉及主表 + 附件表 + 变更记录表的操作必须加 `@Transactional(rollbackFor = Exception.class)`。
- **租户隔离**：所有 DO 继承 `TenantBaseDO`，Mapper 查询自动注入 `tenant_id` 条件；受控下载接口额外做 `validateBizAccess` 二次校验，防止跨租户越权。

---

## 9. 前端设计（txgy-ui-admin-vben）

### 9.1 文件结构

```
apps/web-antd/src/
├── api/bid/                                    # 8 个 API 文件
│   ├── certificate-category/index.ts           # 含 tree/deleteList
│   ├── certificate/index.ts                    # 含 deleteList/export/statusSummary
│   ├── certificate-file/index.ts               # 含 upload/download(blob)/list
│   ├── certificate-change-log/index.ts
│   ├── performance/index.ts                    # 含 deleteList/export
│   ├── template-category/index.ts
│   ├── template/index.ts
│   └── config/index.ts
└── views/bid/
    ├── certificate/
    │   ├── category/                           # 树形表格
    │   ├── list/                               # 证照列表（附件多上传/受控预览/批量/导出）
    │   └── change-log/                         # 变更记录
    ├── performance/
    │   └── list/                               # 业绩管理
    ├── template/
    │   ├── category/
    │   └── list/
    └── config/                                 # 参数配置
```

### 9.2 页面能力

| 页面 | 能力 |
| --- | --- |
| 证照分类 | 树形列表、新增（选父级）、编辑、删除、批量删除（子级与引用保护） |
| 电子证照 | 列表（状态标签 + 剩余天数）、**顶部状态统计条（可点击筛选）**、新增（**多附件上传 + 必填校验**）、编辑、详情（附件画廊 + 变更记录时间轴）、删除、批量删除、导出 Excel、**受控预览/下载** |
| 变更记录 | 按证照筛选的时间轴/列表，手工登记年审记录 |
| 业绩管理 | 列表、新增（多附件）、编辑、删除、批量删除、导出 Excel |
| 模板分类 | 树形列表、增删改、批量删除（引用保护） |
| 标书模板 | 列表、新增（多附件）、编辑、删除、批量删除、导出 Excel、受控下载 |
| 参数配置 | 预警天数、提醒开关、文件限制等在线调整 |

### 9.3 关键实现

- **表单框架**：`useVbenForm` + `useVbenModal`（项目标准），组件 `FileUpload`/`DatePicker`/`RadioGroup`/`InputNumber`/`TreeSelect`/`ApiCascader` 已在 `src/adapter/component/index.ts` 注册。
- **多附件上传**：`FileUpload` 组件配置 `multiple: true`、`maxNumber: 20`、`accept` 取后端配置的白名单；每个文件需选择 `file_type`（照片/扫描件/原件/年审/其他），第一份默认为主附件（`sort=0`）。
- **受控预览**：图片/PDF 预览改为 `requestClient.get('/bid/certificate-file/download', { params: { id }, responseType: 'blob' })`，再 `URL.createObjectURL(blob)` 渲染；组件卸载时 `revokeObjectURL` 释放。
- **必填校验**：表单 `rules` 中对附件字段加 `required: true`，同时后端二次校验（前端可绕过）。
- **日期格式**：`DatePicker` 配 `valueFormat: 'YYYY-MM-DD'`，与后端 `date` 字段对齐。
- **有效期类型联动**：`validity_type` 切换为"长期有效"或"待确认"时，禁用并清空 `expire_date`；切为"固定期限"时 `expire_date` 变必填。
- **批量选择**：`checkboxConfig: { highlight: true }` + `gridEvents.checkboxChange/checkboxAll` + `gridApi.grid?.getCheckboxRecords()`，选中行显示操作条。
- **导出**：`requestClient.download('/bid/certificate/export-excel', { params })` + `downloadFileFromBlobPart`。
- **树形分类选择**：证照表单的分类字段用 `TreeSelect`，数据源 `/certificate-category/tree`。

---

## 10. 网关与部署配置

### 10.1 网关路由

```yaml
        ## bid-server 服务（电子证照管理库 + 模板管理库 + 业绩证明库）
        - id: bid-admin-api
          uri: grayLb://bid-server
          predicates:
            - Path=/admin-api/bid/**
          filters:
            - RewritePath=/admin-api/bid/v3/api-docs, /v3/api-docs
```

### 10.2 POM 聚合

- 根 `pom.xml` 新增 `<module>txgy-module-bid</module>`
- `txgy-server/pom.xml` 新增 `txgy-module-bid-server` 依赖
- `txgy-module-bid-server/pom.xml` 必需依赖：
  - `txgy-spring-boot-starter-websocket`（**缺失会导致启动失败**，见 §14.1）
  - `txgy-spring-boot-starter-job`（本版新增，定时任务）
  - `txgy-spring-boot-starter-excel`、`-mybatis`、`-security`、`-rpc`、`-monitor`、`-env`
  - `txgy-module-system-api`、`txgy-module-infra-api`（Feign 调用站内信与文件服务）
- 启动类 `@SpringBootApplication(scanBasePackages = {"${txgy.info.base-package}.server", "${txgy.info.base-package}.module"})`，新包 `module.bid` 自动被扫描。

### 10.3 XXL-Job 注册

| 执行器 | 任务名 | JobHandler | Cron |
| --- | --- | --- | --- |
| bid-server | 证照状态刷新 | `certificateStatusRefreshJob` | `0 0 1 * * ?` |
| bid-server | 证照到期提醒 | `certificateExpireRemindJob` | `0 0 9 * * ?` |

---

## 11. 系统运行与运维手册

### 11.1 服务清单与端口

| 服务 | 端口 | 启动方式 | 说明 |
| --- | --- | --- | --- |
| MySQL | 3306 | 本机服务 mysqld | 数据库 |
| Redis | 6379 | Docker 容器 `my-redis` | 缓存（提醒去重用） |
| Nacos | 8848 | Docker 容器 `deploy-nacos-1` | 注册中心/配置中心（namespace dev） |
| system-server | 48081 | IDEA 运行 `SystemServerApplication` | 登录/用户/角色/站内信 |
| infra-server | 48082 | IDEA 运行 `InfraServerApplication` | 文件存储 |
| **bid-server** | **48095** | IDEA 运行 **`BidServerApplication`** | 本次新增 |
| gateway | 48080 | IDEA 运行 `GatewayServerApplication` | 统一入口 |
| XXL-Job Admin | 8080（或项目约定端口） | 定时任务调度中心 | 本版新增依赖 |
| 前端 | 5666 | 终端 `pnpm dev`（web-antd 目录） | Vite dev server |

### 11.2 启动顺序

1. 启动基础容器：`docker start deploy-nacos-1 my-redis`，确认 MySQL 服务运行。
2. 启动 XXL-Job Admin（若定时任务需生效）。
3. IDEA 依次启动：`SystemServerApplication` → `InfraServerApplication` → `BidServerApplication` → `GatewayServerApplication`。
4. 终端启动前端：进入 `apps/web-antd` 执行 `pnpm dev`（若提示 pnpm 版本锁定，先设 `COREPACK_ENABLE_STRICT=0`）。
5. 浏览器访问 `http://localhost:5666`。

### 11.3 常用运维命令

```powershell
# 查看容器状态
docker ps

# 启动基础服务容器
docker start deploy-nacos-1 my-redis

# 检查服务端口监听
Get-NetTCPConnection -LocalPort 48095,48080,48081,48082 -State Listen

# 查看 Nacos 已注册服务
Invoke-RestMethod "http://127.0.0.1:8848/nacos/v1/ns/catalog/services?namespaceId=dev&pageNo=1&pageSize=50"

# 登录态接口验证（先取 token，再带 token 请求）
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:48080/admin-api/system/auth/login" `
    -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}' `
    -Headers @{"tenant-id"="1"}
$token = $login.data.accessToken
Invoke-RestMethod -Uri "http://localhost:48080/admin-api/bid/certificate/page?pageNo=1&pageSize=10" `
    -Headers @{"Authorization"="Bearer $token"; "tenant-id"="1"}
```

---

## 12. 页面使用说明

### 12.1 登录

- 浏览器打开 `http://localhost:5666`
- 保持「企业登录」标签（租户默认），账号 `admin`，密码 `admin123`

### 12.2 电子证照管理库

1. **证照分类**：左侧菜单 → 招投标管理 → 电子证照管理库 → 证照分类
   - 树形展示，可新增子分类、编辑、删除
   - 分类下存在子分类或证照时禁止删除
   - 可为分类设置默认预警天数
2. **电子证照**
   - 点「新增」：选分类 → 填证照名称/编号 → 选持有人类型（企业/人员）→ 填等级、专业、发证机关 → 选有效期类型（长期有效/固定期限/待确认）→ 固定期限时填到期日期 → **上传附件（必填，可多份，需标注类型：照片/扫描件/原件/年审记录）** → 保存
   - 证照编号重复时系统拒绝并提示，防止重复录入
   - 列表顶部显示状态统计条，点击可快速筛选
   - 状态列显示 🟢有效 / 🟡即将到期(剩 N 天) / 🔴已过期 / ⚫已作废
   - 点「详情」查看附件画廊（在线预览图片/PDF）与变更记录时间轴
   - 勾选多条可批量删除、导出 Excel
3. **变更记录**：查看资质升级、换证、年审的历史留痕；年审等无字段变化的场景可手工登记

### 12.3 业绩证明库

- 新增业绩：填项目名称、业主单位、项目类型、行业、合同金额、工期、承担角色、规模描述 → 上传附件（中标通知书、合同首页、竣工验收报告等）
- 列表支持按行业、项目类型、金额区间、竣工时间筛选，为后续投标人端 AI 检索做准备

### 12.4 模板管理库

1. **模板分类**：维护技术标/商务标等分类（树形）
2. **标书模板**：新增时填名称、适用行业、项目类型、匹配关键词、说明 → 上传 Word 模板文件；支持批量删除、导出 Excel

### 12.5 参数配置

- 调整全局默认预警天数、到期提醒开关、提醒任务执行时间、文件大小上限与扩展名白名单

---

## 13. 验证情况与待验收清单

### 13.1 v1.1 已验证项（如实标注）

| 验证项 | 方法 | 结果 | 说明 |
| --- | --- | --- | --- |
| 建表/初始数据 | pymysql 查 `SHOW CREATE TABLE` | ✅ 通过 | 4 表结构正确，分类 3+2 条 |
| 菜单数据 | 查 `system_menu` | ✅ 通过 | 25 条完整 |
| 后端编译 | javac + 项目 target/classes | ✅ 通过 | 32 个 Java 文件，0 编译错误 |
| 服务启动 | bid-server 48095 监听 + Nacos 注册 | ✅ 通过 | 已注册 |
| 网关路由连通 | GET `/admin-api/bid/certificate-category/page` | ⚠️ **部分通过** | 返回 `{"code":401,"msg":"账号未登录"}`，证明路由转发到位、接口存在且鉴权生效，**但未在登录态验证任何业务逻辑** |
| 前端可访问 | `http://localhost:5666` HTTP 200 | ⚠️ **部分通过** | 服务启动成功，**未验证页面渲染与交互** |
| 菜单显示 | 后台侧边栏截图 | ✅ 通过 | 「招投标管理」已出现 |

> **v1.1 验证口径修正**：v1.1 将"网关返回 401"和"前端 HTTP 200"标记为完全通过（✅），这是高估。实际证据只支持"服务已启动、路由已通、菜单已显示"，**所有 CRUD、文件上传下载、批量操作、导出功能均未经登录态功能验证**。本版如实降级为"部分通过"。

### 13.2 待验收用例清单（v2.0 整改完成后执行）

| # | 用例 | 预期结果 | 优先级 |
| --- | --- | --- | --- |
| 1 | 登录 admin，进入招投标管理 → 电子证照 | 页面正常渲染，列表加载成功 | P0 |
| 2 | 新增证照，**不上传附件**直接保存 | 拒绝保存，提示"请至少上传一份证照照片或原文件" | P0 |
| 3 | 新增证照，上传 1 张照片 + 1 份 PDF，分别标注类型 | 保存成功，列表显示附件角标 (2) | P0 |
| 4 | 新增证照，填写已存在的证照编号 | 拒绝保存，提示"证照编号已存在" | P0 |
| 5 | 新增证照，有效期类型选"固定期限"但不填到期日期 | 拒绝保存，提示到期日期不能为空 | P0 |
| 6 | 详情页预览图片附件 | 正常显示（走受控下载接口，带鉴权） | P0 |
| 7 | **未登录状态直接访问附件下载 URL** | 返回 401，无法下载 | P0（安全） |
| 8 | 用 A 租户 token 访问 B 租户的附件 id | 返回"无权访问该附件" | P0（安全） |
| 9 | 新增证照，到期日期设为 20 天后（分类预警 90 天） | 状态显示 🟡即将到期(剩 20 天) | P0 |
| 10 | 新增证照，到期日期设为昨天 | 状态显示 🔴已过期 | P0 |
| 11 | 修改一条"有效"证照，把到期日期改为昨天，不手动触发任何任务，次日查询 | 状态自动变为 🔴已过期（验证定时刷新或查询重算生效） | P0 |
| 12 | 手工执行 `certificateStatusRefreshJob` | 日志输出处理条数，库内过期证照 status 更新为 2 | P0 |
| 13 | 手工执行 `certificateExpireRemindJob` | 企业管理员收到站内信；同日重复执行不重复推送 | P1 |
| 14 | 编辑证照，将等级从"二级"改为"一级" | 变更记录新增一条"等级升级"记录 | P1 |
| 15 | 编辑证照，将到期日期延后一年 | 变更记录新增一条"年审/续期"记录 | P1 |
| 16 | 删除有子分类的分类 | 拒绝，提示存在子分类 | P1 |
| 17 | 删除有证照引用的分类 | 拒绝，提示该分类下存在证照 | P1 |
| 18 | 勾选 3 条证照批量删除 | 3 条均软删除，附件与变更记录同步软删 | P1 |
| 19 | 导出证照 Excel | 文件可下载，字段完整，**不含附件下载链接** | P1 |
| 20 | 上传 25MB 的文件 | 拒绝，提示超过大小限制 | P1 |
| 21 | 上传 .exe 文件 | 拒绝，提示不支持的文件类型 | P1（安全） |
| 22 | 新增业绩证明，上传中标通知书 | 保存成功，列表可筛选 | P1 |
| 23 | 新增模板，上传 Word 文件 | 保存成功，可受控下载 | P1 |
| 24 | 修改参数配置中的全局预警天数为 60 | 未单独设置预警天数的证照按 60 天判定 | P2 |
| 25 | 证照列表按状态筛选"已过期" | SQL 层过滤，分页正确 | P2 |

### 13.3 验证方法建议

1. **接口层**：用 §11.3 的 PowerShell 脚本先登录取 token，再逐个调用接口验证（避免 v1.1 只测到 401 的问题）。也可用 Postman 导入 knife4j 生成的接口文档。
2. **页面层**：浏览器手动走一遍用例 1~11，截图归档。
3. **安全层**：用例 7、8、21 必须实测，不能只靠代码审查——这是 v1.1 最大的盲区。
4. **定时任务层**：在 XXL-Job 控制台手工触发一次，看执行日志和数据库变化。

---

## 14. 问题清单与整改计划

### 14.1 v1.1 实施过程问题记录（保留，供后续参考）

- **现象**：`APPLICATION FAILED TO START`，核心异常 `Error creating bean with name 'TxgyWebSecurityConfigurerAdapter': Injection of resource dependencies failed`
- **定位方法**：临时将 logback `${LOG_FILE}` 改为固定路径 `D:/bid-server.log`，重跑后拿到完整日志（定位后已还原）
- **根因**：`TxgyWebSecurityConfigurerAdapter` 注入 `List<AuthorizeRequestsCustomizer>`，唯一实现 `WebSocketAuthorizeRequestsCustomizer` 由 `txgy-spring-boot-starter-websocket` 的 `TxgyWebSocketAutoConfiguration` 提供；bid-server 的 `pom.xml` 缺少该依赖，导致注入失败
- **修复**：在 `txgy-module-bid-server/pom.xml` 增加依赖

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>txgy-spring-boot-starter-websocket</artifactId>
</dependency>
```

- **验证**：依赖生效后 bid-server 成功启动、注册 Nacos，网关转发返回 401（未登录，链路已通）
- **经验沉淀**：yudao 体系下新建业务微服务，`starter-websocket` 是**启动必需依赖**（因 Security 配置强依赖其 Customizer），与业务是否用到 WebSocket 无关。后续新增模块应直接照抄该依赖清单。

- **现象**：gateway 启动即报 Nacos 连接错误退出
- **根因**：Nacos/Redis 的 Docker 容器处于 `Exited` 状态，8848/6379 无监听
- **修复**：`docker start deploy-nacos-1`、`docker start my-redis`，Nacos 健康检查通过
- **验证**：Nacos 8848 监听、健康；各服务注册正常

- **现象**：pnpm 版本锁定（`packageManager: pnpm@11.7.0`）导致 corepack 卡在下载
- **修复**：设置 `COREPACK_ENABLE_STRICT=0` 使用全局 pnpm（11.25.0），`pnpm dev` 启动 Vite，端口 5666 监听、HTTP 200

### 14.2 v2.0 整改清单

#### P0 — 必须改（影响正确性与安全）

| # | 问题 | 整改动作 | 涉及位置 | 工作量 |
| --- | --- | --- | --- | --- |
| P0-1 | **证照状态腐化**：`status` 落库但仅 create/update 时写入，无人编辑的证照状态永久失真 | ① 增加每日 `CertificateStatusRefreshJob` 全量刷新；② 分页查询返回前对当页数据重算覆盖 | 新增 `job/`、`BidCertificateServiceImpl` | 0.5 天 |
| P0-2 | **附件公开可下载**：走 infra 标准 URL，无鉴权，企业资质扫描件可被任意下载 | ① 附件表存 `file_key` 而非 URL；② 新增受控下载接口 `/certificate-file/download`，校验权限 + 租户 + 记审计日志；③ 前端预览改 blob 方式 | 新增 `BidCertificateFileController`、前端 4 个页面 | 1.5 天 |
| P0-3 | **"必须上传附件"无实际约束**：文档称硬性要求，但字段可空、无校验 | ① 附件表 `file_key NOT NULL`；② create/update 校验附件数 ≥ 1，抛 `CERTIFICATE_FILE_REQUIRED`；③ 前端表单加 required 规则 | Service + 前端表单 | 0.5 天 |
| P0-4 | **单附件字段无法满足"照片或原文件都要存"** | 新建 `bid_certificate_file` 子表，主表移除 `file_url`/`file_name`，增加 `file_count` 冗余字段 | 建表 + DO/Mapper/Service + 前端上传组件改多选 | 1.5 天 |
| P0-5 | **验证结论下得过满**：无任何接口在登录态跑通，前端未验证交互，却全部标 ✅ | 按 §13.1 如实降级；执行 §13.2 的 25 条用例（至少 P0 部分 11 条），截图归档 | 测试执行 | 1 天 |
| P0-6 | **SQL/Java 代码块被转义污染**，复制执行必失败 | 使用本文档 §4.10、§4.11 的清理版本 | 文档 | ✅ 本版已完成 |

#### P1 — 应该改（影响后续阶段与数据质量）

| # | 问题 | 整改动作 | 工作量 |
| --- | --- | --- | --- |
| P1-1 | **缺业绩证明数据模型**：需求明确要求 AI 调取"业绩证明"填标书 | 新建 `bid_performance` 表 + 完整 CRUD + 页面 | 2 天 |
| P1-2 | **无换证/升级历史**：update 直接覆盖，丢失投标时点有效资质版本 | 新建 `bid_certificate_change_log` 表 + 自动检测写入 + 手工登记接口 + 详情页时间轴 | 1.5 天 |
| P1-3 | **证照编号无唯一约束**：同一张证可重复录入 | 加 `UNIQUE KEY uk_code (code, tenant_id, deleted)` + Service 层 `validateCodeUnique` | 0.5 天 |
| P1-4 | **人员证书无法按人/按专业检索**：`holder_name` 是纯字符串 | 增加 `major`（本期启用）+ `holder_id`（预留，待团队成员管理落地回填） | 0.5 天 |
| P1-5 | **30 天阈值硬编码**，无法适配不同证照的预警周期 | 新建 `bid_config` 表 + 三级取值（证照 → 分类 → 全局）+ 参数配置页面 | 1 天 |
| P1-6 | **`expire_date` 为空即判"有效"**，混淆"长期有效"与"漏填" | 增加 `validity_type` 字段 + 前端联动 + 状态计算分支 | 0.5 天 |
| P1-7 | **无主动到期提醒**：只有列表标签，管理员不打开页面就不知道 | 新增 `CertificateExpireRemindJob` + Feign 调用 system 站内信 + Redis 去重 | 1 天 |
| P1-8 | **菜单 ID 硬编码 6735~6759**，多环境同步易撞号 | 改用 `SELECT max(id)` 动态生成，或登记到项目 ID 分配表并在迁移脚本中显式声明 | 0.5 天 |
| P1-9 | **分类单层平铺**，证照量大后难定位 | 增加 `parent_id` + 树形接口 + 前端 TreeSelect/树形表格 + 父级环路校验 | 1 天 |

#### P2 — 可以后置

| # | 事项 | 说明 |
| --- | --- | --- |
| P2-1 | 文件下载审计日志表 `bid_file_download_log` | 追溯敏感证照访问行为 |
| P2-2 | 批量导入证照（Excel 模板 + 附件打包上传） | 存量证照迁移时提效明显 |
| P2-3 | 附件打包下载（zip） | 导出证照时一并下载附件 |
| P2-4 | 私有桶 + 预签名 URL | 安全性与性能进一步优化（§6.2 方案 C） |
| P2-5 | 证照 OCR 识别 | 上传后自动识别名称/编号/有效期，减少手工录入 |
| P2-6 | 附件加水印 | 对外分享时叠加"仅供 XX 项目投标使用"水印 |
| P2-7 | 模板 `use_count` 统计与推荐排序 | 依赖投标人端落地 |

### 14.3 陕西官方核实后的增量整改项（本版新增）

> 依据配套文档《陕西热力防腐保温企业证照清单与官方查验渠道.md》的联网核实结果，v2.0 方案在陕西本地口径下仍存在以下缺口。

#### P0-7：附件白名单缺 ofd 格式，陕西电子资质证书无法上传

- **事实**：陕西省住建领域企业资质电子证书（安全生产许可证、建筑业企业资质、工程勘察/设计/监理资质、工程质量检测机构资质、房地产估价机构备案证书）**以加盖"陕西省住房和城乡建设厅"电子签章的 OFD 格式文件为载体**，A4 竖版。
- **v2.0 缺陷**：白名单为 `jpg/jpeg/png/bmp/pdf/doc/docx/xls/xlsx`，**不含 ofd**。直接后果是企业在陕西政务服务网下载的资质电子证书传不进系统，"照片或原文件都要存进去"的硬性要求在最重要的证照上失效。
- **整改**：① 白名单增加 `ofd`（已在 §4.5、§4.9、§4.11 修正）；② 前端提供 OFD 预览方案（开源 ofd.js 渲染，或退化为提示下载后本地打开）。
- **工作量**：0.5 天（含前端预览调试）。

#### P0-8：分类结构存在语义错误与主业资质遗漏

- **事实 1**：「项目承包等级」不是独立证照类型。经查证，承包等级就是各类资质证书上的等级标注（防水防腐保温专业承包一级/二级、市政公用工程施工总承包特/一/二/三级、建筑机电安装专业承包一/二/三级、特种设备生产许可 A级/B级），与「企业资质」分类**完全重叠**——同一张"市政公用工程施工总承包二级"证书会同时属于两个分类，造成归类混乱。
- **事实 2**：**遗漏「防水防腐保温工程专业承包资质」**。这是"防腐保温"企业最核心的主业资质（分一级、二级，原三级已取消），v1.1 和 v2.0 通篇只写了"市政公用工程施工总承包"，等于漏了主赛道。
- **事实 3**：另遗漏 3 类陕西必需证照——**特种设备使用登记证**（锅炉按台套登记，与生产许可证是两回事）、**排污许可证**（热力生产和供应属发证行业，吉林已有供热企业燃煤锅炉无证排放被处罚的执法典型案例）、**外省企业进陕承揽业务登记**（若企业注册地非陕西，这是在陕投标的前置门槛，电子登记表无纸质证）。
- **整改**：采用 §4.11 修正后的 6 大类 + 21 个二级分类结构；取消「项目承包等级」独立分类，等级信息改由 `level` + `level_standard` 字段承载。
- **工作量**：0.5 天（SQL 与前端级联选择器调整）。

#### P1-10：`level` 字段必须应对资质等级口径的政策过渡期矛盾

- **矛盾事实**：现行标准仍是《建筑业企业资质标准》（建市〔2014〕159号），等级为**特级/一级/二级/三级**；而《建设工程企业资质管理制度改革方案》（建市〔2020〕94号，已从住建部官网核实原文）明确"施工总承包资质、专业承包资质等级**原则上压减为甲、乙两级**，10 类特级资质调整为施工综合资质，设置 1 年过渡期后按新旧对应关系简单换证"。但落地时间存在矛盾：部分来源称 2026 年 3 月起已取消三级、新办可直接申请乙级；另有来源称甲乙级划分仅为改革规划、**截至 2026 年 4 月新资质标准尚未正式出台**，现行审批中三级资质依然存在且是新手企业新办的核心选择。住建部官网 2026 年仍在按批次发布"建筑业企业资质核准名单"。
- **设计影响**：`level` **绝不能用枚举或字典硬约束**，必须 varchar 自由文本，同时容纳两套口径；增加 `level_standard` 标注等级所属标准版本，否则改革落地换证时数据会错乱（"二级"换领后变"乙级"，若无版本标记会被误判为降级并写入错误的变更记录）。
- **整改**：① 已增加 `level_standard` 字段；② 变更记录的 `compareLevel` 升降级判断逻辑需按 `level_standard` 分别使用两套等级序，禁止跨标准比较；③ 待住建部正式发布新资质标准后再补字典表。
- **工作量**：1 天（含升降级判断逻辑与测试）。

#### P1-11：证照有效性不只取决于到期日期，需增加非日期型失效状态

- **事实 1**：陕西省自 **2026-03-26** 起对全省施工企业安管人员配备数量和变更情况**动态核查**，安全生产许可证会被平台标注预警——**黄色预警**（A/B/C 三类安管人员中任一类数量不达标）、**红色预警**（任一类数量为 0，劳务分包 B 证除外）、**失效预警**（三类人员数量全部为 0），并通过**短信**通知企业。这意味着安许证可能在**到期日之前就已失效**。
- **事实 2**：安管人员变更受聘企业时，平台校验其**前 12 个月变更次数**，达 **4 次及以上**的标注为期**一年"异常"**。
- **事实 3**：特种设备作业人员证（G1/G2/G3 司炉证）除 4 年有效期外，复审合格还要求"**持证期间从事持证项目的作业时间连续中断未超过 1 年**"——这是纯日期算不出来的失效条件。
- **事实 4**：ISO9001 等管理体系认证证书有效期约 3 年，但**前提是企业接受认证机构监督审核（年审）**，年审频率一般每 12 个月一次（部分机构要求 6 或 10 个月），年审逾期证书会被暂停或撤销。这与"到期日期"是两个独立时间维度。
- **设计影响**：v2.0 的 `calculateStatus` 只按 `expire_date` 与 `warn_days` 计算，会漏判上述所有情形，把已失效的证照显示为"🟢有效"，直接导致投标资格误判。
- **整改**：① 已增加 `risk_flag`（风险标记）与 `annual_review_date`（年审/复审日期）字段；② `calculateStatus` 增加分支：`risk_flag` 为红色/失效预警时直接返回状态 2（已过期）或新增状态 4（已失效）；③ 年审提醒独立于到期提醒，新增 `CertificateAnnualReviewRemindJob`；④ 安许证详情页展示关联的 A/B/C 三类人员证书数量与达标情况。
- **工作量**：2 天。

#### P1-12：官方查验跳转必须白名单化，禁止自由填写 URL

- **事实**：国家市场监督管理总局官方声明，为防范假冒网站（`www.cnsx-gov.cn`、`www.cnse.org`、`www.zjcnse-gov.cn`、`cnvse-gov.cn/chaxun` 等），特种设备查询正版网址**仅** `https://cnse.e-cqs.cn/info-pub/pub` 或 `https://cnse.samr.gov.cn/info-pub/pub`。
- **风险**：招投标场景下核验证照真伪是高频动作。若系统提供"查验网址"输入框让员工自行填写，等于为钓鱼网站开了内部入口，可能导致企业证书信息或个人身份证号泄露。
- **整改**：① 查验平台地址存入 `bid_config` 白名单（已在 §4.11 落库）；② 前端"一键核验"按钮按证照的 `verify_platform` 字段查白名单跳转，**不提供 URL 输入框**；③ 白名单修改需 `bid:config:update` 权限并记录操作日志。
- **工作量**：0.5 天。

#### P1-13：排污许可证的延续时限是法定硬约束

- **事实**：《排污许可管理条例》第十四条——排污许可证有效期届满，排污单位需要继续排放污染物的，应当于**有效期届满 60 日前**向审批部门提出申请。有效期届满未延续的，核发环保部门应当依法办理注销手续并在全国排污许可证管理信息平台上公告，原许可证自动作废。
- **风险**：若该证照沿用全局默认预警天数（90 天）看似够用，但一旦管理员在 `warn_days` 上手工调低（如 30 天），就会错过法定申请窗口，导致许可证被注销——这是**不可逆的合规事故**，且注销信息会在全国平台公开。
- **整改**：① 分类默认预警天数设为 90（已在 §4.11 落实）；② 增加**最小预警天数下限校验**：排污许可证类的 `warn_days` 不得小于 60，低于则拒绝保存并提示法定时限；③ 建议在 `bid_config` 中为"法定时限型证照"单独配置下限映射。
- **工作量**：0.5 天。

#### P2-8：特种设备使用登记证按台（套）管理，宜独立设备台账表

- **事实**：锅炉、压力容器（气瓶除外）、电梯、起重机械等应当**按台（套）**向登记机关办理使用登记；《陕西省特种设备安全监察条例》规定省、市、县三级市场监管部门分级负责安全监察，**例外**是房屋建筑工地和市政工程工地用起重机械、场（厂）内专用机动车辆由建设行政主管部门监管。生产单位主要负责人为安全第一责任人，须每年报送安全生产情况。
- **问题**：一家热力集团可能有几十台锅炉/压力容器，每台一证。若混入 `bid_certificate`，证照列表会被大量同质记录污染，且"按台套"的定期检验周期与"按企业"的资质有效期管理逻辑不同。
- **建议**：后续新建 `bid_equipment`（设备台账）表，字段含设备名称、设备代码/注册代码、规格参数、安装位置、使用登记日期、定期检验到期日期，通过附件子表关联使用登记证。本期可先在分类上单列，暂用证照表承载。

#### P2-9：陕西人员证书业务存在"待企业确认"中间态

- **事实**：依据**陕建质发〔2025〕43号**（陕西省住建厅 2025-10-17 发布，2025-10-20 起执行），陕西省建筑施工企业安全生产考核合格证书、特种作业人员证书的新申请、变更（省内、跨省）、延期、注销，全部按 **"个人申请、企业确认、行政许可"** 流程办理：由持证人通过陕西政务服务网或秦务员 APP **个人账户**发起，经**所在企业账户核实确认**后进入行政审批程序。**陕西政务服务网个人账户为证书业务办理的唯一合法入口**；证书信息纠错需持证人带证明材料到陕西省政务服务大厅住建分中心（西安市新城区东新街平安银行四楼）现场办理。
- **建议**：人员证书记录增加 `biz_status`（未发起/个人已申请/待企业确认/已行政许可/已发证）字段，让企业管理员能看到哪些证书卡在"待企业确认"环节——这是企业侧必须主动处理的动作，逾期会导致证书延期失败。
- **✅ 已落库（2026-09-07 综合评审）**：`biz_status tinyint NOT NULL DEFAULT 0` 已补入 §4.4 字段表与 §4.10 建表 SQL；取值 0 未发起 / 1 个人已申请 / 2 待企业确认 / 3 已行政许可 / 4 已发证，仅人员证书（`holder_type=2`）使用，企业端列表按"待企业确认"筛选并高亮提醒。

### 14.4 增量整改工作量小结

| 优先级 | 项数 | 工作量 |
| --- | --- | --- |
| P0（含陕西增量 P0-7、P0-8） | 8 | 约 6 天 |
| P1（含陕西增量 P1-10~P1-13） | 13 | 约 13 天 |
| P2（含陕西增量 P2-8、P2-9） | 9 | 待排期 |
| **P0 + P1 合计** | — | **约 19 人天** |

### 14.5 落地前必须确认的信息缺口

| # | 缺口项 | 影响 | 确认渠道 |
| --- | --- | --- | --- |
| 1 | ❌ 陕西集中供热经营许可证的**有效期年限** | 预警天数设定（当前按最保守 180 天） | 西安市城市管理和综合执法局行政审批处 029-86785391 |
| 2 | ❌ 特种设备生产许可证（锅炉）的**有效期年限** | 同上 | 陕西省特种设备检验检测研究院 |
| 3 | ⛔ 建筑业资质**甲乙级改革正式落地时间与新资质标准** | `level_standard` 枚举设计 | 住建部建设工程企业资质申请政策咨询 zzsqzx.mohurd.gov.cn |
| 4 | ❌ 安全生产许可证**有效期 3 年**的官方依据（当前仅用户提供的行业资料单源） | 预警天数 | 陕西省住建厅官网 → 政务服务 → 企业库 → 资质查询 |
| 5 | ❌ **热力工程师证**的性质与查验渠道 | 人员证书分类归属 | 陕西省人事考试中心 029-68548236 |
| 6 | ❌ 陕西**现场管理人员岗位证书**查验入口 | 人员证书核验功能 | 陕西省住建厅综合服务中心 |
| 7 | ⚠️ ISO9001 有效期与年审周期为**单源**（第三方认证机构网站） | 年审提醒设计 | 国家认监委 cx.cnca.cn |
| 8 | ❌ 天信管业**注册地是否在陕西** | 决定"进陕承揽业务登记"是否必需 | 内部确认 |

### 14.6 整改建议顺序

```
第 1 步：P0-4 附件子表建表 + P0-2 受控下载（这两项耦合，一起做）
第 2 步：P0-3 附件必填校验 + P1-3 编号唯一 + P1-6 有效期类型（都是主表校验层，一起改）
第 3 步：P0-1 状态刷新任务 + P1-5 阈值配置化 + P1-7 主动提醒（都是到期提醒链路）
第 4 步：P1-1 业绩证明 + P1-2 变更记录（新增业务对象）
第 5 步：P1-9 分类树形 + P1-4 人员字段（增强检索能力）
第 6 步：P0-5 执行 25 条验收用例，截图归档，更新验证章节
```

> 说明：以上为单人开发估算，实际需结合团队对 yudao 框架的熟悉度调整。P0 部分建议优先排入本迭代，因为 P0-2（附件安全）和 P0-1（状态腐化）是会被验收直接发现的缺陷。

---

## 15. 后续规划（非本次范围）

1. **投标人端标书制作**：导入招标文件 → AI 智能解析（资质要求、技术参数、评分标准）→ 前置条件检查（AI 检索证照库判断是否满足，不满足则给出"补齐缺失证照"或"放弃投标"两种方案）→ 模板智能推荐 → AI 从证照库/业绩库调取内容填充标书 → 合规自查生成风险报告。
   - **本期数据模型已为此预留**：`major`（专业检索）、`bid_performance`（业绩检索）、`match_keywords`（模板推荐）、`expire_date` + `status`（资质有效性校验）。
2. **团队成员管理**：企业管理员端成员与权限管理；落地后回填 `bid_certificate.holder_id`，实现证书到期提醒定向推送给持证人。
3. **核心 KPI 卡片**：企业管理员首页展示本年投标总数、中标率、进行中项目数、待处理保证金；证照状态统计接口 `/certificate/status-summary` 可直接复用。
4. **评审专家权限开放**：后期做招标时，对评审专家开放只读评审权限。
5. **证照 OCR 识别**：上传证照图片后自动识别名称/编号/有效期。
6. **文件存储升级**：迁移至 MinIO 私有桶 + 预签名 URL（§6.2 方案 C）。

---

## 附录 A：SQL 脚本清单

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `bid_schema_v2.sql` | 8 张表建表（§4.10） | 待执行 |
| `bid_init_data_v2.sql` | 分类 + 配置初始数据（§4.11） | 待执行 |
| `bid_menu_v2.sql` | 菜单（含业绩库、变更记录、参数配置） | 待编写 |
| `bid_migrate_v1_to_v2.sql` | **v1.1 → v2.0 数据迁移**（附件字段迁移至子表） | 待编写，见附录 C |

## 附录 B：关键路径速查

| 项 | 路径 |
| --- | --- |
| 后端模块 | `E:\WORKSPACE\天信管业防腐保温智慧平台\txgycloud\txgy-module-bid` |
| 前端页面 | `E:\WORKSPACE\...\txgy-ui-admin-vben\apps\web-antd\src\views\bid` |
| 前端 API | `E:\WORKSPACE\...\txgy-ui-admin-vben\apps\web-antd\src\api\bid` |
| 网关配置 | `E:\WORKSPACE\...\txgy-gateway\src\main\resources\application.yaml` |
| 代码规范参照 | fms 模块（`txgy-module-fms`） |
| 需求文档 | 《2026.8.20 天信管业防腐保温智慧平台开发需求方案.docx》模块九 |
| 行业证照参考 | 《证书资格业务项目.docx》 |
| 任务原文 | `1(2).txt` |

## 附录 C：v1.1 → v2.0 数据迁移脚本

```sql
-- 1. 证照附件迁移：主表 file_url/file_name → 附件子表
INSERT INTO bid_certificate_file
    (biz_type, biz_id, file_type, file_name, file_key, sort, tenant_id, creator, create_time)
SELECT 1, id, 2, IFNULL(file_name, '证照文件'), file_url, 0, tenant_id, creator, create_time
FROM bid_certificate
WHERE file_url IS NOT NULL AND file_url <> '' AND deleted = b'0';

-- 2. 回填附件数量
UPDATE bid_certificate c
SET file_count = (SELECT COUNT(*) FROM bid_certificate_file f
                  WHERE f.biz_type = 1 AND f.biz_id = c.id AND f.deleted = b'0');

-- 3. 模板附件迁移
INSERT INTO bid_certificate_file
    (biz_type, biz_id, file_type, file_name, file_key, sort, tenant_id, creator, create_time)
SELECT 2, id, 3, IFNULL(file_name, '模板文件'), file_url, 0, tenant_id, creator, create_time
FROM bid_template
WHERE file_url IS NOT NULL AND file_url <> '' AND deleted = b'0';

UPDATE bid_template t
SET file_count = (SELECT COUNT(*) FROM bid_certificate_file f
                  WHERE f.biz_type = 2 AND f.biz_id = t.id AND f.deleted = b'0');

-- 4. 分类表增加 parent_id 后，存量数据默认为一级分类（parent_id = 0），无需额外处理

-- 5. 证照表 validity_type 回填：有 expire_date 的设为固定期限，无的设为待确认
UPDATE bid_certificate SET validity_type = 2 WHERE expire_date IS NOT NULL;
UPDATE bid_certificate SET validity_type = 3 WHERE expire_date IS NULL;

-- 6. 迁移完成后，确认无误再删除主表旧字段（建议保留一个版本周期作为回滚保险）
-- ALTER TABLE bid_certificate DROP COLUMN file_url, DROP COLUMN file_name;
-- ALTER TABLE bid_template    DROP COLUMN file_url, DROP COLUMN file_name;
```

> **迁移注意**：
> 1. 执行前务必备份 `ruoyi-vue-pro` 库。
> 2. `file_url` 存的是完整 URL，`file_key` 期望的是存储路径。若文件服务已按 URL 组织，需确认 `fileApi.getFileContent(file_key)` 能接受完整 URL；否则要在迁移时用字符串截取把 URL 转为 path。
> 3. 第 6 步的 DROP COLUMN 建议推迟到 v2.0 稳定运行一个迭代后再执行，保留回滚能力。

---



---

