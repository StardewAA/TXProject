# CMS 法规检索前后端接口

一期只开放行业法律法规、劳动法和行业标准规范的检索。管理端与门户使用同一查询模型，租户编号只从请求头读取，不接受前端通过查询参数传入。

## 接口

| 使用方 | 方法与地址 | 说明 |
| --- | --- | --- |
| Vben 管理端 | `GET /admin-api/cms/law/search` | 分页检索 |
| Vben 管理端 | `GET /admin-api/cms/law/get?articleId=...` | 条款详情 |
| 用户门户 | `GET /app-api/cms/law/search` | 仅查询已发布内容 |
| 用户门户 | `GET /app-api/cms/law/get?articleId=...` | 已发布条款详情 |

查询参数：

- `keyword`：必填，法规名称、文号、标准编号、条款号或自然语言问题；最长 200 字。
- `lawType`：可选，当前前端使用 `INDUSTRY_REGULATION`、`LABOR_LAW`、`STANDARD`。
- `industryCode`、`regionCode`、`status`、`asOfDate`：可选筛选条件。
- `pageNo`：从 1 开始。
- `pageSize`：1 至 100。

返回值使用项目统一的 `CommonResult<PageResult<CmsLawSearchRespDTO>>`，前端请求拦截器解包后获得：

```json
{
  "list": [
    {
      "lawId": 101,
      "articleId": 202,
      "title": "工业设备及管道绝热工程设计规范",
      "documentNo": "GB 50264-2013",
      "articleNo": "第5.1.2条",
      "content": "条款正文",
      "issuingAuthority": "住房和城乡建设部",
      "effectiveDate": "2013-10-01",
      "status": "EFFECTIVE",
      "sourceUrl": "https://...",
      "score": 18.25
    }
  ],
  "total": 1
}
```

## 运行条件

- CMS 服务：`48100`
- 网关：`48080`
- Elasticsearch：`127.0.0.1:9200`
- ES 开关：`TXGY_SEARCH_ES_ENABLED=true`（CMS 默认已开启）
- 索引读别名：`txgy-cms-law-read`
- 前端必须传 `tenant-id`；管理端现有请求拦截器已自动处理。

Redis 向量检索目前不在 HTTP 请求链路中。待真实嵌入模型确定并完成历史文档向量化后，再启用 `TXGY_SEARCH_VECTOR_ENABLED` 和 RRF 混合排序，避免用假向量产生错误结果。
