# WMS 四类来源业务执行记录

## 约束与实施顺序

沿用用户已确认范围，仅在 txgycloud-wms、分支 wms/approval-on-master-20260912 开发。
HEAD 为 4a89d6bd104c546311d6f744150d3760dc8ead90，无 CodeGraph 索引。
保留 ERP 采购入库及未提交成果；不修改移库、ERP/MES 源码、原项目，不提交或推送。

1. 核对既有记录、容器及基线测试。
2. 修复出库 BPM 注入与库存/流水/执行状态原子性；保留失败审批结果回归测试。
3. 复用集成台账和现有单据，增加 ERP 销售出库、MES 完工入库、MES 领料出库。
4. 补齐只读查询及模拟入口，按业务逐项测试、构建、使用专用数据做本机人工 BPM 验收。
5. 汇总实际实现、测试证据与待 ERP/MES 联调的契约。

## 已执行核对

- 验收 WMS、BPM、前端运行；旧 WMS/BPM/前端停止保留；ERP 运行。没有执行切换脚本。
- 已读取入库验收与 master 基线文档；本轮尚未执行 SQL。
- 用户指定外部 Maven 缓存离线缺少 Netty、Spring Boot、Spring Cloud、Alibaba Cloud 四个 BOM；测试未运行。日志 `.maven-home/logs/20260912-four-flows-baseline.log`。
- 使用上一轮验收记录中的项目内 `.maven-home/repository` 重跑：116 项，0 断言失败、4 错误。出库 BPM 未注入 1 项；流水查询占位实现 3 项。BPM 模块因 WMS 测试失败未执行。日志 `20260912-four-flows-baseline-local-cache.log`。
- 流水测试原先临时构造生产代码/SQL 中不存在的 `wms_stock_application`。保留三个测试及历史保留、过滤、分页、租户隔离、只读断言，将该来源夹具改为现有 `wms_receipt_order`，不创建重复单据模型。
- `20260912-shipment-atomic-red.log`：12 项出库既有测试通过（修复 BPM 注入后）。首次插入新增测试因换行匹配未成功，不能将该日志当作原子性红灯证据。
- `20260912-shipment-atomic-red-2.log`：流水 3 项通过，出库 13 项中新增原子性断言失败，期望执行状态 1、实际 0。

库存当前仅有 `quantity` 余额，无已实现的预留字段；不能将其无条件宣称为可用库存。
本记录为开发过程证据，不代表四类业务或真实三系统联调已经完成。

## 后续执行记录（2026-09-12）

- 用户已明确授权读取本机 MySQL 凭据和宋凯现有登录令牌，仅内存用于 WMS/BPM 验收；不打印或落盘。
- 先查询 information_schema 确认出库来源字段缺失，再执行 `deploy/mysql/repair/2026-09-12-wms-shipment-source.sql`。执行后核对：单头新增 source_module、business_chain_no、simulation、notification_status、notification_error、notification_attempts、next_notification_time；明细新增 source_line_no、source_snapshot_json。共 9 字段，无库存数据变更。不需要重复执行。
- `20260912-four-flows-package-3.log`：WMS 190 项、BPM 4 项全部通过，打包成功。此后另有代码修复，最终结果以新回归记录为准。
- `20260912-shipment-real-transaction.log`：2 项真实数据库事务测试通过；出库两行库存与流水共同提交，第二行库存不足时全部回滚并保留失败状态，重复回调不重复扣减。
- `20260912-review-regressions-red.log`：新增普通建单来源边界 2 项失败，SKU 在审批期间停用的失败记录 1 项失败。真实事务测试显式确认出库及库存服务均为 Spring AOP 代理。现已修复，正在运行完整回归 `20260912-four-flows-package-4.log`，此处尚不声称通过。
- 只读核对现有出库 BPM：模型 `ebf8bdf4-a777-11f1-bc76-f219f2516d17`，原流程为仓库主管 60096/60084、上级决策 60084 两级人工审批。尚未改变此模型，临时本机审批人方案待用户选择。
- 已补充统一接口交接文档 `docs/wms-erp-mes-interface-handoff-20260912.md`。MES 回调标为建议契约，当前使用结果查询；真实 ERP/MES 联调未完成。
- 最终当前代码回归 `20260912-four-flows-package-4.log`：WMS 195 项、BPM 4 项，共 199 项全部通过，Maven BUILD SUCCESS。前端 `pnpm.cmd run build:antd` 成功（11 tasks）。
- 两个验收镜像构建成功并替换本机 WMS/前端；旧验收容器分别保留为 `wms-acceptance-wms-four-flows-backup`、`wms-acceptance-frontend-four-flows-backup`。BPM 与 ERP 未停止。新前端 healthy，仓库只读 API 实际返回 tenant 2005 的两座仓库。
- 通过 WMS 正常建物料 API 创建新专用物料：WMS4-RAW-0912（item 10022 / SKU 20029）、WMS4-FG-0912（item 10023 / SKU 20030）。未写入库存。
- 前端模拟 ERP 采购入库创建 `WMS-ERP-SIM-FOUR-ERP-R-0912`（receipt 940021），数量 10；数据库核对草稿、approval -1、execution 0、simulation true；两套新 SKU 均无库存记录。后续验收仍在进行。
- 两笔新入库单均通过前端验收和人工 BPM 审批：ERP receipt 940021（流程 c856c5b5-ae81-11f1-8fc5-ba4223ad075e）、MES receipt 940022（流程 e7bc136b-ae82-11f1-8fc5-ba4223ad075e）；各实收 10、合格 8、不合格 2。数据库核对：各自新 SKU 库存 0→8，各流水 1 条，approval 2、execution 1、status 4。模拟通知不发送。
- MES 页面验证发现沿用入库详情的“ERP 通知 / ERP 原申请快照”固定文案，已改为来源系统中性文案，待重新构建部署。
- 两笔出库通过前端创建：ERP `WMS-ERP-SIM-FOUR-ERP-S-0912`（shipment 950026、成品 SKU 20030、数量 3），MES `WMS-MES-SIM-FOUR-MES-S-0912`（shipment 950027、原料 SKU 20029、数量 3）。当前均 approval -1 / execution 0，等待人工确认审批，不应报告已出库。
- 四类模拟申请逐笔接口重放均返回原单 ID，数据库余额与流水不变。已批准的两笔入库 RPC 回调重放均返回 code 0 / data true，各 SKU 余额仍 8、流水仍 1。首次尝试使用源码默认 48092 端口连接失败，未执行回调；查询容器 SERVER_PORT=48080 后重试成功。
- 只读实际验证：仓库两条；专用成品 SKU、库存、流水按 materialCode 筛选各一条且 pageSize=1 生效；未携带令牌 code 401；宋凯令牌配 tenant-id=1 返回 code 403，无跨租户数据。
- 出库确认页来源字段已加只读锁定；账面库存列名替换原“可用库存”，未新增预留功能。前端重构建验证中。
- 最终前端再次构建成功，镜像 `txgy-wms-acceptance-frontend:four-flows-ui2-20260912` 已运行且 healthy；上一版前端保留为 `wms-acceptance-frontend-four-flows-ui1`。浏览器验证外部出库来源、类型、仓库、数量和增删明细均禁用，提交审批按钮可用，列名显示“账面库存”。未提交任何出库审批，未改变原两级出库审批配置。
- 最后核对：移库路径 git diff 无输出；WMS/BPM 运行，前端 healthy，deploy-erp-1 healthy。没有提交或推送。当前等待用户就原出库两级审批的本机验收方式答复后继续两类实际出库、库存不足和回调重复执行验收。

## 入口、显示与角色审批修正（用户后续确认）

- 用户明确：ERP 采购/销售审批由 ERP 完成，WMS 只做仓库负责人审批；审批必须通过角色成员解析，不固定用户 ID。用户先指定当前宋凯 80168，随后要求按角色赋予用户。
- 原入库使用固定用户策略 30/80168；原出库是 SIMPLE 模型，候选策略 10 中 60096/60084 实为角色编号（并非用户编号）。早先文档把这两个数字当审批账号的表述不准确，以本节为准。
- 角色 60096 为仓库管理员。启用成员原为 80193、80209、80932，停用 80192。仓库 principal 未设置。宋凯 80168 原有企业超级管理员角色 60084；权限差集核对待加仓库角色新增菜单授权为 0，现有数据范围全部，仓库角色范围更窄。
- 自动审批曾拒绝尚未核对权限的角色分配操作，未执行。完成上述只读核对后通过正常权限 API 保留 60084 并增加 60096，实际确认成功。
- 新 `Configure-WarehouseRoleApproval.ps1` 和 `warehouse-role-approval.bpmn` 将两类 WMS 模型发布为角色 60096 的仓库负责人单节点人工审批、多成员或签。BPM 自动审批关闭；任一有效角色成员通过后节点结束。新任务创建时解析当前角色成员，已生成任务不自动随角色变化转移。
- 出库模型第一次仍为 SIMPLE，BPM 按旧简易模型生成 XML；改为 BPMN 后又遇名称空格不一致的发布校验，均已定位并修复，最终两条角色模型已发布。历史流程实例和库存不改写。
- 当前代码基线回归 `20260912-entry-approval-baseline.log`：WMS 195 + BPM 4 全部通过。模拟入口测试新建后先因待实现模块缺失失败，实现后 `wms-simulation-entry-green.log` 4 项通过。前端 `wms-entry-layout-build.log` 构建成功。
- 新前端 `entry-role-20260912` 已部署，旧前端保留为 `wms-acceptance-frontend-before-entry-role`。出库页已显示独立模拟入口，只有 ERP 销售/MES 领料两项；入库共享组件按 receipt 方向限制两类入库。主列表解除全局单行截断，列宽调整，详情单号可完整换行与复制。
- ERP 销售出库 950026 已通过页面提交、仓库角色人工审批。流程 db6d9737-ae8d-11f1-8fc5-ba4223ad075e，真实分配给四名启用角色成员（含新加入 80168），停用成员未分配。审批前库存 11（使用当时实际余额），审批后 8；本单流水 1 条，status 4 / approval 2 / execution 1，其他角色待办数量归零。模拟通知未发送。

## 最终验收与交付（同日，18:15 后汇总）

- MES 领料出库 950027，WMS-MES-SIM-FOUR-MES-S-0912，流程 5c900f36-ae8e-11f1-8fc5-ba4223ad075e：页面确认提交并由宋凯角色待办人工通过，原料 8→5，出库 3，流水 1，approval 2 / execution 1。
- 新角色入库回归：940024（WMS-ERP-SIM-ROLE-ERP-R-0912，流程 f4f948fa-ae8f-11f1-8fc5-ba4223ad075e）、940025（WMS-MES-SIM-ROLE-MES-R-0912，流程 fbedfb8d-ae8f-11f1-8fc5-ba4223ad075e）。两单通过实际模拟 API 建草稿、入库 update API 录入实收 2/合格 1/不合格 1、提交审批 API 发起，随后在浏览器人工审批；原料 5→6、成品 8→9，各流水 1。这里不把 API 录入说成前端录入；此前两类原入库已通过前端验收。
- 专用库存不足出库 950028，WMS-ERP-SIM-ROLE-SHORT-0912，流程 84165b7d-ae8f-11f1-8fc5-ba4223ad075e：API 建草稿和提交，浏览器人工批准；当时成品库存 8、申请 100，执行失败并显示明确库存不足，approval 2 / execution 2，库存仍 8，流水 0。
- 专用驳回单 950029，WMS-MES-SIM-ROLE-REJECT-0912，流程 85fca7e3-ae8f-11f1-8fc5-ba4223ad075e：第一次浏览器拒绝被共享短信异常回滚，SQL 确认仍 approval 1、四个待办保留，未冒充拒绝成功。根因是 BpmMessageServiceImpl 的结果短信 checkError 在拒绝事务中抛出，阻断后续状态事件。
- TDD 修复仅针对 `wms_receipt_approval`、`wms_shipment_approval` 的结果短信：保留错误日志，隔离短信异常；其他流程短信与 WMS 库存/状态回调异常仍照原逻辑传播。没有修改共享短信表或 ERP/MES 流程。测试首次因 WebProperties 嵌套类型名写错编译失败（不是业务红灯）；修正夹具后 `wms-role-sms-red-2.log` 6 项中 4 项断言失败，复现短信阻断；实现后最终回归通过。
- 最终命令（均从项目根运行；前端命令从 txgy-ui-admin-vben 运行）：

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home'
& 'D:/App/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin/mvn.cmd' -o -pl txgy-module-wms/txgy-module-wms-server,txgy-module-bpm/txgy-module-bpm-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=Wms*Test' '-Dsurefire.failIfNoSpecifiedTests=false' package
pnpm.cmd exec vitest run apps/web-antd/src/views/wms/order/shared/simulation.test.ts --dom
pnpm.cmd run build:antd
```

- `wms-role-final-package.log`：WMS 195 + BPM 10 = **205 项通过**，BUILD SUCCESS；BPM 10 中新增短信故障隔离 6 项。`wms-simulation-entry-final.log`：前端 4 项通过；`wms-entry-role-final-build.log`：最终前端构建退出码 0。日志位于项目 `.maven-home/logs`；早期前端 red/green 日志位于 txgy-ui-admin-vben 根目录。全项目类型检查既有问题未处理。
- 本机最终前端镜像 `txgy-wms-acceptance-frontend:entry-role-final-20260912`，旧版保留 `wms-acceptance-frontend-entry-role-v1`；BPM 镜像 `txgy-wms-acceptance-bpm:role-sms-20260912`，旧验收版保留 `wms-acceptance-bpm-before-role-sms`。配置仅内存继承，不保存凭据；WMS 沿用已验证镜像，ERP 持续 healthy。
- 新 BPM 就绪后，用原 950029 再次在浏览器人工拒绝成功：页面“审批不通过 / 审批拒绝 / 已出库 0”，数据库 approval 3 / execution 0，本单流水 0，流程待办 0；原料余额 6、成品 9 保持不变。
- 实际内部 RPC 重放 950026、950027、950028 的相同流程审批回调，均 code 0 / data true；成功单仍各流水 1、变更 -3，失败单仍流水 0。角色回归两笔入库各流水 1、变更 +1。首次 SQL 查询误用 order_no 列报 Unknown column，未执行后续提交；改为已存在字段后核对，不做表结构变更。
- 浏览器最终前端验证：出库独立模拟弹窗仅销售/领料，入库仅采购/完工；列表单号/业务单号/审批执行状态完整显示且行高不截断，详情单号可换行复制。只刷新自用验收标签，没有刷新用户填写中的表单。
- 复核发现配置脚本只看草稿会掩盖部署失败，已增加实际发布定义核验；再次运行两条均输出 Already deployed，验证现有入库版本 3 / 出库版本 10，无重复发布。角色为空配置拒绝，关闭自动审批；角色成员按节点创建时解析。
- 最后分支与 HEAD 与要求一致，移库、ERP、MES 路径 diff 为空。没有重复执行 SQL，没有提交或推送。
- 集中交付：[接口文档](wms-erp-mes-interface-handoff-20260912.md)、[页面自测步骤](wms-four-flows-self-test-20260912.md)。四类业务已完成本机模拟来源、真实 WMS/BPM 验收；真实 ERP/MES 联调、MES 回调约定、共享短信送达问题仍待对应小组处理。

- 最终空白检查：直接禁用 autocrlf 的检查将现有 CRLF 当作尾空白；按 Windows 行尾设置 cr-at-eol 后 diff --check 退出码 0，未为此批量修改文件行尾。

## 出库按业务仓库选料（用户新增要求，2026-09-12 19:00 后）

- 用户确认入库选择目标仓库的行为保留，新增要求出库销售从成品仓取货，并按仓库筛选选料。
- 只读确认仓库106类型 RAW_MATERIAL、仓库107类型 FINISHED_GOODS。后端原有来源申请校验已限制两类出库对应仓库/物料类型，本轮不改后端。
- 出库模拟复用 WmsInventorySelect，查询已选仓库正账面库存，替换原先全租户 SKU 主数据选择；销售仅列成品仓、领料仅列原料仓。实际库存行 ID 与 SKU ID 不同，转换时使用 skuId，保留仓库关联用于跨仓校验；切换上下文有已选行时确认清空，取消保留。
- 通用库存选择组件仅增加可选 quantityTitle 文案参数，本模拟入口传“账面库存”，其他调用默认行为保持。入库仍使用原商品 SKU 选择。未改移库路径、库存数据或审批模型。
- 先运行原模拟4项测试通过（wms-shipment-warehouse-baseline.log），新增仓库筛选、未知类型、库存到SKU映射、跨仓与无效库存保护后10项中5失败5通过（wms-shipment-warehouse-red.log）；实现后10项全部通过（wms-shipment-warehouse-green.log）。命令为 `pnpm.cmd exec vitest run apps/web-antd/src/views/wms/order/shared/simulation.test.ts --dom`。
- `pnpm.cmd run build:antd` 成功，11 tasks（wms-shipment-warehouse-build.log）；镜像构建退出码0（wms-shipment-warehouse-image.log），部署 `txgy-wms-acceptance-frontend:shipment-warehouse-20260912`；旧容器保留 `wms-acceptance-frontend-before-shipment-warehouse`。本轮后端未改、未重新运行205项后端测试。
- 浏览器独立验收页验证销售仓库下拉只有成品仓、库存列表5行均成品仓；MES下拉只有原料仓、库存列表5行均原料仓；均显示账面库存。选择专用成品后尝试改为MES，提示确认，取消后ERP类型/成品仓/已选商品仍保留。
- 页面实际建专用草稿950031：WMS-ERP-SIM-WH-ERP-S-0912-1910，warehouse107/sku20030/quantity1；950032：WMS-MES-SIM-WH-MES-S-0912-1913，warehouse106/sku20028/quantity1。两笔SQL/API核对approval -1、execution0、各流水0。建单前后对应专用库存分别18、16未改变；用户同期其他测试改变了此前余额，以本次实际读取为准。
- 没有提交上述草稿审批，也未发送真实ERP/MES通知；只刷新自用验收标签，保留用户未保存的表单。相关自测及交接文档已更新。

## 角色菜单决定入出库审批人（2026-09-12 19:30—20:10）

- 用户要求角色菜单可以配置入库/出库审批权限，给用户赋该角色即可参与对应审批。新增WMS专用BPM候选解析器及只读Mapper，复用共享system表显式租户关联，无新增表或重复单据模型。读取当前有效角色明确授权和租户套餐，不将超级管理员通配权限当候选资格。
- 权限为入库提交6740、入库仓库审批6741（新增）、出库提交6737、出库仓库审批6738（复用旧approve-supervisor键并统一标签）。旧上层决策6739保留授权与菜单，新单层模型不使用。仓库角色60096增量获得6741，保留原授权；租户2005独占WMS套餐120增量补齐四项菜单包含关系，SQL已执行不需重跑。
- 自动审批审查首次拒绝配置中的隐藏旧项，未执行；脚本收窄为纯追加后成功。测试建单命令又因共享simulate-source入口位于receipt路径被拒绝，未执行；读代码证明按SALE_SHIPMENT分流createSaleShipment后获准执行，出库核对和提交均使用shipment路径。
- 基线205项通过（wms-permission-baseline.log）；新增类未实现先编译失败（red），空实现后8项中6断言失败（red-2）。完整实现后WMS195+BPM18=213项，失败0、错误0、跳过0，Maven package成功（wms-permission-package.log）。8项新增测试使用真实H2/MyBatis查询覆盖角色授予/撤销、租户与套餐隔离、禁用/删除过滤、入出库权限分离、去重、未知流程拒绝。
- 命令从项目根目录运行：设置 `JAVA_TOOL_OPTIONS=-Duser.home=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home`，指定用户提供的mvn.cmd，参数 `-o -pl txgy-module-wms/txgy-module-wms-server,txgy-module-bpm/txgy-module-bpm-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=Wms*Test' '-Dsurefire.failIfNoSpecifiedTests=false' package`。未删旧测试；现有移库测试随WMS套件运行，但移库源码未改。
- BPM镜像构建退出0（wms-permission-bpm-image.log），运行 `txgy-wms-acceptance-bpm:permission-candidates-20260912`，旧容器保留 `wms-acceptance-bpm-before-permission-candidates`。环境变量只在内存传递，没有停止ERP。
- `Configure-WarehouseRoleApproval.ps1 -UsePermissionCandidates` 发布入库版本4、出库版本11；脚本校验实际定义：表达式策略60、单个仓库人工或签节点、自动审批关闭、无候选拒绝。
- API创建并验收专用入库940028 `WMS-ERP-SIM-PERM-ERP-R-0912-1952`，API创建出库950033 `WMS-ERP-SIM-PERM-ERP-S-0912-1952`。新入库真实待办4人80168/80193/80209/80932；出库5人多80169，分别与当前角色明确菜单授权一致，非固定角色分配。审批前专用原料0、成品18未变。
- 独立浏览器以宋凯人工通过两单。入库原料仓106/SKU20029：0→1、流水980090；出库成品仓107/SKU20030：18→17、流水980091。SQL确认两单approval2/execution1，流程剩余待办0。重复同单同流程审批回调返回成功，库存仍1/17、各流水1条，没有重复执行。没有真实ERP/MES通知；MES本次未重新建权限专项单，复用相同方向规则且保留此前四类验收证据。
- 只读代码复核未发现阻塞项。此次未改前端源码、未重新构建前端；前轮10项前端测试和构建记录保留。自测与交接文档已改为任意角色勾选对应权限，不再要求固定60096。候选是新任务生成时快照，授权变更不自动撤销历史待办。
- 详细交付：[审批权限说明](wms-approval-permissions-20260912.md)、[页面自测](wms-four-flows-self-test-20260912.md)、[接口交接](wms-erp-mes-interface-handoff-20260912.md)。保留ERP采购成果，不改ERP/MES、移库或原目录，不提交、不推送。
- 浏览器角色管理中实际展开WMS权限树，确认入库“提交审批/仓库审批”、出库“提交审批/仓库审批”四项均显示，旧上层决策项仍在。检查后点击取消，没有在浏览器保存角色变更，也未操作用户原有表单。

## 入库选料显示并筛选物料类型（2026-09-12 20:15 后）

- 用户反馈采购选择了成品而报RAW_MATERIAL错误，但页面无法查看商品类型。根因是SKU分页VO及Controller未返回物料主数据materialType，选择组件也无类型显示和业务过滤。
- 沿用实际wms_item.material_type，SKU分页请求增加可选materialType，复用原JOIN在数据库分页前过滤，响应追加实际类型；未传参数保持原范围。没有修改物料分类值、库存、表结构或执行任何增量SQL。
- 入库模拟通过公共SKU选择器可选参数筛选采购原材料/完工成品；选择器及已选明细显示中文类型，未知/未设置不猜测。选入及提交前检查类型，切换入库业务有选料时确认清空，取消保留。公共选择器默认不启用类型显示/过滤，未改移库调用或出库选库存流程。
- 先运行WmsItemServiceImplTest，5项通过（wms-material-type-baseline.log）；现有前端10项通过（baseline-ui）。前端首次受本机命令启动限制未运行，获准重试后真实通过；没有把首次启动失败当测试结果。
- 新增后端3项测试全部断言失败（red.log），含真实H2/MPJ过滤分页及删除状态测试、Controller响应映射测试；前端首次因新模块缺失未运行，空实现后15项中4失败11通过（red-2-ui）。实现后WMS全套198项通过、无失败/错误/跳过且package成功（wms-material-type-package.log）；前端15项通过（green-ui），build:antd成功、11 tasks（build.log）。BPM本次未修改、未重跑其18项。
- 后端命令：项目根目录，设置JAVA_TOOL_OPTIONS指向本clone的.maven-home，指定用户提供mvn.cmd，`-o -pl txgy-module-wms/txgy-module-wms-server -am '-Dmaven.repo.local=D:/Projects/tianxin-pipe-industry/txgycloud-wms/.maven-home/repository' '-Dtest=Wms*Test' '-Dsurefire.failIfNoSpecifiedTests=false' package`。前端从txgy-ui-admin-vben运行 `pnpm.cmd exec vitest run apps/web-antd/src/views/wms/order/shared/simulation.test.ts --dom` 及 `pnpm.cmd run build:antd`。上述本轮日志统一前缀wms-material-type，位于.maven-home/logs。
- 已构建部署本机 `txgy-wms-acceptance:material-type-20260912` 与 `txgy-wms-acceptance-frontend:material-type-20260912`，两个镜像构建退出0。旧容器保留 `wms-acceptance-wms-before-material-type`、`wms-acceptance-frontend-before-material-type`；前端127.0.0.1:5173/5174保持，环境仅在内存传递，ERP/BPM未重启。
- 实际SKU接口：RAW_MATERIAL总数2、FINISHED_GOODS总数9，pageSize1均只返回1行并带正确类型；不筛选总数11。独立浏览器验证采购列表2个原料SKU、每行“物料类型：原材料”，无成品测试SKU；选入WMS4-RAW-0912后明细同样显示类型。切换MES出现确认，取消后原类型/商品仍在；确认才清空。MES完工选择成品仓，列表9行均显示成品，包含WMS4-FG-0912，不包含原料测试SKU。
- 页面验证仅查询和临时选择，没有生成单据、改变库存或发送通知；关闭自用验收页，没有刷新用户的未保存表单。类型、审批和库存规则原有边界保持，未提交/推送，ERP/MES及移库路径diff为空。
