# WMS 入库第 2 项：来源明细快照

## 本次交付范围

采购入库集成创建草稿时，在现有 `wms_receipt_order_detail` 增加可空的 `source_snapshot_json`，保存收到的完整来源明细。保留创建时的名称、规格、单位、质检要求、批次及附件，不重新读取主数据来替换来源申报。

沿用 ERP-WMS v0.2 请求 DTO，ERP 不需要为本次新增字段修改请求。`qualityRequiredSnapshot` 原样保留：true、false、未传入分别保留其含义，未传入不擅自解释为免检。

查询接口新增结构化 `sourceSnapshot`：

- `GET /admin-api/wms/receipt-order/get?id=...` 的 `details[].sourceSnapshot`
- `GET /admin-api/wms/receipt-order-detail/list-by-order-id?orderId=...` 的 `[].sourceSnapshot`

内部 `sourceSnapshotJson` 不输出给页面，管理后台保存请求不能写入该字段，常规 MyBatis 更新不能覆盖已保存的快照。现有 `itemName` / `unit` 等主数据展示字段仍保持原语义，后续页面须使用 `sourceSnapshot` 展示来源申报。

历史单据没有快照时返回 null 或省略该属性，不用当前主数据补造历史记录。快照中的数量、质检标记和附件都是来源申报，不代表 WMS 已验收、已质检或已入库。本次不改变审批、库存执行和页面按钮。

## 文件与修改位置

- `WmsIntegrationApiImpl.java`：创建采购入库明细时序列化来源 DTO。
- `WmsReceiptOrderDetailDO.java`：新增仅插入的快照字段。
- `WmsReceiptOrderDetailSaveReqVO.java`：新增服务端填写字段。
- `WmsReceiptOrderDetailRespVO.java`：隐藏内部存储值，返回结构化来源快照；现有两个 Controller 的 BeanUtils 映射复用该字段。
- `src/test/resources/sql/create_tables.sql`：H2 测试表增加可空快照字段。
- `WmsPurchaseReceiptDraftTest.java`：新增两项测试，覆盖快照、主数据修改、业务重试、显式免检及两个查询入口。
- `deploy/mysql/repair/2026-09-12-wms-receipt-source-snapshot.sql`：可重复执行的新增字段脚本。

## 验证命令

在项目根目录 PowerShell 执行：

```powershell
$env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud/.maven-home').Trim()
& 'D:\App\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server -am '-Dmaven.repo.local=D:\Projects\tianxin-pipe-industry\txgycloud\.maven-home\repository' '-Dtest=WmsIntegrationApiImplTest,WmsPurchaseReceiptDraftTest,WmsReceiptOrderServiceImplTest,WmsIntegrationRequestServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

先运行新增测试的实际结果：15 项，2 失败、0 错误、0 跳过，两项新增测试均因查询响应缺少来源快照失败。日志 `.maven-home/logs/20260912-receipt-step2-snapshot-red.log`。

修改后上述 4 类测试共 35 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS，进程退出码 0，完成时间 2026-09-12 11:00:29 +08:00。日志 `.maven-home/logs/20260912-receipt-step2-snapshot-green.log`。本次涉及的已跟踪代码及 H2 SQL 的 `git diff --check` 通过（仅有 Git 换行格式提示）。只读代码复核未发现本小项缺陷。本轮未修改 ERP 代码，未提交或推送。

## 部署与人工核验边界

本次尚未应用 MySQL SQL、重建容器或浏览器联调。部署前需按顺序应用既有入库审批脚本、来源追踪脚本及本次快照脚本。仅针对 WMS 表，不修改 ERP 数据库，不迁移历史库存。

部署后可用模拟 ERP 请求创建一张 WMS 草稿，带上批次、质检要求和附件，读取上述两个接口确认 sourceSnapshot 与来源明细一致；修改物料主数据后再查，快照应不变；更换 requestId 重试原来源单，应返回原 WMS 单号且不新增明细。单元测试使用 H2、真实 WMS 持久化链路和 Controller 方法；BPM、库存及外部依赖为 mock，不能据此宣称真实 ERP-WMS 审批库存闭环完成。
