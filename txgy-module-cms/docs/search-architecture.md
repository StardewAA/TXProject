# 法律混合检索架构：Elasticsearch 8.x + Redis 8 + Lettuce

## 当前边界

当前建立部署、客户端、向量索引操作、编号规范化和 RRF 排序骨架。真实法规数据、文本切片策略和向量模型尚未接入，不修改现有 MySQL 数据。

## 组件职责

| 组件 | 职责 |
| --- | --- |
| MySQL | 原始资料、审核发布、历史版本、权限与同步任务，业务事实来源 |
| Elasticsearch 8.19.x | 法规全文与元数据存储；BM25、文号和条款编号精确检索 |
| Elasticsearch Java Client 8.19.19 | Spring Boot 中访问 Elasticsearch；不得使用 Boot 4.1 默认管理的 9.x 客户端 |
| Redis 8 + Lettuce | HNSW 向量语义检索；只保存向量、租户、版本和 ES `source_id` 指针 |
| 文件服务 | PDF、图片和视频，检索文档只保存附件引用和已授权文本 |
| 向量模型 | 通过 `TextEmbeddingProvider` 接入；模型及维度变化必须重建 Redis 索引 |
| 融合层 | 用 RRF 合并 BM25 与语义检索名次，再从 ES 批量读取完整结果 |

## 查询链路

1. 服务端从认证上下文取得租户，规范化 `GB 50264`、`第二百三十二条` 等编号。
2. Elasticsearch 执行 BM25、精确编号和元数据过滤查询。
3. 同一查询由选定向量模型编码，Redis HNSW 在当前租户范围内查找相似条款。
4. RRF 融合两路名次，按 `source_id` 从 Elasticsearch 批量取回全文和来源。
5. 返回前复核发布状态、版本与权限；模型生成内容不得替代法规原文。

## 索引规则

- 法规模板为 `deploy/elasticsearch/cms-law-index-template.json`，物理索引使用 `txgy-cms-law-v1-*`。
- 读取别名为 `txgy-cms-law-read`，写入别名为 `txgy-cms-law-write`。
- 使用稳定的读写别名切换重建索引，不在生产环境直接删除后原地重建。
- 法规长正文按条款拆分，保存法规 ID、条款 ID、业务版本和原文位置。
- 编号、状态、地区和分类使用 keyword；标题与获授权正文使用 text；时间和数值使用对应类型。
- 中文分析器、同义词及专业词库必须在目标 Elasticsearch 8.19.x 环境验证后确定，不写死未安装的第三方插件。
- Redis 索引名为 `txgy:cms:law:vector-idx-v1`，每条向量强制携带 `tenant_id`。

## 更新链路

CMS 业务记录和同步任务在同一个 MySQL 事务提交。消费者领取任务后回源读取最新修订号，先幂等写入 Elasticsearch，再生成向量并更新 Redis 指针。下架时同步删除 ES 投影和对应 Redis 向量。旧任务不能覆盖新版本，失败任务需要退避重试和人工处理记录。

直接修改数据库不会自动产生同步任务；后续需要增量扫描或 CDC。Redis Streams 可作为任务分发通道，但不能代替 MySQL 中可恢复的同步记录。Redis 向量数据丢失后必须能从 ES/MySQL 重建。

## 本地启动

```powershell
docker compose -f deploy/docker-compose.search.yml --profile search up -d
powershell -ExecutionPolicy Bypass -File deploy/elasticsearch/init-cms-law-index.ps1
```

启动 CMS 时按实际向量模型设置 `TXGY_SEARCH_VECTOR_DIMENSIONS`，然后显式设置 `TXGY_SEARCH_ES_ENABLED=true` 与 `TXGY_SEARCH_VECTOR_ENABLED=true`。没有真实向量模型实现时不得启用语义查询或写入占位向量。

## 版本说明

项目使用 Spring Boot 4.1.0，其 BOM 默认管理 Elasticsearch Java Client 9.4.2。目标服务端指定为 8.x，因此项目依赖管理模块显式锁定 Java Client 和 legacy REST Client 为 8.19.19。正式部署建议服务端采用同一 8.19.x 系列，并在确定补丁版本后一起升级验证。

参考：

- [Redis 向量检索](https://redis.io/docs/latest/develop/ai/search-and-query/vectors/)
- [Elasticsearch Java Client 8.19 安装](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.19/installation.html)
- [Elasticsearch Java Client 兼容策略](https://www.elastic.co/docs/reference/elasticsearch/clients/java)
