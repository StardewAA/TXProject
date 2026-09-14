# WMS 独立开发基线

> 2026-09-12 更新：下文保留建立基线时的历史记录。WMS 已完成迁入与 ERP 镜像耦合移除，入库最小闭环已在本机前端验证。当前状态及复测步骤见 [WMS 入库前端验收记录](wms-receipt-frontend-acceptance-20260912.md)。原项目未修改，ERP 与此 master 基线无差异，未提交或推送。本机 WMS 表结构和 BPM 模型已准备，测试库存只来自审批通过的专用测试单。

## 开发边界

用户确认以远程 master 为基线，只负责 WMS 开发，不修改 ERP，保留旧 WMS 成果后继续审批流程。

- 原项目：`D:/Projects/tianxin-pipe-industry/txgycloud`，分支 `wms/erp-integration-20260908`。保留原有源码、暂存改动与未提交文件；未执行 pull、reset、clean、覆盖或推送。
- 新项目：`D:/Projects/tianxin-pipe-industry/txgycloud-wms`，为独立 clone，有独立 `.git`，不共享原项目的分支和暂存区。
- 新开发分支：`wms/approval-on-master-20260912`。
- 本次获取的远程 master 基线：`4a89d6bd104c546311d6f744150d3760dc8ead90`。后续远程提交不会自动进入本地开发分支。

本次只建立并核验基线，旧目录的 WMS 改动尚未迁入，审批功能尚未继续修改；没有改动 ERP 源码，没有提交、推送、部署或操作业务数据库。

## 已发现的迁入依赖

旧 WMS 的 `WmsInventoryServiceImpl`、`WmsWarehouseServiceImpl` 引用了本地新增 ERP 镜像 DTO 和回调，不能原样迁入后再要求修改 ERP 来配合。

远程 ERP 已提供 `ErpWmsCallbackApi.callbackExecutionResult`（执行结果回调）、库存判断回调及协作单 B 接收接口。后续应检查这些已有接口的契约并适配 WMS；ERP 镜像扩展不随 WMS 搬入。缺少的 ERP 功能列入对接说明，由 ERP 小组维护。

WMS 迁入将按后端、WMS 页面、WMS 专用 SQL 和测试逐项比较远程差异，避免整目录覆盖远程已有 WMS 更新。原目录保存全部旧成果，不通过清理原目录来实现隔离。

## 核验命令

在新项目执行 `git diff --exit-code origin/master -- txgy-module-erp txgy-ui-admin-vben/apps/web-antd/src/api/erp txgy-ui-admin-vben/apps/web-antd/src/views/erp`，退出码 0，无差异。

基线测试命令（使用已有 Maven 依赖缓存，构建输出及日志写入新项目）：

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home'
& 'D:\App\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server -am '-Dmaven.repo.local=D:\Projects\tianxin-pipe-industry\txgycloud\.maven-home\repository' '-Dtest=WmsReceiptOrderServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

日志：`.maven-home/logs/20260912-remote-master-baseline.log`。

实际结果：2026-09-12 11:11:27 +08:00，命令退出码 1，BUILD FAILURE。源码编译完成；10 项测试、0 断言失败、10 初始化错误、0 跳过，业务断言未执行。根因是远程 WMS 测试建表脚本 `src/test/resources/sql/create_tables.sql` 在 `wms_item` 表重复声明 `status`，H2 报 `Duplicate column name "status"`。本轮记录基线问题，尚未修复，不能报告测试通过。

下一小项：只修复新目录中的 WMS 测试初始化问题并重跑基线，再逐项迁入原 WMS 成果与解除本地 ERP 镜像扩展依赖。审批流程在此基础上继续完成。
