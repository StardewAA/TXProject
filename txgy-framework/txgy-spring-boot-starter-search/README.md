# Redis 检索组件骨架

## 当前状态

这是 Maven 公共组件，不是独立微服务，也不是 Redis 服务端。

已提供：

- 独立配置命名空间 `txgy.search.redis`，默认端口 6380、开关关闭。
- Spring Boot 自动配置入口与配置绑定。
- `RedisSearchClient`、`RedisIndexManager`、`SearchQueryBuilder` 接口契约。
- 检索请求、索引文档、检索结果模型。
- JsonSearchOperations、JsonSearchRequest、JsonSearchDocument：多资料库 JSON 检索契约，支持标签和数值范围模型。
- 配置绑定、请求参数边界测试。

尚未提供 Redis Search 命令实现、实际中文索引、全文/向量索引、排名算法或重建逻辑。
原 SearchRequest / SearchDocument / RedisSearchClient / RedisIndexManager 保留为法规一期契约，不影响现有调用。
四库使用新增 JSON 契约，数字和数组不再压成字符串 metadata；该契约不依赖 CMS 业务枚举。
即使手动将 enabled 改为 true，也不会凭空获得检索能力；需要先实现客户端和索引管理。
目前不会连接 Redis，也不会替换现有缓存 Redis 的连接工厂。

## 接口实现约束

- 租户编号来自服务端认证上下文，不能直接信任前端参数。
- 查询文本必须转义；过滤字段使用白名单；租户条件不可被用户查询覆盖。
- 索引按业务前缀和租户隔离，只更新本模块拥有的资料。
- 根前缀默认 txgy:cms，具体业务 collection 必须由服务端白名单选择，不能由用户随意指定。
- JsonSearchRequest 允许无关键词的分页浏览，但实现仍须强制租户/权限及发布状态过滤。
- TAG 和 NUMERIC 分开建模；温度覆盖查询使用 temp_min 上限与 temp_max 下限两个约束。
- JSON 数值时间统一为 Unix 毫秒；普通列表/附件不自动当作可搜索字段，按 schema 投影。
- JsonSearchDocument 只复制顶层 Map，生产者须提供安全、不可变、符合 JSON schema 的嵌套值。
- 源记录编号和版本用于幂等同步，更新后需处理旧段落，防止重复命中。
- 结果返回前由业务层核对最新权限、发布状态及版本。
- Redis 只是可重建索引，MySQL 原始资料是事实来源。
- 索引副本的检索分数不是答案正确率，也不是法律效力判断。

## IDEA 中已有的普通 Java 模板

原有 `src/Main.java` 与 `txgy-spring-boot-starter-search.iml` 保持不变。
`src/Main.java` 不属于 Maven 标准源目录，不参与本模块构建。
原手工 IDEA 模块指定了 JDK 21；请从项目根 pom.xml 重新加载 Maven，并使用根项目要求的 JDK 25。
不需要运行这个公共组件中的 Main 模板。

当前独立模板使用 Redis 8.2 镜像；这里只是配置，尚未启动服务或创建索引。
Redis 8 已整合 Search 与 JSON，参见 [Redis 官方说明](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/modules-lifecycle/)。
业务字段与不自动执行的命令示例见 [CMS 索引设计](../../txgy-module-cms/docs/redis-index-design.md)。
