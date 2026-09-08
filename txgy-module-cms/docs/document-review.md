# CMS 文档审查

当前实现与 `cms-policy-search` 原有检索代码并存。审查使用现有业务MySQL和平台登录/租户权限，本身不依赖Docker内的ES、向量库或大模型；现有检索开关保持独立。

## 实际流程

1. 提交UTF-8 TXT、DOCX、PDF或粘贴完整正文。原始文件及报告保存在 `cms_document_review`。
2. 乱码、规范性与质量检查自动执行。来源链接仅做HTTPS政府域名初筛，不自动下载用户URL，不将域名视作已核验。
3. 没有可信依据时返回 `FAIL / SOURCE_UNVERIFIED`，作为数据库中的待核验/退回记录，知识库不接收。
4. 有 `cms:review:verify` 权限的人员独立对照官方完整原文、标题、文号、机关、日期、效力及图片表格，登记官方正文、版本沿革链接和意见。核验人及日期由服务端记录。系统严格比对正文SHA-256（仅统一换行和首尾空白）以及元数据，重新执行全部检查。
5. `PASS` 或只有非阻断问题的 `WARNING` 自动写入 `cms_knowledge_document` 及 `cms_knowledge_chunk`。报告、文档和切片在同一事务提交。任何写入失败全部回滚。`FAIL` 仅更新数据库退回记录。
6. 修改重提产生新审查ID，保留原始失败版本和核验历史。重复确认已经通过的审查不会重复写入。

这是一套“自动质量检查＋授权人工官方依据核验”的实现，不宣称自动完成网上法律沿革调查。核验日期超过30天、正文不一致、元数据冲突、未知效力、乱码、扫描页或缺条都会阻断。已废止或失效版本仅能明确选择历史归档；尚未生效版本保留警告和未来日期。

## 知识库与数据库

知识库是数据库中仅保存通过审查内容的独立逻辑区，失败资料仍留在审查表。当前CMS尚无完整业务资料表，新增这三张表用于闭环，未直接写入AI模块的 `ai_knowledge_*` 表，也没有生成向量或开放检索。后续通过API对接AI/RAG模块，不跨模块直接改表。

切片为最多1200个UTF-16字符，优先在换行处分割，不丢字符、不拆开代理对；保存原文起止偏移。此阶段不是语义切片，表格关系仍需对照原文。

## 部署

1. 在已有业务库执行 `sql/cms_document_review.sql`，只有建表语句，不重置原有数据。
2. 执行 `sql/cms_document_review_menu.sql`，为角色分配 `cms:review:query`、`cms:review:submit`、`cms:review:verify`。不要运行旧菜单脚本中的DELETE来替代本迁移。
3. 使用原有CMS数据库配置启动CMS Server（48100），并保持System服务、认证、租户校验和网关可用。审查流程无需ES；如只运行审查，可设置 `TXGY_SEARCH_ES_ENABLED=false`。
4. 前端位于 `txgy-ui-admin-vben/apps/web-antd/src/views/cms/review/index.vue`，菜单组件 `cms/review/index`，路径 `/cms/review`。前端权限模式可使用附带路由；后端权限模式使用新增菜单。

本次不自动运行生产迁移，也不假设用户丢失的Docker数据已经恢复。MySQL迁移与服务启动后才可在线使用。无需改动或恢复ES索引。

## 接口

所有路径均以 `/admin-api/cms/review` 为前缀；租户来自认证后的平台上下文，不接受请求体租户ID。

| 方法 | 路径 | 权限 | 功能 |
| --- | --- | --- | --- |
| POST | /text | submit | JSON `{metadata,text}`，保存审查报告 |
| POST | /upload | submit | multipart `file` 与JSON `metadata` |
| GET | /list | query | 最近1至100条，默认50 |
| GET | /get?id=UUID | query | 正文、报告、核验历史、已入库切片 |
| GET | /original?id=UUID | query | 原始文件下载，附件方式返回 |
| PUT | /verify?id=UUID | verify | 登记核验依据；通过即自动入知识库 |
| POST | /resubmit?id=UUID | submit | 保留旧版并新建审查 |

Metadata：`title, documentNo, issuer, sourceUrl, kind(LAW/STANDARD/POLICY), validity(EFFECTIVE/NOT_YET_EFFECTIVE/REPEALED/EXPIRED/UNKNOWN), effectiveDate, expiryDate, historical`。日期为YYYY-MM-DD；`expiryDate`表示最后有效日期。

Verify：`officialMetadata, officialText, validitySourceUrl, note`。需以官方材料为准，不能为使文档通过而复制未核实内容。当前官方链接支持HTTPS `.gov.cn`，其他授权渠道需后续明确扩展。

评分：四项各100分，每个错误扣50分、警告扣10分，最低0分，总分为四项平均向下取整。任一错误直接FAIL，与总分无关；无错误但有警告为WARNING。

## 限制与测试

- 文件10MB、正文50万字符、PDF最多300页；超限请求可能由上传层直接拒绝，不会保存超大文件。
- 扫描PDF须外部OCR与人工校对；本版不自动运行OCR、不解析DOCX内嵌附件、不执行文档指令。修订痕迹会阻断。
- 源文件解析失败会保存失败报告及大小允许的原文件，格式不合法的JSON元数据返回参数错误。
- 报告列出问题维度、严重程度、位置、修改意见，可从页面下载JSON。
- 审查是时间点结论，入库后法规变更需另行重新审查；尚未实现自动效力监控。

```powershell
mvn -o -pl txgy-module-cms/txgy-module-cms-server -am "-Dtest=ReviewEngineTest,ReviewParserTest,ReviewWorkflowTest,CmsLawSearchServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

事务测试使用隔离H2数据库和真实迁移DDL，覆盖失败不入库、成功入库、重复确认、跨租户拒绝、切片写失败整笔回滚、修改保留旧版。它不代表生产MySQL迁移已经执行。
