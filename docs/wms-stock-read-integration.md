# ERP 产品库存读取 WMS

本次沿用 `web-antd` 的 ERP 产品库存、仓库信息页面及 ERP 查询、导出权限。页面经 ERP 后端调用 WMS 内部只读接口，直接查询当前租户的 WMS 仓库及 SKU 库存，无需 ERP 物料、仓库、库存镜像表先存在对应记录。

## 接口

| 消费入口 | WMS 内部接口 | 权限 |
| --- | --- | --- |
| `GET /admin-api/erp/stock/wms/page` | `GET /rpc-api/wms/stock-read/page` | `erp:stock:query` |
| `GET /admin-api/erp/stock/wms/warehouses` | `GET /rpc-api/wms/stock-read/warehouses` | `erp:stock:query` |
| `GET /admin-api/erp/stock/wms/export-excel` | 按页调用库存查询接口 | `erp:stock:export` |
| `GET /admin-api/erp/warehouse/wms/page` | `GET /rpc-api/wms/stock-read/warehouses` | `erp:warehouse:query` |
| `GET /admin-api/erp/warehouse/wms/export-excel` | `GET /rpc-api/wms/stock-read/warehouses` | `erp:warehouse:export` |

分页参数：`pageNo`（从 1 开始）、`pageSize`（1–200）。可选筛选：`materialCode`、`skuCode`、`warehouseCode` 为精确编码，`materialName` 为名称包含匹配。仓库选择器直接使用 WMS 编码，不能传 ERP 仓库 ID。

库存返回 `id`（WMS 库存记录 ID）、`materialCode`、`materialName`、`skuCode`、`skuName`、`unitName`、`warehouseCode`、`warehouseName`、`quantity`。`quantity` 为实际库存；不同 SKU 分行展示，不换算或合并不同规格，不将其宣称为可用库存。

租户取自请求上下文并由现有 Feign 拦截器传递。WMS 每个关联表均约束租户及未删除状态；没有租户上下文时拒绝查询。RPC 沿用现有内部服务访问约定，不应另行暴露到公网。WMS 返回失败时向调用端报告错误，不退回 ERP 历史镜像。

导出保持筛选条件，每次读取 200 条。导出是多次实时查询，库存同时变动时不保证全表同一时点快照。

## 本轮边界

- ERP 产品库存、仓库信息列表和导出只读取 WMS。仓库信息页不再提供操作旧 ERP 仓库表的创建、修改、删除或默认仓库开关；仓库资料由 WMS 维护。ERP 订单表单的旧仓库选择接口暂未改造，不能将 WMS 编码冒充 ERP 仓库 ID。
- WMS 仓库编辑新增类型选择，允许原材料仓和成品仓；旧调用未传类型时，创建保持原有默认值，修改保留已有类型。此变更不写库存数量，不预留库存，不触发出入库审批。
- ERP 订单表单原有 `/erp/stock/get-count` 等旧接口尚未改造；此轮完成不代表所有 ERP 库存入口都已接入 WMS。
- ERP 审批、协作、询价功能对应的数据库与菜单迁移不在本轮执行。
- 入出库及其他库存变更仍需由后续 WMS 审批工作完成，本轮只读查询不改变这些流程。
- `2026-09-09-erp-inventory-mirror-schema.sql` 是本次方向调整前已执行的 ERP 基础字段修复，不是只读接口的部署前置条件。没有执行批量主数据导入或 ERP 库存镜像刷新；后续仓库类型修正沿用既有仓库回调，产生对应的 ERP 仓库镜像，见下文。

## 验证

`WmsStockQueryServiceTest` 覆盖实时库存、SKU 分离、分页、仓库/规格过滤、删除记录与租户隔离，以及缺少租户时拒绝查询。

`ErpWmsStockControllerTest` 覆盖直接返回 WMS 数据及 WMS 失败传播。

后端五项测试与 ERP/WMS 打包通过；`web-antd` 生产构建通过。前端全量类型检查仍存在历史错误，不能视为全量通过。

本机验收应对照同租户 WMS 库存记录检查 ERP 产品库存页，并测试筛选及导出。查询期间 ERP 镜像表是否为空不影响结果。

同一 SKU 存在于不同仓库时保留分仓库存行。例如岩棉毡在两个仓库的 300 卷与 200 卷，以及包装膜的 40 卷与 5 卷，不属于同仓库同 SKU 重复记录；是否需要实际移库应另外核对业务，不能靠删行或合并展示修改真实库存。

### 2026-09-10 本机部署核验

- WMS、ERP、前端已使用本轮编译产物更新，三个容器均 healthy。旧镜像保留为各自的 `before-stock-read-20260910` 标签。
- 从 ERP 容器访问 WMS 内部接口，租户 2005 返回 8 条库存，与数据库逐条一致；仓库列表返回 2 个仓库。ERP 本地库存表仍为 0 条。
- 成品仓岩棉板筛选总数为 2，分页到第二条返回 80mm 规格 60 包；50mm SKU 精确筛选返回 1 条、39 包。
- 缺少租户的 WMS 请求被拒绝（当前返回业务码 500）；经前端入口未登录访问 ERP 新接口返回业务码 401。
- 租户 1 查询返回 31 条，未包含租户 2005 的 8 个库存记录 ID。
- 使用已登录的金岩企业账号，ERP 产品库存页面显示全部 8 条库存；仓库下拉框展示两个 WMS 仓库；物料编码 `JY-FG-BOARD` 与成品仓组合筛选显示 39 包、60 包两条规格。ERP 后端访问日志确认分页与仓库请求正常完成，实际跨服务读取已验证。
- 初次点击导出时，ERP 请求正常完成，但未捕获内置浏览器下载事件，因而当时未将文件落地记为通过。后续验收实际找到了下载文件并读取内容，详见下文。

### 2026-09-10 仓库信息补齐验收

- 按用户授权，通过 ERP 正常删除接口逻辑删除租户 2005 的测试仓库“北京原料仓”（ID 6）。删除前检查 11 处 ERP 仓库引用列，活动记录引用数均为 0；删除后 `deleted=1`。
- 修复 WMS 创建仓库强制写入 `RAW_MATERIAL`、修改时忽略请求类型的问题，新增类型表单和列表字段。通过 WMS 页面将“金岩成品仓”（ID 107、编码 `W64672393`）改为 `FINISHED_GOODS`，版本从 1 增至 2；“金岩原材料仓”保持 `RAW_MATERIAL`。
- 本次编辑触发既有仓库回调，ERP 出现编码 `W64672393` 的仓库镜像（ID 7）。ERP 仓库信息页直接读取 WMS，因此也能展示尚无 ERP 镜像的原材料仓；只读查询不依赖该镜像。
- ERP 仓库信息页面实际显示两个 WMS 仓库及正确类型；按“成品仓”筛选只返回金岩成品仓。已将页面恢复为全部仓库。
- 8 条实际库存余额逐条复查未变。两类仓库分类修正不代表已经完成库存移库或业务存放关系调整。
- 仓库修复相关后端测试共 13 项通过，ERP/WMS 打包及前端生产构建通过；WMS、ERP、前端容器均 healthy。前一版镜像保留为 `before-warehouse-fix-20260910`，没有提交或推送代码。

### 2026-09-10 导出验收

- 实际通过 ERP 产品库存页面，以物料 `JY-FG-BOARD`、仓库 `W64672393` 筛选后点击导出，文件落到 `C:\Users\XWB\Downloads\产品库存.xls`。读取工作簿确认只有两个规格、数量分别为 39 包与 60 包，物料和仓库编码与页面一致。
- 发现内容实际为 XLSX，但原文件名使用 `.xls`。本轮库存、仓库导出前后端文件名统一为 `.xlsx`；Excel 工具按 `.xlsx` 后缀设置对应 MIME 类型，原 `.xls` 调用的 MIME 行为不变。
- 新增导出测试，实际生成并重新读取工作簿，核验 201 条数据的两页读取、筛选条件保留、最后一条记录和数量；WMS 异常时不生成工作簿。加入文件后缀和 MIME 断言后先复现失败，修正后相关 6 项测试通过。
- 格式修正已完成 ERP 打包、前端生产构建，并更新本机 ERP 和前端容器；旧镜像保留为 `before-stock-export-20260910`。未重启 WMS 或修改库存。
- 更新后浏览器刷新曾被自动审批服务因额度耗尽拒绝，故当时保留下载验收未完成状态。额度恢复后已继续完成以下实际验收。
- 19:57 实际从 ERP 页面按物料 `JY-FG-BOARD`、仓库 `W64672393` 导出，下载文件为 `C:\Users\XWB\Downloads\产品库存.xlsx`（3987 字节）。以 XLSX 读取器重新打开，确认一个“库存”工作表、8 列表头和2条数据，SKU分别为50mm/80mm规格，数量39/60，物料和仓库编码与页面一致；文件扩展名与内容格式一致。未使用原生 Excel 界面验证显示效果。
- 续验收时发现独立运行问题：19:53 首次页面查询出现 `feign.codec.DecodeException: 'messageConverters' must not be empty`，位于 SpringDecoder 响应解码；WMS 内部接口仍正常返回8条库存，随后页面重试恢复，筛选和导出成功。当时未将重试成功视为解决，随后完成下述修复。

### 2026-09-10 首次并发请求修复

- 检查实际依赖 OpenFeign 5.0.2 的字节码，确认 `FeignHttpMessageConverters` 首次初始化先向字段赋空列表，再填充默认转换器，且读写没有同步；第二个并发请求可能提前取得空列表。
- ERP 的 Feign 子上下文通过 `ErpFeignClientConfiguration` 对 `getConverters()` 加同步保护，保留原有默认转换器、自定义器和 SpringDecoder；网络请求与解码过程不在锁内。配置通过 ERP 的 `EnableFeignClients.defaultConfiguration` 应用。
- 新增可控并发回归测试：暂停第一次初始化，以真实 SpringDecoder 发起第二次解码。原实现稳定复现空转换器异常；修复后第二次等待初始化，两个响应均正确解码。连同库存分页、仓库、导出测试共8项通过，ERP 打包成功。
- 20:17 仅更新 ERP 容器，旧镜像保留为 `deploy-erp:before-feign-fix-20260910`。20:18:46 冷启动后首次刷新页面，仓库列表和库存分页分别于 .141 和 .162 开始，于 .340 和 .355 完成；无需重试即显示8条库存和两个仓库。新容器日志未出现 `messageConverters`、`DecodeException` 或 ERROR。
- ERP、WMS、前端均 healthy。本次没有修改库存数据、没有提交或推送；ERP 迁移和审批协作开发仍在本轮范围之外。
