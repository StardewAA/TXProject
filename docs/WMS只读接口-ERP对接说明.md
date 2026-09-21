# WMS 只读接口 — ERP 对接说明

日期：2026-09-20

提供方：WMS；调用方：ERP

用途：替换 ERP `ErpWmsInventoryReadonlyMapper` 对 WMS 表的直接查询。

## 1. 交付与联调状态

WMS 已新增下述接口源码，并于 2026-09-20 完成 JDK 25 Maven 编译和 7 项专属 H2/MyBatis 回归测试，覆盖租户隔离、逻辑删除、零库存、SKU × 仓库粒度、编码过滤、数量/时间字段和只读数据不变。尚未部署本次接口、发布 API 依赖或完成 ERP HTTP/Feign 联调；自动化测试不代表线上接口已可用。服务地址、依赖版本和部署时间由双方联调时确认。

## 2. 调用约定

| 项目 | 约定 |
|---|---|
| Feign 服务名 | `wms-server` |
| RPC 前缀 | `/rpc-api/wms/inventory-read` |
| API 模块 | `cn.iocoder.cloud:txgy-module-wms-api` |
| API 类 | `cn.iocoder.txgy.module.wms.api.inventoryread.WmsInventoryReadApi` |
| 快照 DTO | `cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO` |
| Feign contextId | `wmsInventoryReadApi` |
| 方法 | 全部为 GET，只查询数据 |
| 返回包装 | `CommonResult<T>`；成功 `code=0`，结果在 `data` 中 |
| 租户 | 使用现有 Feign 租户透传和服务端租户上下文，不接受查询参数 `tenantId` |

调用方必须具有有效租户上下文。缺少租户上下文应按调用失败处理，不能当作零库存。接口未新增业务权限注解，访问仍遵循现有内部 RPC 的安全配置；实际鉴权与租户透传须联调确认。

## 3. 库存快照

`GET /rpc-api/wms/inventory-read/snapshot`

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| materialCodes | Collection<String> | 否 | WMS 物料编码，对应 ERP material_code |
| warehouseCodes | Collection<String> | 否 | WMS 仓库编码，对应 ERP wms_warehouse_code |

不传参数或传空集合，表示该维度不限制。编码精确匹配，不进行模糊查询或自动去除空格。请勿以空字符串代替不传参数。HTTP 多值参数使用重复参数名，例如：

```text
/rpc-api/wms/inventory-read/snapshot?materialCodes=MAT-001&materialCodes=MAT-002&warehouseCodes=WH01
```

返回类型：`CommonResult<List<WmsInventorySnapshotRespDTO>>`。

| 字段 | Java 类型 | 含义 |
|---|---|---|
| materialCode | String | WMS 物料编码 |
| warehouseCode | String | WMS 仓库编码 |
| skuId | Long | SKU 主键 |
| skuCode | String | SKU 编码 |
| warehouseId | Long | 仓库主键 |
| quantity | BigDecimal | 实物库存数量；无库存记录时为 0 |
| updatedAt | LocalDateTime | 库存记录 update_time；无库存记录时为空 |

返回粒度为“物料下的 SKU × 仓库”，同一物料有多个 SKU 时会返回多行。接口返回当前租户下未逻辑删除的物料、SKU 和仓库组合；不按启用状态过滤。不存在的物料或仓库不返回；物料没有 SKU 时也不返回快照行。

对存在的 SKU 和仓库，即使没有库存记录也返回该组合，数量为 0。已有库存记录的数量直接取 `wms_inventory.quantity`，不减预留、不计算可用量。因此已预留但尚未实际出库的库存，只要仍计入该字段，就包含在本接口数量中。更新时间只表示库存记录更新时间，并非查询时间。

示例（示意数据，不是实测响应；时间非空时的 JSON 格式以部署环境统一序列化配置和联调结果为准）：

```json
{
  "code": 0,
  "msg": "",
  "data": [
    {
      "materialCode": "MAT-001",
      "warehouseCode": "WH01",
      "skuId": 101,
      "skuCode": "MAT-001-DEFAULT",
      "warehouseId": 1,
      "quantity": 0,
      "updatedAt": null
    }
  ]
}
```

接口返回真实 SKU 编码，不生成或强制要求 `-DEFAULT` 后缀。ERP 目前如只使用默认 SKU，请筛选 `skuCode == materialCode + "-DEFAULT"`。若缺少该 SKU，应作为主数据映射问题诊断，不要自行选取其它 SKU。本文不承诺所有物料未来都只有一个 SKU。

## 4. 仓库编码存在性

`GET /rpc-api/wms/inventory-read/existing-warehouse-codes`

可选参数：`warehouseCodes`，类型 `Collection<String>`；不传或空集合表示全部。

返回类型：`CommonResult<List<String>>`。只返回当前租户下未逻辑删除、实际存在的仓库编码，不按启用状态过滤。未知编码忽略；全部未匹配返回空列表。

```json
{"code":0,"msg":"","data":["WH01","WH02"]}
```

ERP 可用“请求编码集合 - 返回编码集合”得到未匹配仓库。

## 5. 物料编码存在性

`GET /rpc-api/wms/inventory-read/existing-material-codes`

可选参数：`materialCodes`，类型 `Collection<String>`；不传或空集合表示全部。

返回类型：`CommonResult<List<String>>`。只返回当前租户下未逻辑删除、实际存在的物料编码，不按启用状态过滤，也不要求物料已有 SKU 或库存。

```json
{"code":0,"msg":"","data":["MAT-001","MAT-002"]}
```

ERP 可用“请求编码集合 - 返回编码集合”得到未匹配物料。后两个接口返回字符串数组，不是包含 warehouseCode/materialCode 字段的对象数组。

## 6. ERP Feign 接入示例

引入包含本接口的 WMS API 版本，并确保现有 Feign 扫描配置覆盖 `WmsInventoryReadApi`。以下示例省略调用类其它成员：

```java
import cn.iocoder.txgy.module.wms.api.inventoryread.WmsInventoryReadApi;
import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import jakarta.annotation.Resource;
import java.util.List;

@Resource
private WmsInventoryReadApi wmsInventoryReadApi;

public void queryWms() {
    // 此处需已建立调用方租户上下文，沿用现有 Feign 租户透传。
    List<String> materials = List.of("MAT-001", "MAT-002");
    List<String> warehouses = List.of("WH01", "WH02");

    var snapshotResult = wmsInventoryReadApi.getInventorySnapshot(materials, warehouses);
    snapshotResult.checkError();
    List<WmsInventorySnapshotRespDTO> rows = snapshotResult.getData();

    var warehouseResult = wmsInventoryReadApi.getExistingWarehouseCodes(warehouses);
    warehouseResult.checkError();
    List<String> existingWarehouses = warehouseResult.getData();

    var materialResult = wmsInventoryReadApi.getExistingMaterialCodes(materials);
    materialResult.checkError();
    List<String> existingMaterials = materialResult.getData();
}
```

ERP 替换原 Mapper 的三段查询后，可按 `materialCode + warehouseCode` 关联自身产品与仓库；有多 SKU 时先执行默认 SKU 筛选，再关联。库存数量使用 BigDecimal，避免转为浮点数。

查询失败、超时或 `code != 0` 必须保留为失败状态，不能把已有镜像覆盖成 0。成功返回的零库存行与未匹配而缺失的行含义不同。

## 7. 联调验收

- 两个租户有同名物料、仓库时，结果只包含调用方租户数据。
- 指定物料和仓库，返回正确 SKU、数量及库存更新时间。
- SKU 与仓库存在但无库存记录，返回数量 0、更新时间为空。
- 未匹配编码不报业务错误，不返回该组合；两个存在性接口可诊断缺失编码。
- 一个物料多个 SKU 时全部返回，ERP 能正确选择所需默认 SKU。
- 已逻辑删除的数据不返回；没有 SKU 的物料仍可通过物料存在性接口查询到。
- 不传/空集合与多值参数均按约定查询；接口失败不被 ERP 当作零库存。
- 对照 ERP 原查询确认数量精度、预留库存口径及时间反序列化。

接口当前不分页；全量快照规模为匹配 SKU 数量乘以匹配仓库数量。ERP 日常同步建议分批传入编码，避免超长 GET URL 和过大的响应。三个接口分别执行查询，不保证跨三次调用的同一数据库快照。

## 8. 本次范围

本次提供 WMS 侧三个只读接口与 DTO。ERP 的调用替换、镜像更新及业务关联由 ERP 侧接入。接口不新增主数据、不预留或释放库存、不执行出入库。整合分支、启动步骤及验证边界见 `WMS-整合交付-20260920.md`；API 依赖发布和服务部署需按双方联调流程完成。
