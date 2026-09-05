# notice 前端开发指南

> 面向前端（txgy-ui-admin-vben / apps/web-antd），只做公告/公示（notice）域。
> 原则：**不改后端**。契约以 `NoticeController` + `api/vo` 为准，联调以 `/v3/api-docs`（knife4j）为事实源。后端缺口/疑点集中列在 §5，交后端处理，前端不自行改后端代码或字段。
> 后端未落地：主体/项目登记/投标/开标/评标/中标合同/归档/监管/信用等——本指南不覆盖。

## 1. 接口清单（后端已提供，前端照此对接）

| # | 动作 | 方法+路径 | 权限 | 关键入参 | 关键出参 |
|---|---|---|---|---|---|
| A1 | 管理端分页 | GET `/bidding/notice/page` | `bidding:notice:query` | tenderProjectCode/bidSectionCodes/noticeType/status/publicLevel/keyword + 分页 | `PageResult<NoticeRespVO>` |
| A2 | 管理端详情 | GET `/bidding/notice/get?id=` | `bidding:notice:query` | id | `NoticeRespVO`（含 currentBody、pdfFileId） |
| A3 | 新建草稿 | POST `/bidding/notice/create` | `bidding:notice:create` | `NoticeSaveReqVO`（id 空） | Long（公告ID） |
| A4 | 修改 | PUT `/bidding/notice/update` | `bidding:notice:create` | `NoticeSaveReqVO`（id 必填） | true |
| A5 | 删除 | DELETE `/bidding/notice/delete?id=` | `bidding:notice:create` | id | true |
| A6 | 发布 | POST `/bidding/notice/publish?id=` | `bidding:notice:publish` | id | true |
| A7 | 撤下 | POST `/bidding/notice/unpublish?id=` | `bidding:notice:publish` | id | true |
| A8 | 大厅公开分页 | GET `/bidding/notice/public/list` | 无（@PermitAll） | tenderProjectCode/bidSectionCode/noticeType/keyword + 分页 | `PageResult<NoticeRespVO>`（仅已发布+社会公开） |
| A9 | 大厅公开详情 | GET `/bidding/notice/public/detail?id=` | 无（@PermitAll） | id | `NoticeRespVO` |
| A10 | 截止时间查询 | GET `/bidding/notice/exchange/deadline?bidSectionCode=` | 内部只读 | bidSectionCode | `{tenderProjectCode,bidSectionCode,noticeCode,deadline,payload}` |
| A11 | 公开同步 | POST `/bidding/notice/exchange/gather-sync` | 内部 | `NoticePublicSyncReqVO` | `NoticePublicSyncRespVO` |

> A10/A11 是系统间/归集接口，一般不做管理页；如需在运维页手动触发，先与后端确认口径。**A6/A7 与 create/update 均幂等（@Idempotent），前端防连点由按钮 loading 承担，不要重复触发。**

## 2. 前端要用的枚举与字段

- 状态 `status`：`DRAFT` 草稿 / `PUBLISHED` 已发布 / `UNPUBLISHED` 已撤下 / `ARCHIVED` 已归档。流转（后端 `canTransit` 强制，非法返回 `1_060_101_002`）：
  DRAFT→PUBLISHED/ARCHIVED；PUBLISHED→UNPUBLISHED/ARCHIVED；UNPUBLISHED→PUBLISHED/ARCHIVED；ARCHIVED=终态。
- 类型 `noticeType`：取 `NoticeTypeEnum` 18 值（ZDGG/GCSGG/ZSJC 等，含中文 label）。**下拉 value 用英文码、展示用中文**；字典 key 若已配 `bidding_` 前缀走字典，否则前端本地枚举兜底（见 §5）。
- 公开级别 `publicLevel`：`SOCIAL` 社会公开 / `GOVERNMENT` 政务公开；决定大厅是否可见（GOVERNMENT 不出现在 A8/A9）。
- 检索开关：大厅关键词检索依赖 `bidding.es.enabled`；关闭时后端抛 `1_060_101_005`（"关键词检索不可用"）。前端需捕获并给提示、不静默失败。
- `NoticeRespVO` 字段：id, noticeCode, tenderProjectCode, bidSectionCodes[], noticeName, noticeType, noticeTypeName, publicLevel, status, url, publishTime, sendTime, currentBody, pdfFileId, updateTime。
- 时间：入参 `publishTimeStart/End` 格式 `yyyy-MM-dd HH:mm:ss`；出参 `publishTime/sendTime/updateTime` 的序列化格式（ISO 串 or 毫秒值）联调时以 knife4j 实际为准，前端统一按后端返回处理，不自作约定（列入 §5）。

## 3. 前端任务（可勾选，按此顺序落）

### 3.1 API 层（`src/api/bidding/notice/index.ts`）
- [ ] `namespace BiddingNoticeApi`：定义 `NoticeVO`、`NoticeSaveReqVO`、`NoticePageReqVO`、`NoticeDeadlineVO` 类型，字段对齐 §1/§2。
- [ ] `getNoticePage / getNotice / createNotice / updateNotice / deleteNotice / publishNotice / unpublishNotice / getPublicNoticePage / getPublicNoticeDetail / getDeadline`，均走 `requestClient`，不写裸 axios。

### 3.2 管理端列表页（`src/views/bidding/notice/index.vue`）
- [ ] 筛选：tenderProjectCode、bidSectionCode、noticeType(下拉)、status(下拉)、publicLevel(下拉)、keyword、发布时间区间。
- [ ] 列：noticeCode、noticeName、noticeTypeName、status(标签色：草稿灰/已发布绿/已撤下橙/已归档蓝)、publicLevel、publishTime、updateTime。
- [ ] 行操作按钮按状态显隐：编辑/删除（草稿或撤下可编辑，已发布禁编辑→走撤下）；发布（DRAFT/UNPUBLISHED 显示）；撤下（PUBLISHED 显示）；查看。
- [ ] 权限指令 `v-hasPermi` 绑 `bidding:notice:query|create|publish`；分页对接 A1。

### 3.3 新建/编辑表单（弹窗或独立页）
- [ ] 字段：noticeName*、tenderProjectCode*、bidSectionCodes*(多选)、noticeType*(下拉)、publicLevel*(单选, 默认 SOCIAL)、sendTime(日期时间)、url、正文。
- [ ] 正文：优先"上传公告 PDF"→ `pdfFileId`（走 FileApi，拿文件 id，不把二进制塞 body）；在线正文用 `content`。两者满足"发布前须有正文/PDF"，否则触发 `1_060_101_004`/`1_060_101_006` 提示。
- [ ] 表单校验对齐后端 9 项字段规则（见后端 NoticeSaveReqVO 文档），必填缺失前端先拦。
- [ ] 保存=A3/A4（幂等）；提交后按钮 loading，禁双击。

### 3.4 发布 / 撤下 / 删除
- [ ] 均二次确认框；成功后刷新列表 + 详情。
- [ ] 失败按错误码给文案：状态流转非法 `..._002`、字段不合权威数据集 `..._003{字段}`、发布前无正文 `..._004`、缺 PDF `..._006`、删除非草稿/撤下 `..._002`。

### 3.5 详情页（管理端 A2）
- [ ] 展示 §2 的 NoticeRespVO 全字段；currentBody 渲染；pdfFileId→文件预览/下载入口。

### 3.6 大厅公开（只读，`/public/list`、`/public/detail`）
- [ ] 无登录即可访问；仅传 tenderProjectCode/bidSectionCode/noticeType/keyword（**不传 status/publicLevel**，后端已固定"已发布+社会公开"）。
- [ ] 详情正文、附件、源URL、发布时间展示；隐藏任何内部字段。

### 3.7 全文检索
- [ ] 列表 keyword 输入框 + 命中高亮（若后端未回高亮片段，先普通匹配、高亮列入 §5 待后端）。
- [ ] 捕获 `bidding.es.enabled` 关闭态的 `..._005` 错误，展示"检索暂不可用"。

## 4. 公共约定
- 路由/菜单：菜单由 `sql/2026-09-03-notice-menu.sql` 下发（权限码 `bidding:notice:query/create/publish`，父目录"公告公示"）；前端不硬编码菜单。
- 分页统一 `PageParam`；返回 `CommonResult`（code/msg/data），前端仅处理 code≠0 的错误。
- 金额域无；时间统一后端返回口径（§5 待确认格式后固化一次）。

## 5. 报后端处理的问题（前端不改后端，先按现状适配）
1. **缺"归档"接口**：状态机有 `ARCHIVED`（终态，`canTransit` 允许 →ARCHIVED），但 `NoticeController` 无 `/archive` 端点。前端无法触发归档。→ 请后端补 `POST /archive`（权限 `bidding:notice:publish`）。
2. **正文版本历史无接口**：后端正文多版本（NoticeContent），但 A2/A9 只回 `currentBody`，无版本列表/按版本查看接口。若详情页要展示历史版本，需后端补只读版本接口。
3. **noticeType 字典未确认**：18 值是否已配 `bidding_` 字典、还是仅后端枚举？前端需稳定 value 列表，避免硬编码漂移。→ 后端给字典 key 或枚举接口。
4. **时间序列化格式未定**：出参 LocalDateTime 是 ISO 串还是 epoch 毫秒？→ 后端确认一种，前端全局统一。
5. **检索高亮未实现**：控制器注释标"高亮片段待检索组件增强"。前端先不做高亮，等后端补 `highlightFragments`。
6. **错误码段位表不一致（既有审核项）**：规范 §4.7 与代码/测试（100~105/199）冲突。前端只需错误码→文案映射稳定；请后端/文档统一，勿改码。
7. **A10/A11 是否要前端入口**：deadline/gather-sync 属系统间接口，确认无管理页需求后前端不实现。

## 6. 里程碑建议
API 层(3.1) → 管理端 CRUD+发布流(3.2–3.5) → 大厅只读(3.6) → 检索(3.7) → 全量过一遍 §5 与后端对齐。每步以 `/v3/api-docs` 校验字段，接口对不上以文档+后端为准，缺口进 §5。
