# 后端变更请求：主体接口 subjectType 去必填（一租户一档）· 落地核对清单 —— 2026-09-28

> 提出方：前端（招投标模块）。
> 前置：业务/接口口径已在《后端变更请求-主体档案合并一租户一档-2026-09-20.md》定稿；迁移 SQL 已出（`sql/history/2026-09-27-subject-profile-one-per-tenant.sql`）；**前端已完全按新契约落地**。
> 本单目的：实码核对现状后，给后端列出**尚差的后端代码改动点**（精确到文件/方法），并附验收标准。**不改后端代码，仅提供跟进依据。**

---

## 一、现状核对（2026-09-28 实码核对）

| 层 | 状态 | 说明 |
|---|---|---|
| 契约文档 | ✅ 已定稿 | 09-20「一租户一档」变更请求 §2.1~2.4（唯一键合并、接口去类型、资格门、经办人可选） |
| DDL 迁移 | ✅ 已出 | 09-27 脚本：①合并存量双行（保留较新/complete_flag=1 一行，其余软删留痕）②唯一键改 `(tenant_id,deleted)` |
| 前端 | ✅ 已按契约 | `fetchSubjectProfile()` / `fetchSubjectEligibility()` 无类型调用；主体档案页单档化；cert-form / material-check / safety-risk 单次无类型调用 |
| **后端代码** | ❌ **未落地** | Controller/Service 仍强制 `subjectType` 必填 + 按类型分档读写 |

**结论：契约与 SQL 已就绪，前端已就绪，唯一缺口在后端 Service/Controller 三层。**

---

## 二、实码问题复现（用户可见）

进入「资质与证书管理」（`/certificate/qualification`、`/certificate/subjectProfile`）等页面：

1. 前端首次**不带参**调 `GET /bidding/subject/profile/get`
2. 后端 `SubjectProfileController.get(@RequestParam("subjectType") String subjectType)` 因必填参数缺失
3. 返回「请求参数缺失:subjectType」
4. 前端虽有兼容重试兜底，但因另一处前端 bug（见 §五，`X-Silent-Error` 头未实际发出），**每次进页面会先闪一次红错**

顺带解释业务困惑：「企业资质/证书里要求重新填企业信息」——是主体档案自动带出（`fetchSubjectProfile` → 企业名/uscc）在报错时未带出，导致界面退回手填；不是数据模型重复（资质表只存 `entId` 外键，不冗余主体字段）。

---

## 三、后端落地清单（请后端同学按此修改）

### 3.1 `SubjectProfileController.java`（必需）

| 端点 | 现状 | 改为 |
|---|---|---|
| GET /get | `@RequestParam("subjectType") String`（必填） | `@RequestParam(value="subjectType", required=false) String`；语义=不传/传 BIDDER/TENDERER 均返回本租户唯一档案 |
| GET /eligibility | 同上必填 | 同上改可选；传=按该角色授权判定；不传=任一角色授权；档案完善度一律取唯一档案 |

### 3.2 `SubjectProfileServiceImpl.java`（必需）

- **读取**：`getOwnProfile` 由 `findByType(subjectType)`（按类型过滤）改为查**本租户唯一档案**（不再 `.eq(subjectType)`）；`getOwnSubjectId()` 收敛为复用唯一档案（现有"BIDDER 优先、TENDERER 回落"可简化为直接取唯一档案）。
- **保存**：`saveOwnProfile` 由 `findByType(reqVO.getSubjectType())` 改为**按租户 upsert（一租户一档）**：
  - 已有档案 → 更新；请求携带 `subjectType` 仅回写角色标记列
  - 无档案 → 新建；`subject_type` 列 NOT NULL，缺省值建议 `BIDDER`（最先建档角色默认投标人）
  - 移除保存时的 `validateType(`reqVO.getSubjectType()`)` 必填强制（为空不再拒绝；不为空仍校验合法性）
- 私有方法随之上调：`findByType` → `findOwnOne`（按租户 LIMIT 1）。

### 3.3 `SubjectEligibilityServiceImpl.java`（必需）

- `readProfile(tenantId, subjectType)`：去掉 `.eq(SubjectProfileDO::getSubjectType, subjectType)`，改查**租户唯一档案**（契约 §2.3：档案完善度读取同一份档案）。
- `findAuth(tenantId, subjectType)`：`subjectType` 为 null 时按 `BIDDER/TENDERER` 两者查，**ENABLED 优先**取一条；非 null 保持按单一角色查（授权仍按角色，`bidding_subject_auth` 不动）。

### 3.4 `SubjectProfileSaveReqVO.java`（必需）

- `subjectType`：去掉 `@NotEmpty(message="主体类型不能为空")`；`Schema.requiredMode` 改 `NOT_REQUIRED`；注释改「可选（角色标记，一租户一档下仅回写标记）」。
- 其余字段校验口径不变。

### 3.5 `SubjectProfileDO.java`（建议，纯注释）

- 类注释由「同一租户可有 BIDDER/TENDERER 两条档案」改为「一租户一档；`subject_type` 降级为角色标记列」；`subjectType` 字段注释同步。

---

## 四、验收标准（达成即算落地；对齐 09-20 契约 §四）

- [ ] `GET /profile/get` 不传参返回本租户唯一档案；传 BIDDER/TENDERER 均返回同一份；未建档返回 null（语义不变）。
- [ ] `POST /profile/save` 不带 `subjectType` 可保存；同一租户只产生一行；重复保存不报唯一键冲突（upsert 生效）。
- [ ] `GET /profile/eligibility` 不传参返回「任一角色授权 + 唯一档案完善度」；传参按该角色授权判定。
- [ ] 前端进「资质与证书管理」「主体档案」等页面不再弹「请求参数缺失:subjectType」，主体信息自动带出正常。
- [ ] 招标人公告资格门仍按 TENDERER 授权校验；投标资格（预留）仍按 BIDDER 授权校验（仅档案完善度共用一份）。
- [ ] `getOwnSubjectId()` 返回唯一档案 id（cert-form / material-check / safety-risk 取本企业主体正常非空）。

---

## 五、前端本次同步修复（已交付，不需要后端配合、不影响上述契约）

1. **`api/bidding/subject/index.ts`**：修复兼容静默头未生效 bug。
   vben `requestClient.get(url, config?)` 只接受两参；原代码把 `COMPAT_SILENT`（含 `X-Silent-Error: true`）作为**第三参**传入被忽略，导致缺参错误的静默头从没发出去、每次进页面都弹红错。已将 headers 并入第二参 config（`fetchSubjectProfile` / `fetchSubjectEligibility` 两处）。
   > 注：后端完成 §三 后，不传参即成功，前端兼容重试分支自然失效（零额外请求），无需再删。

   > **2026-09-21 补记（实测核对）**：本条此前只写在本清单里，**代码并未落地** ——
   > `git show HEAD:.../api/bidding/subject/index.ts` 仍是三参写法，用户当场复现「每次进主体档案页控制台刷两条
   > `[api 请求失败] ... code=400 请求参数缺失:subjectType`」。本次真正改掉了，并顺手加两条硬化：
   > ① `api/request.ts` 的静默标记改为**大小写都认**（axios `AxiosHeaders` 对已存在的 header 保留原始大小写，
   >    只按一个写法取有再次失效的风险）；
   > ② 新增模块级记忆 `backendRequiresSubjectType`：首次命中缺参 400 后，后续直接带默认值 TENDERER，
   >    **不再每次进页面都先发一个注定 400 的请求**（此前每页 2 个端点各浪费一次往返）。
   > 另：本次全仓扫描「两参方法被传三参」的调用点，除本文件外**没有其他**（含 `delete`），已归零。

   > **2026-09-21 收尾（后端已落地 ⇒ 兼容层整体删除）**：后端已按 §三 改造完成并实测通过
   > （提交 `93cb6b7a2`：get/eligibility 改 `required=false`、`getOwnProfile` 改查本租户唯一档案、
   > `getOwnSubjectId` 收敛为直取唯一档案、save 改按租户 upsert；另 `8166aeeb8`：资质 page/stats/apply/page 全改
   > `entId != null ? entId : getOwnSubjectId()`）。不带参加实测 `code=0`，因此前端把兼容层**整段删除**：
   > - `api/bidding/subject/index.ts`：删 `SUBJECT_TYPE_COMPAT` / `isMissingSubjectType()` / `COMPAT_SILENT_HEADERS` /
   >   `backendRequiresSubjectType`，两个读接口收敛为「有入参才带 `subjectType`」的一次请求；
   > - `views/bidding/subjectProfile/index.vue`：删掉硬编码的 `const subjectType = 'TENDERER'`（读接口本就不传、
   >   保存也不再传 —— 继续传会把"投标人/招标人共用的一份档案"错误地钉成 TENDERER 角色标记）。
   >
   > **结论：兼容重试不要再加回来** —— 后端已放宽，该分支永不命中，属于死代码；
   > 且它曾把静默头当第三参传给 `get(url, config)`（该签名只收两参）而导致「每次进页面刷两条假错误」。
   >
   > 另：资质页首屏时序（先 `await loadCurrentSubject()` 带出主体再发查询，避免空 entId 触发校验错误）
   > 已在提交 `611f5b74c` 落地，本清单不再列为待办。
2. **`views/bidding/subjectProfile/index.vue`**：法人机构类别下拉框 `<Select>` 未设宽导致塌缩只显示 A/B/C/D，已加固定宽度展示完整文案。

---

## 六、相关文件

- [后端变更请求-主体档案合并一租户一档-2026-09-20.md](./后端变更请求-主体档案合并一租户一档-2026-09-20.md)（业务/接口口径定稿）
- [2026-09-27-subject-profile-one-per-tenant.sql](../../sql/history/2026-09-27-subject-profile-one-per-tenant.sql)（唯一键迁移，已出）
- 前端：`txgy-ui-admin-vben/apps/web-antd/src/api/bidding/subject/index.ts`、`.../views/bidding/subjectProfile/index.vue`
- 后端待改：`txgy-module-bidding-server/.../controller/admin/subject/SubjectProfileController.java`、`.../service/subject/SubjectProfileServiceImpl.java`、`SubjectEligibilityServiceImpl.java`、`.../vo/SubjectProfileSaveReqVO.java`、`.../dal/dataobject/subject/SubjectProfileDO.java`
