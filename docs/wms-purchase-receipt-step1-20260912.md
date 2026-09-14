# 采购入库草稿与幂等：第 1 项核验记录

本项只完成 ERP 采购入库请求创建 WMS 草稿、明细和业务幂等。没有执行真实数据库迁移、更新容器、增加实际库存或提交/推送代码。审批、质检页面、执行结果回传属于后续第 2—5 项。

## 行为

- 沿用 `WmsIntegrationApi.createPurchaseReceipt`、`WmsReceiptOrderService.createReceiptOrder` 和现有单头/明细表。
- 原单号格式 `WMS-ERP-来源单号` 保持不变，来源单号最多 56 字符，生成单号不超过现有 64 字符字段。
- 显式校验空请求、租户、ERP 来源、采购入库类型、请求号、业务链、事件版本、明细行号、来源行号、数量、物料/SKU 关系和单一原料仓。
- 当前库存和明细是 `decimal(20,2)`：数量必须为正数，最多两位小数，合计不得超过字段容量；不会静默舍入。未做数量字段扩容。
- 请求台账仍按 requestId 检查重复内容；新增来源业务台账，其内部 requestId 为 `B:` 加来源标识摘要。采购入库外部 requestId 禁止使用 `B:` 前缀。
- 来源标识为租户、来源模块、业务类型和来源单号；业务链编号参与内容比对。同一业务链可包含多张不同来源单，不能只按 businessChainNo 去重。
- 换 requestId、改变重试时间，业务内容一致时返回原草稿；重排行顺序及数量尾随零不改变业务内容。事件版本、业务链或业务内容发生变化时报来源冲突，不覆盖原单据。
- 单头保存 sourceModule、businessChainNo，原 bizOrderNo 保存来源单号；明细保存 sourceLineNo，未传时使用 lineNo。管理端保存 VO 中新增来源字段仅允许服务器赋值，响应 VO 提供这些字段。
- 两层台账、单头及明细在原有事务中一起提交或回滚。使用原台账唯一键处理同一来源并发请求；未引入第二套库存模型。
- 创建草稿不提交审批、不调用库存服务。请求受理状态 RECEIVED 不是实际入库成功。

## 本次文件改动

以下路径相对于 txgycloud；清单仅列本次编辑，不代表工作区其他已有修改由本次产生。

| 文件 | 修改内容 |
| --- | --- |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/api/integration/WmsIntegrationApiImpl.java | 请求及物料映射校验，接入来源业务幂等，传递来源字段 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/service/integration/WmsIntegrationRequestService.java | executePurchaseReceiptBusiness |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/mysql/integration/WmsIntegrationRequestMapper.java | 重复插入后使用锁定当前读；MySQL 使用共享锁，避免旧快照与不必要的锁升级 |
| txgy-module-wms/txgy-module-wms-api/src/main/java/cn/iocoder/txgy/module/wms/enums/ErrorCodeConstants.java | 无效入库请求及来源内容冲突错误 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/dataobject/order/receipt/WmsReceiptOrderDO.java | 来源模块和业务链 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/dal/dataobject/order/receipt/WmsReceiptOrderDetailDO.java | 来源行号 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/controller/admin/order/receipt/vo/order/WmsReceiptOrderSaveReqVO.java | 内部赋值来源字段 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/controller/admin/order/receipt/vo/order/WmsReceiptOrderRespVO.java | 返回来源字段 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/controller/admin/order/receipt/vo/detail/WmsReceiptOrderDetailSaveReqVO.java | 内部赋值来源行号 |
| txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms/controller/admin/order/receipt/vo/detail/WmsReceiptOrderDetailRespVO.java | 返回来源行号 |
| txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/api/integration/WmsIntegrationApiImplTest.java | 保留原用例，补全物料及仓库模拟数据、业务台账委派 |
| txgy-module-wms/txgy-module-wms-server/src/test/java/cn/iocoder/txgy/module/wms/api/integration/WmsPurchaseReceiptDraftTest.java | 新增真实数据库测试 |
| txgy-module-wms/txgy-module-wms-server/src/test/resources/sql/create_tables.sql | H2 来源字段 |
| deploy/mysql/repair/2026-09-12-wms-receipt-source.sql | 可重复执行的 MySQL 增量字段脚本，尚未应用 |

## 实际验证

- 初次基线日志：`.maven-home/logs/20260912-receipt-step1-baseline.log`。4 项接口测试通过，16 项数据库测试因 Nacos 尝试写入无权限的 `C:\logs\nacos` 启动失败；不是业务断言失败。
- 调整仅测试进程的 user.home 后，红灯日志：`.maven-home/logs/20260912-receipt-step1-red.log`。23 项中，新增测试 3 项失败、1 项错误，复现非法数量、重复行号、物料/SKU 不匹配和换请求号不能返回原草稿；原入库服务 12 项和台账 4 项通过。
- 核验日志：`.maven-home/logs/20260912-receipt-step1-verify.log`。33 项全部通过：接口 4、采购草稿数据库 13、请求台账 4、入库服务 12。数据库测试显式启用与生产相同的租户拦截器，覆盖来源落库、租户隔离、并发幂等、失败回滚和不调用审批/库存。
- 代码复核后最终回归：`.maven-home/logs/20260912-receipt-step1-final-shared-lock.log`。2026-09-12 10:30:54 完成，33 项通过、0 失败、0 错误、0 跳过，Maven 退出码 0。
- H2 测试通过不等同于已部署 MySQL 和真实服务联调通过；MySQL 下的并发锁行为仍需部署阶段复核。复核时已调整重复插入后的读取：MySQL 使用共享锁当前读，H2 使用其支持的 FOR UPDATE。锁定读语义参考 [MySQL 官方文档](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。

实际核验命令（PowerShell；目录明确，参数整体引用）：

```powershell
Set-Location 'D:\Projects\tianxin-pipe-industry\txgycloud'
$savedJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud/.maven-home').Trim()
    & 'D:\App\JetBrains\IntelliJ IDEA 2025.2.1\plugins\maven\lib\maven3\bin\mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server -am '-Dmaven.repo.local=D:\Projects\tianxin-pipe-industry\txgycloud\.maven-home\repository' '-Dtest=WmsIntegrationApiImplTest,WmsPurchaseReceiptDraftTest,WmsReceiptOrderServiceImplTest,WmsIntegrationRequestServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
} finally {
    $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
}
```

## 部署后核验步骤（本轮尚未执行）

1. 先确认入库审批字段脚本已应用，再应用本次来源字段脚本；不要删除或清空请求台账，来源业务幂等依赖持久化台账。
2. 更新 WMS 后，以当前租户已有原料仓、物料和 SKU 发送采购入库请求，需求数量 10；核对返回 WMS 单号。
3. WMS 入库列表查询该单，确认只有一张草稿和一条数量 10 的明细，库存及流水数量均未变化；新增来源字段可先通过详情接口核对，页面展示在第 2 项补齐。
4. 重复原请求，再换 requestId 和重试时间提交：均返回原单号。改变数量或业务链：明确报冲突。
5. 用非法数量、错误物料/SKU、重复行号、混仓请求验证：拒绝请求，不留下单头、明细或受理台账。

已经存在但缺少来源业务台账的旧草稿不会被自动认领或覆盖；如命中相同单号，将保留原有单号重复错误供人工核对。请求台账记录受理时的结果，不代表当前业务状态；手工删除/取消草稿后的重放与来源单修改规则，将在第 2—5 项和最终执行查询一起收口，本项不自动重建已删除草稿。后续采购草稿修改、质检、审批和最终执行查询另按计划验收。
