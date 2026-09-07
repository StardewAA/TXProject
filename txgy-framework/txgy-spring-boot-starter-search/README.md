# Elasticsearch 8.x 与 Redis 向量检索组件

## 当前状态

这是 Maven 公共组件，不是独立微服务，也不包含 Elasticsearch/Redis 服务端。

已提供：

- `txgy.search.elasticsearch` 主检索配置，默认关闭。
- `txgy.search.cache` Lettuce 查询缓存配置，默认关闭。
- 隔离的 Elasticsearch Java Client 8.19.19 包装器，开启配置后注册。
- Lettuce 原生 `FT.CREATE`、`HSET`、`FT.SEARCH`、`DEL` 向量操作，兼容 RESP2/RESP3。
- FLOAT32 little-endian 向量编码、HNSW 索引、租户过滤、双路并行查询和 RRF 融合算法。
- `TextEmbeddingProvider` 向量模型扩展契约；本组件不绑定具体模型供应商。
- `ElasticsearchSearchOperations` 与 `SearchCacheOperations` 接口契约。
- `RedisSearchClient`、`RedisIndexManager`、`SearchQueryBuilder` 接口契约。
- 检索请求、索引文档、检索结果模型。
- JsonSearchOperations、JsonSearchRequest、JsonSearchDocument：多资料库 JSON 检索契约，支持标签和数值范围模型。
- 配置绑定、请求参数边界测试。

尚未提供 CMS 法规的 Elasticsearch Java 查询实现、查询缓存、中文分词插件、真实向量模型、业务同步和重建任务。
Maven 使用官方 Elasticsearch Java Client 8.19.19；Spring Boot 4.1 默认管理的 9.x 已被本项目显式覆盖。
Redis 使用 Spring Data Redis 默认的 Lettuce，CMS 检索组件不再直接依赖 Jedis。
原 SearchRequest / SearchDocument / RedisSearchClient / RedisIndexManager 保留为法规一期契约，不影响现有调用。
旧 Redis Search 接口仅为兼容骨架，不再作为四库主检索方案。
开启 Redis 向量配置会在首次操作时连接 6380；开启 Elasticsearch 配置会创建客户端，但具体法规查询仍由 CMS 业务层实现。

## 接口实现约束

- 租户编号来自服务端认证上下文，不能直接信任前端参数。
- 查询文本必须转义；过滤字段使用白名单；租户条件不可被用户查询覆盖。
- Elasticsearch 索引按业务类型建立版本化索引/别名，文档必须包含租户、发布状态和版本字段。
- collection 必须由服务端白名单选择，不能由用户随意指定。
- JsonSearchRequest 允许无关键词的分页浏览，但实现仍须强制租户/权限及发布状态过滤。
- TAG 和 NUMERIC 分开建模；温度覆盖查询使用 temp_min 上限与 temp_max 下限两个约束。
- JSON 数值时间统一为 Unix 毫秒；普通列表/附件不自动当作可搜索字段，按 schema 投影。
- JsonSearchDocument 只复制顶层 Map，生产者须提供安全、不可变、符合 JSON schema 的嵌套值。
- 源记录编号和版本用于幂等同步，更新后需处理旧段落，防止重复命中。
- 结果返回前由业务层核对最新权限、发布状态及版本。
- Elasticsearch 是可重建检索副本，MySQL 原始资料是事实来源。
- Redis 向量记录只保存向量、租户、版本和指向 Elasticsearch 的 `source_id`，不保存法规全文。
- Redis HNSW 检索强制添加租户 TAG 条件；模型 ID 或向量维度变化时必须重建索引。
- 索引副本的检索分数不是答案正确率，也不是法律效力判断。

## IDEA 中已有的普通 Java 模板

原有 `src/Main.java` 与 `txgy-spring-boot-starter-search.iml` 保持不变。
`src/Main.java` 不属于 Maven 标准源目录，不参与本模块构建。
原手工 IDEA 模块指定了 JDK 21；请从项目根 pom.xml 重新加载 Maven，并使用根项目要求的 JDK 25。
不需要运行这个公共组件中的 Main 模板。

`deploy/docker-compose.search.yml` 可启动 Redis 8 与 Elasticsearch 8.19.19 本地开发服务。
新架构见 [CMS 搜索架构](../../txgy-module-cms/docs/search-architecture.md)。
