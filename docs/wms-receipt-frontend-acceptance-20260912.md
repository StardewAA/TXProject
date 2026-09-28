# WMS 入库前端验收记录（2026-09-12）

## 本次交付与边界

已在本机浏览器操作完成：模拟 ERP 申请 → WMS 草稿 → 本单验收 → BPM 人工审批 → 按合格数量入库 → 查询执行结果和库存。

工作目录为 `D:/Projects/tianxin-pipe-industry/txgycloud-wms`，分支 `wms/approval-on-master-20260912`，基于远程 master `4a89d6bd104c546311d6f744150d3760dc8ead90`。原项目 `txgycloud` 保留；本目录 ERP 后端和 ERP 前端与基线无差异。未执行提交、推送或历史库存迁移。WMS 之外仅包含入库 BPM 监听及启动所需的通知 Feign 注册修复。

模拟 ERP 是开发验证入口，调用真实 WMS 建单服务、真实 BPM、真实本机 WMS 库存事务；记录 `simulation=true`，不向 ERP 回调。它不代表 ERP 小组的申请入口已经联调，也不代表 MES 已完成。

## 直接查看验收结果

- 入库管理：http://localhost:5173/#/wms/receipt
- 已通过流程：http://localhost:5173/#/bpm/process-instance/detail?id=1e10ff29-ae6b-11f1-8fc5-ba4223ad075e
- 租户：金岩矿物棉实业有限公司（2005）；本次操作及审批用户：宋凯（80168）。
- 测试单号：`WMS-ERP-SIM-ACCEPT-0912-01`，入库单 ID `940019`。
- 测试物料：`WMS联调测试原料0912`，编码 `WMS-TEST-RAW-0912`，SKU `20028`，免检；没有修改原有物料类型。
- 仓库：金岩原材料仓（106）。

| 验收项 | 实际结果 |
| --- | --- |
| 申请 / 实收 / 合格 / 不合格 | 10 / 10 / 8 / 2 kg |
| 提交审批前库存 | 无该 SKU 库存记录，即 0 |
| 审批中库存 | 仍为 0，执行状态待执行 |
| 人工审批通过 | 2026-09-12 13:33:37，BPM 显示审批通过 |
| WMS 执行结果 | SUCCESS；completedQuantity=8；单据已完成 |
| 库存 | 8 kg，库存记录 ID 940123 |
| 入库流水 | 1 条，ID 980078，变化 0 → 8 |
| 同一审批事件再次回调 | 返回 code=0/data=true，库存仍为 8，流水仍为 1 条 |
| ERP 通知状态 | SIMULATED，不发送 ERP 通知 |

## 你如何重新操作一遍

1. 打开入库管理，点击「模拟 ERP 申请」。填写新的来源单号，例如 `ACCEPT-0912-02`；选择金岩原材料仓。
2. 点击「选择商品规格」，按商品编号 `WMS-TEST-RAW-0912` 搜索，勾选测试物料，申请数量填 10，点击「生成 WMS 草稿」。
3. 修改新草稿，填实收 10、合格 8、不合格 2。此专用物料免检；需质检物料必须有本单质检图片。点击「提交 BPM 审批」并确认。
4. 点击新单号打开详情，点击「查看 BPM 审批」。当前模型指定宋凯审批，点击「通过」，填写测试审批意见并提交。
5. 确认页面显示「审批已通过 / 执行已入库」以及「已入库合格数量 8」。点击「查询收货仓库当前库存」核对累计库存：如果在现有测试基础上再完成一张同样的单，会从 8 增加到 16。

重复使用相同来源单号会命中幂等结果，不应生成第二张单；需要新的测试单时必须换来源单号。不要用已有生产物料或旧单据做这次练习。

## 实际验证与日志

WMS 相关 93 项、BPM 监听 4 项，共 97 项测试在组合 package 中通过；0 失败、0 错误、0 跳过。日志 `.maven-home/logs/20260912-wms-bpm-package.log`。此前已有失败测试保留，包括清空验收数量回归测试；没有删测试规避失败。

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home'
& 'D:/App/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin/mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server,txgy-module-bpm/txgy-module-bpm-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=WmsIntegrationApiImplTest,WmsPurchaseReceiptDraftTest,WmsReceiptOrderServiceImplTest,WmsReceiptOrderDetailServiceImplTest,WmsReceiptExecutionServiceTest,WmsReceiptNotificationSchedulerTest,WmsReceiptSimulationServiceTest,WmsIntegrationRequestServiceTest,WmsInventoryServiceImplTest,WmsWarehouseServiceImplTest,WmsReceiptOrderStatusListenerTest' '-Dsurefire.failIfNoSpecifiedTests=false' package
```

本次部署发现 BPM 的 NotifyMessageSendApi 未注册，补充 Feign 注册后实际重跑以下命令，4 项通过、BUILD SUCCESS，并确认容器启动成功：

```powershell
& 'D:/App/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin/mvn.cmd' -o -pl txgy-module-bpm/txgy-module-bpm-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=WmsReceiptOrderStatusListenerTest' '-Dsurefire.failIfNoSpecifiedTests=false' package
```

日志 `.maven-home/logs/20260912-bpm-startup-fix.log`。前端生产构建成功，见 `txgy-ui-admin-vben/wms-build.log`；全项目 typecheck 存在 140 项既有诊断，不能称全项目类型检查通过。收货相关文件未出现类型诊断。

浏览器验收后的单据、执行结果、库存及流水证据在 `.maven-home/logs/20260912-frontend-closure.json`；审批前证据在 `20260912-inventory-before.json`。重复回调初次手写请求漏了 Spring 事件的 `source` 字段，返回 500；补全为真实事件格式后返回成功，库存及流水断言通过。这是测试报文错误，不能将初次请求描述成通过。

`git diff --check` 退出码 0；ERP 范围 `git diff --exit-code origin/master -- txgy-module-erp txgy-ui-admin-vben/apps/web-antd/src/api/erp txgy-ui-admin-vben/apps/web-antd/src/views/erp` 无差异。

## 本机运行与尚未覆盖事项

当前使用 `wms-acceptance-wms`、`wms-acceptance-bpm`、`wms-acceptance-frontend`。ERP 容器持续运行；旧 WMS、BPM、前端容器停止但保留。配置仅在内存中传递，未保存明文环境配置文件。

已给本机 WMS 表补齐入库验收字段及 master 已有的物料元数据字段；商品列表实际报错后补齐分类 `storage_description`。SQL 位于 `deploy/mysql/repair`。已发布 `wms_receipt_approval` 模型。验收 DDL 有一次性脚本，不能无检查重复执行整个 SQL 集合。

恢复原服务可运行 `script/wms/Stop-LocalAcceptance.ps1`；此操作不回滚数据库新增字段、不删除测试记录。启动脚本用于首次切换，已存在同名测试容器时不能再次直接运行它。

仍需由后续对接处理：

- 真实 ERP 调用与执行回调网络联调：本轮不修改 ERP，也未将模拟返回当作 ERP 成功。
- 共享系统短信日志的 `template_params` 长度不足，BPM 分配待办的短信日志写入报错。流程待办、人工审批与 WMS 入库均实际成功；本轮未修改 system 表或源码。此前进度中笼统称为站内信错误，以这里的实际日志根因为准。
- 需质检图片上传、拒绝重提及全不合格场景有后端校验/测试覆盖，但本轮浏览器实测使用免检物料，未宣称上述场景都已做前端验收。
- BPM 和 WMS 为两个服务，跨服务没有分布式原子提交，依赖幂等及重试；后续可增强对账与运维补偿。
- 原有历史单据没有审批执行历史，新页面可能显示旧单「已完成」但审批字段仍为默认值，本轮没有迁移或重写旧库存。

本地依赖缓存与测试证据通过本 clone 的 `.git/info/exclude` 排除，避免误加入未来源码提交；原项目及共享 `.gitignore` 未变。
