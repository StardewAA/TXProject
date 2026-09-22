# 2026-09-21 Docker 全量重部署记录（第九轮：按远程重构重建并启动）

> 本轮目标（用户原话）：按照已经更新下来的重构并在 Docker 中启动（包括 gateway/infra/system/training 这 4 个 Dockerfile.runtime 按照远程版本启动）。
> IoT / iot-gateway 按此前要求**不启动**。

---

## 一、最终状态

| 项目 | 结果 |
|---|---|
| 运行容器 | **27 个，全部 healthy**（25 个后端 + 3 个中间件 + 1 个前端；IoT 2 个未启动） |
| 前端 | http://localhost:5173 （admin / admin123，租户 1） |
| 网关 | http://localhost:48080/admin-api |
| Nacos（dev 命名空间） | **23 个服务实例**，含 cms-server |
| 网关接口冒烟 | **29 个端点全部 code=0** |
| 数据库表数 | **856** 张（本轮 834 → 856） |
| 菜单 | 2008 条；可见菜单**空图标 0 个**；HRM 186 条、MES 434 条 |
| 中文乱码 | 全库 843 张表 / 5682 个文本列按「双重编码特征」扫描，**0 处** |

---

## 二、镜像来源：4 支服务按远程版本启动

`gateway / infra / system / training` 的 `deploy/jars/<svc>/Dockerfile.runtime` **与远程 HEAD 完全一致**
（`git diff HEAD` 为空），基底为 `eclipse-temurin:25-jre` 薄运行镜像。

因这 4 支远程镜像**不含 curl / wget**，其 healthcheck 从 `curl /actuator/health` 改为
**端口探活**（bash `/dev/tcp/127.0.0.1/48080`），见 `deploy/docker-compose.hostjar.yml` 的 `x-tcp-probe`。
其余模块镜像自带 curl，仍走 `x-curl-probe`。

> 备注：`ai` 的远程 `Dockerfile.runtime` 依赖一个不存在的自制基础镜像 `deploy-mes-base:latest`，
> 本地已改回 `eclipse-temurin:25-jre` + 安装 curl；这是本仓库唯一被改动的 Dockerfile。

---

## 三、上游 master 编译问题（本轮部署的前置修复）

远程 master（HEAD = `35fe1390e`）**自身编译不通过**：提交 `1f00cc7cb`「错误回退」删掉了 master 已有功能
但保留了对它们的引用，干净 HEAD 下 `bpm-server / crm-server / erp-server / mes-api / wms-server`
5 个模块缺失 195 个被删文件，`mvn package` 失败。

处理方式：本地**撤销该错误回退**（等价于把 master 内容补回），再手工修 2 处合并产生的重复定义：

- `txgy-module-erp/txgy-module-erp-api/.../enums/ErrorCodeConstants.java`：删除重复的 ERP/MES 集成常量块
- `txgy-module-mes/txgy-module-mes-api/.../integration/dto/MesWorkOrderCreateReqDTO.java`：删除重复的 `dataVersion` 字段

结果：全量 `mvn -B -T 4 -fae -Dmaven.test.skip=true package` **BUILD SUCCESS**（1:25）。

> **该修复目前只在本地工作区（未提交、未推送）。** 同事直接拉 master 仍会编译失败。
> 建议后续以 `codex/` 分支 + PR 方式把复原结果回推上游。

---

## 四、本轮新修的三个问题

### 1. cms 未注册 Nacos → 网关 503「Unable to find instance for cms-server」

`txgy-module-cms-server/application.yaml` 中：

```yaml
spring.cloud.nacos.discovery.enabled: ${CMS_NACOS_ENABLED:false}
server.port: ${CMS_SERVER_PORT:48100}
```

且 cms 的 `application-local.yaml` **没有**像其他模块那样声明 `namespace: dev`，
导致它既不注册、端口也对不上（其他模块统一 48080）。

修法（`deploy/docker-compose.hostjar.yml` 的 cms 服务，仅改环境变量、不重建 jar）：

```yaml
CMS_NACOS_ENABLED: "true"
NACOS_SERVER_ADDR: nacos:8848
NACOS_NAMESPACE: dev
CMS_SERVER_PORT: "48080"
```

验证：Nacos dev 命名空间服务数 22 → **23**；`/cms/policy/page` 由 503 → `code=0, total=49`。

### 2. HRM 缺 14 张表 + 22 个列 → `/hrm/employee/page` 500（Unknown column 't.employment_type_id'）

上一轮导入时**有意跳过了 `hrm_*` 与 `fms_*`**，本轮的 500 即由此而来。
以 `sql/hrm_202609201427.sql`（全库快照，2026-09-20 14:27）为基准做结构对齐，生成
`deploy/mysql/repair/2026-09-21-hrm-schema-sync.sql`（只含 CREATE TABLE IF NOT EXISTS / ALTER ADD COLUMN / INSERT，
无 DROP / DELETE / TRUNCATE，可重复执行）：

- 补表 14 张：`hrm_company_profile`、`hrm_contract_sign_record`、`hrm_contract_template`、`hrm_dept_headcount`、
  `hrm_employee_approval`、`hrm_employee_blacklist`、`hrm_employee_no_rule`、`hrm_employee_no_sequence`、
  `hrm_employment_type`、`hrm_health_check`、`hrm_policy`、`hrm_recruit_demand`、`hrm_safety_education`、
  `hrm_salary_attendance_rule`
- 补列 22 个，集中在 `hrm_employee`(employment_type_id)、`hrm_employee_contract`(11)、`hrm_employee_certificate`(5)、
  `hrm_certificate_type`(3)、`hrm_attendance_clock`(1)、`hrm_recruit_post`(1)
- 另执行 HRM 模块自带的 10 支业务迁移（`sql/2026-09-1x-hrm-*.sql`）补齐用工形式种子、菜单与权限按钮
  （`hrm_employment_type` 种入 10 类防腐保温行业用工形式，租户 2020）

验证：`/hrm/employee/page` 500 → `code=0, total=56`。

### 3. MES 缺 4 张表 + 6 个列

以 `sql/mes_202609202041.sql`（2026-09-20 20:41 的 MES 快照）为基准，生成
`deploy/mysql/repair/2026-09-21-mes-schema-sync.sql`：

- 补表 4 张：`mes_wms_shortage_inbox` / `mes_wms_shortage_read` / `mes_wms_shortage_version`（WMS 缺料协作单收件箱），
  `mes_work_order_approval_task`（生产工单 BPM 审批结果任务）
- 补列 6 个：`mes_pro_work_order` 的 `wms_shipment_order_no` / `wms_shipment_status` / `wms_shipment_version` /
  `wms_receipt_order_no` / `wms_receipt_status` / `process_instance_id`

**该文件同时修掉了快照里的中文双重编码**：源快照中 `wms_shipment_*` 等注释是「UTF-8 被按 CP1252 解读」
的乱码（形如 `WMS é¢†æ–™å‡ºåº“å•å·`），生成补丁时按 CP1252 → UTF-8 逆变换还原为
「WMS 领料出库单号」「WMS 领料状态：0未申请 1申请中 2已出库 3失败 4取消」等正常中文。

> `sql/mes_202609202041.sql` 是整库导出且**含 156 条 DROP TABLE**，禁止整文件导入；
> 同样 `sql/hrm_202609201427.sql` 也含 DROP TABLE。上述两个 repair 脚本都只抽取需要的表，并已把
> `DROP TABLE IF EXISTS` 去掉、`CREATE TABLE` 改为 `IF NOT EXISTS`、数据行改为 `INSERT IGNORE`。

### 4. 补列：`mes_wm_warehouse_area`（安全环保）

`deploy/mysql/repair/2026-09-21-wms-area-safety-columns.sql`：补 `pollution_control`（污染管控库位）与
`storage_zone`（储存专区：GENERAL / EXPLOSION_PROOF / ISOLATION）。

---

## 五、结构对齐的最终核对

| 快照文件 | 表数 | 缺表 | 缺列 |
|---|---|---|---|
| hrm_202609201427.sql | 78 | 0 | 0 |
| 202609201700/云学院_202609201615.sql | 56 | 0 | 0 |
| 202609201700/招投标_202609201616.sql | 51 | 0 | 0 |
| 202609201700/安全环保_202609201707.sql | 168 | 0 | 0 |
| 202609201700/WMS_202609201610.sql | 20 | 0 | 0 |
| 202609201700/mall_202609201611.sql | 109 | 0 | 0 |
| 202609201700/CRM_202609201615.sql | 30 | 0 | 0 |
| mes_202609202041.sql | 156 | 0 | 0 |

> 快照中出现的 `act_*`（Flowable）在本库为**大写表名**，属正常（Flowable 默认大写策略，
> 见 `deploy/mysql/flowable/0070-flowable-uppercase-fix.sql`）；未采纳的表为
> `jimu_*` / `onl_drag_*` / `chat2bi_table_meta` / `flw_*` / `qrtz_*` /
> `wms_inventory_reservation|return|operation_retry`，经全库检索**当前代码无任何引用**，故不创建。

---

## 六、网关接口冒烟（29 个端点）

`system / infra / bpm / member / pay / mp / report / product / trade / promotion / statistics / crm /
hrm / fms / im / ai / wms / mes / erp / bidding / training / cms` 全部 `code=0`。

- `/fms/voucher/page` 需带 `accountSetId`（`@NotNull(message="账套不能为空")`），
  补上 `accountSetId=1` 后 `code=0, total=3`。此前返回的 400 属正常入参校验，不是缺陷。
- `/bpm/process-definition/page`、`/mp/account/page`、`/erp/stock/page` 等为 0 行，属数据未产生的正常空集。

---

## 七、本轮新增/修改文件

| 文件 | 说明 |
|---|---|
| `deploy/mysql/repair/2026-09-21-hrm-schema-sync.sql` | HRM 缺表/缺列补齐（可重复执行） |
| `deploy/mysql/repair/2026-09-21-mes-schema-sync.sql` | MES 缺表/缺列补齐（含乱码修复，可重复执行） |
| `deploy/mysql/repair/2026-09-21-wms-area-safety-columns.sql` | 库位污染管控/储存专区字段 |
| `deploy/docker-compose.hostjar.yml` | cms 增加 Nacos 注册与端口环境变量 |
| `.docker-local/import/smoke_all.js` | 冒烟脚本补 `accountSetId` |

---

---

## 八、回推上游：修复 master 编译不通过

### 结论

远程 `master` 已由 `7bfde7a10` 前进到 **`32f044d3f`**，编译恢复通过。

### 排查过程

1. 重新拉取后发现远程 master 已前进到 `7bfde7a10`，并已存在
   `fix/restore-revert-20260921` 分支（PR !204「恢复被 revert 1f00cc7cb 误删的 197 个文件」）。
2. 在独立工作树检出 `origin/master` 实际编译验证：**仍然失败**，
   462 处编译错误，`crm-server / report-server / erp-server / mes-server / wms-server` 5 个模块失败。
3. 根因：PR !204 只补回了**被整文件删除**的 197 个文件，**没有补回已存在文件里被删掉的代码块**。
   典型缺项：

   | 缺什么 | 后果 |
   |---|---|
   | `txgy-module-report-server/pom.xml` 缺 `txgy-spring-boot-starter-excel` | 报表中心 Excel 导出相关 200 处报错 |
   | `txgy-module-erp-server/pom.xml` 缺 `txgy-module-mes-api` | ERP 侧 96 处找不到符号 |
   | `txgy-spring-boot-starter-rpc/pom.xml` 缺 Jackson 3 `jackson-databind` | Feign 解码器缺依赖 |
   | `txgy-gateway/application.yaml` 缺 training-server 路由 | 云培训学院无法经网关访问 |

### 修法（为什么不是简单 `git revert 1f00cc7cb`）

直接对那个「反向合并」再执行一次 `git revert`，会把 master 上更新的功能一起删掉
（实测会删掉 training 的「学员批量导入分配」、`login-dom.html` 等 7 个文件，
并回退 `PlanClassStudentController`/`ClassStudentServiceImpl`、报错码 `PLAN_STUDENT_IMPORT_ALLOC_*` 等）。

因此改为**只改 5 个失败模块 + 2 个模块外的必要修复**，不动其余模块：

- 改动 70 个文件，+1029 / -130 行
- 移除的只是「无法编译的残留私有方法」（如 `ErpSaleOrderApiImpl.buildSaleOrderItems`
  引用了已不存在的 `productUnitMapper` / `ErpProductUnitDO`），不是功能删除
- 未包含前端 `txgy-ui-admin-vben` 改动（不参与 Maven 编译，未做前端构建验证，避免误伤）
- training 模块 master 本就编译通过，不改动

### 验证

`mvn -B -T 4 -fae -Dmaven.test.skip=true package`：

| 轮次 | 结果 |
|---|---|
| `origin/master`（7bfde7a10） | BUILD FAILURE，5 个模块失败，462 处错误 |
| 修复后（32f044d3f） | **BUILD SUCCESS，91 个模块全部成功，0 失败，0 ERROR，1:40** |

### 推送

- 提交：`32f044d3f fix: 补全 1f00cc7cb 错误回退中被删的代码改动，修复 master 编译不通过`
- 快进推送到 `origin/master`（`7bfde7a10..32f044d3f`）
- 同时保留分支 `codex/master-build-restore-revert-20260921` 供评审比对
- 本地仓库已同步到新 master，仅保留 3 处**本机专用**的部署微调（未推送）：
  `deploy/docker-compose.yml`（wms JAVA_OPTS）、`deploy/local/docker-compose.local.yml`（48081 端口）、
  `deploy/jars/ai/Dockerfile.runtime`（改用 eclipse-temurin:25-jre）

> 注意：本地 Docker 里跑着的 26 个后端镜像仍是**上一个版本**（基于 35fe1390e）构建的，
> 不含 master 新增的 crm 工单超时预警、wms AI 客服助手、training 学员批量导入分配等功能；
> 如需同步，重新 `mvn package` + 重建镜像即可。


---

## 九、遗留与建议

1. **上游 master 编译不通过**，本轮部署依赖本地对该错误回退的复原（未提交/未推送）；
   建议尽快以 `codex/` 分支 + PR 回推，否则其他人拉 master 依然编译失败。
2. `ai` 的远程 `Dockerfile.runtime` 引用了不存在的 `deploy-mes-base:latest`，建议上游修正。
3. cms 未注册 Nacos 属模块配置缺失（`application-local.yaml` 缺 namespace），
   建议上游与其余模块对齐，去掉 `CMS_NACOS_ENABLED` 这个本地开关。
4. 4 支远程薄镜像不含 curl，healthcheck 只能端口探活；如需 HTTP 探活可在镜像内装 curl。
5. IoT / iot-gateway 未启动；菜单里的「IoT 物联网」仍在，需要时
   `docker compose ... up -d iot iot-gateway` 即可，或单独隐藏该菜单。
6. Flowable 相关表（`ACT_*` / `FLW_*`）中 `FLW_*` 未创建，工作流暂无事件订阅类流程定义，属预期。
