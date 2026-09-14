# 缺口通知定向测试记录（2026-09-14）

## 范围与数据

只运行相关定向测试。Java 测试使用独立 H2 内存数据库；HTTP 发送测试使用127.0.0.1随机端口的测试接收服务器，不调用运行中的ERP/MES。MySQL验证使用无网络临时容器 wms-shortage-rr-test-20260914（mysql:8.4），与业务数据库无连接；验证后容器及测试卷已清理，可由脚本重新生成测试证据。

本机原有 wms-acceptance-wms、wms-acceptance-bpm、wms-acceptance-frontend、deploy-erp-1、deploy-mes-1 运行中；新代码没有部署至这些容器，也没有修改其业务库、权限或账号。前端新功能需要部署新构建后才能在运行页面出现。

## WMS 定向后端

工作目录 D:/Projects/tianxin-pipe-industry/txgycloud-wms。最终命令：

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home'
& 'D:/App/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin/mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=WmsShortageSenderTest,WmsShipmentInventoryTransactionTest,WmsShipmentOrderServiceImplTest,WmsPurchaseReceiptDraftTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

日志 .tmp/shortage-review-tests.log，BUILD SUCCESS：47项，0失败、0错误、0跳过。

| 测试类 | 数量 | 关键覆盖 |
| --- | --- | --- |
| WmsPurchaseReceiptDraftTest | 17 | 既有来源建单幂等、来源字段及新增缺口钩子兼容 |
| WmsShipmentOrderServiceImplTest | 14 | 既有审批、修改限制和库存事务 |
| WmsShipmentInventoryTransactionTest | 8 | 缺口阻止审批但持久化、两方向、同SKU分配、补货消除、租户隔离、执行失败不可绕过重审、失败单取消 |
| WmsShortageSenderTest | 8 | 未配置、成功回执、5次自动上限、4xx/业务拒绝、模拟/跨租户禁发、版本替代、并发单次确认、取消消息、调度公平性 |

隔离测试专用来源标识包括 SHORTAGE-TEST-001、GAP-001、SALE-GAP-001、FAILED-GAP-001、CANCEL-GAP-001、SEND-001，WMS单据包括 TXN-SHIP、WMS-MES-SEND-001；它们不是可在运行系统查询的业务单号。

库存/流水断言：库存不足时原SKU库存保持10和1，出库流水0；同SKU两行需求3+4、现存5时缺口2，补足7后RESOLVED且尚未扣库存；批准成功的原有测试库存10/10变7/6、流水2条，重复回调不再改变库存。所有数据随隔离测试清理。

首次红灯测试确实复现库存不足仍提交BPM。接入阶段出现精简测试环境缺少JdbcTemplate，以及新增测试泛型断言编译错误；均已修正，最终以上述完整定向命令结果为准。没有删除失败场景规避测试。

## WMS 前端

在 txgy-ui-admin-vben/apps/web-antd 执行：

```powershell
& 'D:/Projects/tianxin-pipe-industry/txgycloud-wms/txgy-ui-admin-vben/node_modules/.bin/vite.CMD' build --mode production
```

日志 .tmp/shortage-frontend-build.log；补充已完成/取消/审批中禁用复查按钮后，最终构建日志 .tmp/shortage-frontend-final-build.log，退出码0，生成dist及dist.zip，有既有大包提示。两处修改组件的SFC编译也通过。首次日志路径写错导致命令未启动，改为绝对路径后运行成功。本项不是浏览器验收或全项目类型检查。

## MySQL 隔离验证

使用已有 mysql:8.4 镜像：

```powershell
docker run --name wms-shortage-rr-test-20260914 --network none -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -d mysql:8.4
& 'D:/Projects/tianxin-pipe-industry/txgycloud-wms/script/wms/Verify-ShortageMysqlIsolation.ps1'
```

脚本校验容器名称及无网络模式，仅在专用 shortage_test 数据库运行。日志 .tmp/shortage-mysql-isolation.log：

- PASS：事务已建立旧快照后，另一事务确认发送，锁定读取取得 latest=1:SUCCEEDED。
- PASS：另一事务增加版本，锁定读取取得 latest=2:PENDING。
- PASS：库存锁定读取分别取得新值7.00及10.00，不使用旧快照。
- PASS：WMS实际新增表迁移在MySQL8.4执行成功。

ERP/MES脚本 sql/migrations/wms-shortage-inbox-20260914.sql 也在同一专用库执行成功，确认新增6张消息表。随后校验容器完整ID，执行 docker rm -f -v wms-shortage-rr-test-20260914 清理测试容器与测试卷。

这验证数据库语法和本次关键读取方式，不宣称已经完成运行中的三系统并发联调。

## ERP/MES 证据与真实联调待办

接收端定向验证及审查修复结果记录在 [ERP/MES接收端报告](../../txgycloud/docs/wms-shortage-receiver-report-20260914.md)。接收端前端SFC编译和菜单权限检查与WMS整体构建是不同验证，不相互替代。

接收端最终ERP9项、MES9项通过（0失败/错误/跳过），联合reactor退出码0。包含精确HTTP小数、实际全局异常处理链、局部4xx/5xx响应、独立事务死锁重试和失败完整回滚。总计65项定向测试。接收端全量前端类型检查因默认4GB堆内存耗尽未完成，不能宣称全项目类型检查通过；六个消息箱Vue组件编译及两套菜单权限行为检查通过。

## 主要修改文件

WMS后端均位于 txgy-module-wms/txgy-module-wms-server/src/main/java/cn/iocoder/txgy/module/wms：

- service/integration/WmsShortageService.java：缺口检查、版本记录、复查与取消。
- service/integration/WmsShortageSender.java、WmsShortageScheduler.java：有界投递、确认和重试。
- controller/admin/order/shipment/WmsShortageController.java：受权限保护的查询、复查和重试。
- service/order/shipment/WmsShipmentOrderServiceImpl.java、controller/admin/order/shipment/WmsShipmentOrderController.java：接入原建单/审批/执行/取消流程及业务错误。

WMS前端位于 txgy-ui-admin-vben/apps/web-antd/src：

- api/wms/order/shipment/shortage.ts。
- views/wms/order/shipment/modules/shortage.vue。
- views/wms/order/shipment/bpm-detail.vue。

附加新增表迁移、隔离MySQL验证脚本，以及对应发送/库存测试；ERP/MES具体文件由接收端报告列出。两个分支既有修改均保留，没有修改移库代码，没有提交或推送。

仍待部署方配置：目标网关地址、服务账号令牌、租户、专用接收身份绑定和消息查看权限；应用新表和新构建；实际发起MES领料/ERP销售来源单，核验接收箱、未读提醒及补货后的仓库审批。没有使用真实访问令牌的三系统联调证据。
