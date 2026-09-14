# CRM 读取 WMS 商品接口

用途：CRM 商机页面点击“添加商品”时读取 WMS 商品及规格。仅新增查询接口，不创建、修改商品，不扣减或预留库存，不产生库存流水。无数据库迁移、无前端改动。

## 请求

`GET /admin-api/wms/crm/products/page`

主机地址使用部署环境中可访问 WMS 的网关地址，由部署方提供。本文不代表接口已部署。

沿用现有系统认证：

```http
Authorization: Bearer <有效访问令牌>
tenant-id: <该账号所属租户ID>
```

调用账号需要现有权限 `wms:item:query`。令牌须由现有认证系统签发并被 WMS 接受，不能直接假定任意 CRM 令牌可用；由管理员分配相应租户的账号及查询权限。`tenant-id` 不是身份凭证，不应通过更换请求头跨租户读取。接口不接受查询参数中的 tenantId，数据库关联的商品及 SKU 均约束当前租户、未删除状态；缺少租户上下文拒绝查询。

建议 CRM 后端调用，勿在页面代码中硬编码长期令牌。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| pageNo | integer | 1 | 大于等于 1 |
| pageSize | integer | 10 | 1–200，不支持 -1 全量读取 |
| materialCode | string | 无 | WMS 商品编码，精确匹配 |
| materialName | string | 无 | 商品名称包含匹配，沿用 SQL LIKE 规则，% 和 _ 是通配符 |
| skuCode | string | 无 | WMS SKU 编码，精确匹配 |

筛选条件组合生效，空字符串不筛选。中文参数需进行 URL 编码。

```bash
curl --get "<WMS_BASE_URL>/admin-api/wms/crm/products/page" \
  --header "Authorization: Bearer <TOKEN>" \
  --header "tenant-id: <TENANT_ID>" \
  --data-urlencode "pageNo=1" \
  --data-urlencode "pageSize=20" \
  --data-urlencode "materialName=管"
```

## 返回

以下为字段示例，不是实际业务数据：

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "itemId": 100,
        "skuId": 101,
        "materialCode": "DEMO-PIPE",
        "materialName": "示例管材",
        "skuCode": "DEMO-PIPE-20",
        "skuName": "20mm",
        "unitName": "米"
      }
    ],
    "total": 1
  },
  "msg": ""
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemId | long | WMS 商品 ID |
| skuId | long | WMS SKU ID，选中规格的标识 |
| materialCode / materialName | string | 商品编码 / 名称 |
| skuCode / skuName | string | 规格编码 / 规格名称 |
| unitName | string | 商品单位，未维护时可为 null |
| total | long | 当前筛选下 SKU 总条数 |

ID 使用现有系统 Long 序列化约定。CRM 应按外部标识保存，不应把 WMS ID 当作 CRM 数据库商品 ID；前端处理大整数时避免精度丢失。

每个 SKU 一行，按商品 ID、SKU ID 稳定排序；只返回启用商品（status=0），排除删除的商品及 SKU。不限制物料类型，不依赖库存表，因此没有库存记录或库存为零的商品仍可返回；没有 SKU 的商品不返回。查询结果不是可售库存承诺，也不包含价格或成本。

无匹配数据或页码超出结果时，`list` 为空；`total` 为筛选总数。不同页是实时查询，不提供跨页一致性快照。

## 错误处理

沿用系统 CommonResult，必须判断响应体 `code`，不能只检查 HTTP 状态。

| 业务码 | 处理 |
| --- | --- |
| 0 | 成功，读取 data.list 和 data.total |
| 400 | 参数格式或分页范围错误，修正参数 |
| 401 | 未登录或令牌失效，重新获取有效令牌 |
| 403 | 查询权限或租户访问不满足要求，由管理员核对 |
| 其他非零 | 查询失败，展示 msg；不要视为空商品列表 |

网络错误或服务暂不可用可有限重试；认证、权限和参数错误应先修正。查询可重复调用，没有写操作或幂等键要求。

## 实现与验证范围

新增 Controller、请求/响应 VO、只读 Service 和 SELECT Mapper，全部位于 WMS server 模块。没有修改已有商品/库存实现。

定向测试 `WmsCrmProductQueryServiceTest` 使用 H2 内存数据库，覆盖租户隔离及非法跨租户关联、停用/删除过滤、无库存商品、分页与组合筛选、注入形式编码、缺少租户、分页越界，并检查不新增库存和流水。另运行现有 `WmsStockQueryServiceTest`。

尚待部署环境使用真实认证令牌进行 HTTP 鉴权和 CRM 联调。未创建账号、未修改真实业务数据、未提交或推送。

### 2026-09-14 本机执行记录

在项目根目录执行（PowerShell）：

```powershell
& 'C:\Users\XWB\.m2\wrapper\dists\apache-maven-3.9.11\03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc\bin\mvn.cmd' -o '-Dmaven.repo.local=D:\Projects\tianxin-pipe-industry\txgycloud-wms\.maven-home\repository' '-Duser.home=D:\Projects\tianxin-pipe-industry\txgycloud-wms\.maven-home' -pl txgy-module-wms/txgy-module-wms-server -am '-Dtest=WmsCrmProductQueryServiceTest,WmsStockQueryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q
```

退出码 0：新查询测试 3 项、现有库存查询测试 4 项，共 7 项通过，失败和错误均为 0。使用 JDK 25 和项目内 Maven 依赖缓存。新测试首次在实现缺失时编译失败；补齐实现后，测试启动曾因默认日志目录 `C:\logs\nacos` 无法创建而失败，指定测试进程的 `user.home` 到工作区后运行成功。保留依赖的 Unsafe、重复 JSON 类等警告，未将其描述为无警告构建。

只读代码审查未发现必须修复的问题。尚未执行部署、真实 HTTP 鉴权测试或 CRM 页面联调。
