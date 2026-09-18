# WMS 只读与 ERP 库存履约适配（2026-09-12）

## 业务边界

- 商城维护展示、价格、店铺、运费、订单、付款和售后。
- WMS 给商城的接口只查询商品规格、元计价售价和现存数量。现存数量不等于可售数量，未设售价的 null 不作为免费。
- 商城不再直接预占/增减 WMS 表；实际预占、释放、出库、退货入库统一由 ERP 协调。
- ERP 真实接口尚未完成：当前仅有商城端请求契约、持久化记录、结果回传入口和独立模拟账本。没有真实 ERP 网络请求发送器，PENDING 不代表 ERP 已收到。
- 不改 WMS/ERP 模块，不改变商品图片、价格、店铺、分类等经营资料；不自动转换或覆盖库存。

## 已实现的状态流程

| 操作 | 商城行为 | ERP 未确认 | 确认后 |
|---|---|---|---|
| 新建订单 | 与订单同事务保存 RESERVE 请求 | 不创建支付单，前端显示待 ERP 确认库存 | 补建支付单 |
| 取消/未发货退款 | 保存 RELEASE 请求 | 保留待确认记录，不伪造释放成功 | 记录释放结果，不复活订单 |
| 快递发货 | 保存 OUTBOUND 请求与物流参数 | 订单保持待发货 | 复用商城正常发货逻辑、日志和通知 |
| 自提核销 | 保存 OUTBOUND/PICKUP 请求 | 不完成核销 | 仍由有权限的门店人员完成提货核销，ERP 出库不等于买家收货 |
| 退货验收 | 保存 RETURN 请求，对应售后编号 | 售后保持待收退货 | 推进到等待平台退款 |
| 已发货仅退款 | 不自动加回物理库存 | 不产生虚假入库 | 真实退货另按验收流程处理 |

旧订单不会补造模拟预占。没有 ERP 记录的历史订单保留既有支付入口；涉及出入库的旧订单进入 pending，需 ERP 核实，不能直接用模拟结果认定历史事实。正式模式下应在对接前清理未完成的旧支付与库存占用；商城缓存不是新的库存权威。

## 模式和安全开关

trade-server 默认：

```yaml
txgy:
  trade:
    erp-inventory:
      mode: pending
      mock-enabled: false
      callback-enabled: false
      callback-secret:
```

等价环境变量：

- MALL_ERP_INVENTORY_MODE=pending 或 mock；其他值（包括尚未实现的 remote）拒绝启动。
- MALL_ERP_MOCK_ENABLED=false。
- MALL_ERP_CALLBACK_ENABLED=false。
- MALL_ERP_CALLBACK_SECRET：启用回传时必须设置至少32字符独立密钥，不能提交到 Git。
- mock 必须同时满足 mode=mock、mock-enabled=true，且所有激活 profile 都属于 local/dev/test/unit-test。prod、production、未指定 profile 或混合未知 profile 均不允许模拟。
- WMS 只读模式沿用 product-server 的 txgy.integration.wms.readonly：mock 表示本地直接只读 WMS 表；remote 才调用 WMS 服务。真实部署的只读服务账号、租户和服务发现仍需配置，不向浏览器下发 WMS 令牌。
- WMS 查询入口仅返回当前租户已上架、未删除、明确绑定的商城 SKU；不按名称/相同数字猜测关联，不返回未绑定规格。

## 数据库

执行 deploy/mysql/repair/2026-09-12-mall-erp-inventory.sql，新增：

1. trade_erp_inventory_request：请求、商品映射快照、处理状态、尝试次数、ERP 单号和结果说明。
2. trade_erp_mock_stock：独立模拟库存，不随商城商品库存更新。
3. trade_erp_mock_order_stock：按订单记录模拟预占、释放、出库和退货累计数量。

已在本地 Docker MySQL 的 ruoyi-vue-pro 应用新增表；未插入长期测试业务数据。MySQL 事务测试数据已回滚，WMS 与 product_sku 库存数量/汇总前后相同。无需更改现有 WMS 表结构。

唯一约束 tenant_id/order_id/operation/business_key 防止同一业务重复执行。请求绑定首次预占时的 WMS 映射快照，后续商品编辑不会把释放/退货指向新规格。模拟操作与请求/订单在同一数据库事务中提交，按订单及SKU锁定；不允许超量释放、超量退回或重复初始化补货。

## 接口与权限

网关前缀 /admin-api：

- GET /trade/erp-inventory/list?orderId=...：trade:order:query。
- POST /trade/erp-inventory/retry?requestId=...：trade:order:update。仅待确认请求可登记重试；仍用原请求号，当前不实际发送至 ERP。ERP 明确拒绝的最终结果不能被重置。
- POST /trade/erp-inventory/mock/seed：trade:order:update，同时必须通过模拟环境检查。JSON：{"skuId":123,"quantity":100}。同一SKU只允许首次初始化，相同数量重试不会充值，不同数量拒绝覆盖。
- POST /trade/erp-inventory/callback：独立权限 trade:erp-inventory:callback + 有效后台服务账号 Bearer 令牌 + 同租户 tenant-id + X-ERP-Callback-Secret。默认关闭。对接时另行配置服务账号及此权限，不扩大成库存/订单后台管理权限。

回传契约：

```json
{
  "requestId": "商城记录中的原始请求号",
  "status": "CONFIRMED",
  "erpNo": "ERP真实单号",
  "message": "ERP确认的处理结果"
}
```

status 仅 CONFIRMED/REJECTED。重复相同最终结果幂等；不同最终结果/单号拒绝覆盖。正式回传不能修改 mock 请求。确认结果与商城状态推进同事务：推进失败回滚并保留待确认，已鉴权控制器另记录失败说明，不伪装成功。取消后晚到的预占确认会另建释放请求，不重新开放付款。

## 前端入口

- PC 买家订单详情、移动 H5 订单详情：ERP 记录和模拟标识。预占未确认时不显示可执行的付款入口。
- PC 销售中心：订单详情、售后详情显示 ERP 记录；发货/退货提示改为“请求已提交”，不把提交当作完成。
- web-antd：交易订单详情增加 ERP 履约记录、刷新、待确认重试、受后端保护的模拟库存初始化入口。
- 旧“缺货采购联调”的内存模拟入口已停用，不再返回虚假的 ERP 已接收/库存已就绪。
- 旧 WMS 可售库存同步/写入 RPC 失败关闭，不能用 WMS 现存库存覆盖可售缓存。商品原有手工库存字段仅是商城资料/缓存，不会改变模拟账本或真实 WMS。

## 本地测试步骤

1. 先执行商城新增表脚本；协调更新 product 和 trade，避免旧 trade 调用已停用的 product 库存写入口。
2. 测试环境为 trade 开启 mode=mock、mock-enabled=true、local/dev/test profile。真实环境保持 pending，回传关闭。
3. 用有订单管理权限的测试管理员，在后台订单详情 ERP 面板初始化待测商城SKU的独立模拟数量；或调用 mock/seed。无需 WMS 写权限。
4. 新建测试订单，确认记录标记“模拟测试”；检查付款、取消释放、发货、退货验收。
5. 验证重复提交不重复扣减；确认 WMS 和 product_sku 数量不随模拟出入库变化。
6. 切换 pending 后用新的测试订单验证：无预占确认不开放付款，无出库确认不标记发货，无退货入库确认不推进验收。
7. 回传测试需独立配置测试凭证及权限；不要通过生产凭证或真实支付测试模拟订单。既有订单按记录保存的模式处理，不直接把 pending 订单改成 mock。
8. ERP 请求明确拒绝、物流参数需要变更或历史订单状态矛盾时，先人工核实；本版不提供覆盖已确认事实的快捷修改。

## 尚待 ERP 团队确认

- 请求地址、认证方式、真实单据类型、受理与完成的状态区别。
- 商城SKU、ERP物料、WMS规格及仓库的映射规则与数量单位。
- ERP 对多渠道可售量/预占的统一控制。不能拿 WMS 现存量直接承诺库存。
- 超时后按请求号查询、幂等重试、取消与出库竞争、部分发货/部分退回等正式协议。
- 当前回传是预留契约，需双方确认后才能启用；商品/订单缓存更新由 ERP 权威结果驱动的协议尚待对接，不能借本次模拟实现冒充真实同步。

## 验证与上线范围

验证结果：商城 product/trade 后端共141项测试通过（包含33项本次新增ERP/WMS边界测试）；PC类型检查、原有51项单元测试通过；PC/H5/web-antd构建通过；买家待确认禁付、模拟标识、供应商待确认状态的独立浏览器验证通过，无页面脚本错误；MySQL DDL与事务回滚检查通过。后台全量类型检查存在非商城模块错误；本次商城文件无类型报错，不修改 CRM/培训/WMS 等模块。

本次没有重建或替换运行中的 Docker 镜像、没有提交或推送。使用新功能需要协调发布 product、trade、PC、H5、web-antd；不需要重建 WMS 或 ERP。默认 pending，不应在真实业务环境切成 mock。

事务实现参考（find-docs 查询配额不足后改查官方资料）：
[Spring Framework 程序化事务](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)。
采用共享事务与数据库约束，避免私有方法上的事务注解失效和重试重复扣减。
