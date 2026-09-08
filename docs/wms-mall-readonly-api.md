# WMS 商品规格、销售价及库存只读接口

## 范围

仅修改 WMS。商城团队负责规格绑定及页面展示，本次不修改商城、订单、扣库存逻辑或数据库结构。

## 调用

`GET /admin-api/wms/inventory/sku-summary?itemId=123`

本地网关地址：`http://localhost:48080`。itemId 是 WMS 商品 ID，不是商城 SPU ID；返回的 skuId 是 WMS 规格 ID，不是商城 SKU ID。商城团队必须显式建立对应关系，不能按同名或相同数字自动关联。

请求头：

```http
Authorization: Bearer <WMS只读账号访问令牌>
tenant-id: <该账号所属企业租户ID>
```

由商城服务端调用，不把 WMS 管理账号令牌交给浏览器。使用专用账号，仅授予 `wms:inventory:query` 查询权限，不授予入库、出库、盘点、商品编辑等写权限。这个权限允许查询该租户库存，商城服务端需再限制消费者可见的已关联商品。

沿用项目已有身份认证、权限和租户拦截机制，不新增匿名访问，也不忽略租户。不允许使用其他企业的令牌跨租户查询；客户端传 tenant-id 不代表取得该企业授权。

```powershell
Invoke-RestMethod -Uri 'http://localhost:48080/admin-api/wms/inventory/sku-summary?itemId=123' -Headers @{
  Authorization = 'Bearer <访问令牌>'
  'tenant-id' = '<同一企业租户ID>'
}
```

## 返回示例

```json
{
  "code": 0,
  "data": [{
    "itemId": 123,
    "itemCode": "PIPE-001",
    "itemName": "管材",
    "skuId": 456,
    "skuCode": "PIPE-DN20",
    "skuName": "DN20",
    "unit": "米",
    "sellingPrice": 12.50,
    "totalQuantity": 125.75
  }],
  "msg": ""
}
```

- 商品所有未删除规格按 skuId 升序返回，包括没有库存记录的规格（数量为 0）。
- sellingPrice 直接读取 WMS 销售价，单位为元，null 表示未设置，不得当作免费。不要套用商城以分计价的转换。
- totalQuantity 合计当前租户该规格所有未删除仓库库存记录，包括负库存，不做截断；这是现存数量，不是已扣预留的可售库存。
- 数量和价格使用 BigDecimal 汇总、传递，接入方使用十进制类型，显示单位来自 unit。
- 成本价、内部备注、仓库明细不返回。查询没有任何写库、冻结、预留或扣减行为。
- 商品不存在或不属于当前租户：沿用 WMS 商品不存在错误；没有规格：data 为 []。
- 权限不足、令牌失效、数据库异常都按项目错误格式返回；接入方必须检查 code 和 HTTP 状态，错误显示“暂时无法获取”，不能显示为零库存。

## 验收

1. 用只读账号请求本租户商品，对照 WMS 各仓库存量手工相加，核对每种规格及销售价。
2. 请求无库存规格应为 0，未设置售价应为 null。
3. 无令牌、无查询权限、跨租户商品均不可获取数据。
4. 同一只读账号尝试写操作应被拒绝。此 GET 端点没有 POST/PUT/DELETE 实现。
5. 查询前后库存余额、库存流水、商品销售价不变。

只需重新构建部署 WMS 服务，无数据库迁移，无商城构建。
