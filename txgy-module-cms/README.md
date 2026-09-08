# CMS 骨架：法规与四大专业资料库

## 本次交付范围

已落地 Maven 结构、接口契约、资料模型、搜索服务部署、Elasticsearch 法规查询、HTTP 接口、客户端、向量索引操作和 RRF 算法骨架。
保留原有行业法规/劳动法骨架，追加标准规范、材料参数、工法案例、资质定额四大库。
资质、定额、价格分别建模，不把四大库拆成四个微服务。
尚未完成业务 CRUD、数据库字段映射、索引同步、真实向量模型或 AI 解读；法规检索已经与 `cms-policy-green` 的门户页面完成接口对接。
没有执行 SQL、迁移业务数据或向远程 Git 推送；本机已启动 Redis 8 和 Elasticsearch 8.19.19，并创建空法规索引。

材料只预留条件筛选模型，不包含真实选型算法、工程计算或推荐结论。
本次不包含金融政策、碳成本计算、专家咨询、订单或支付，也不自动采集网络资料。

- [Elasticsearch 8.x 与 Lettuce 搜索架构](docs/search-architecture.md)
- [CMS 法规检索前后端接口](docs/frontend-integration.md)

## Maven 模块

| 模块 | 职责 | 独立启动 |
| --- | --- | --- |
| txgy-module-cms | 聚合 CMS 子模块 | 否 |
| txgy-module-cms-api | 法规与四库查询接口、DTO、枚举 | 否 |
| txgy-module-cms-server | CMS 服务启动入口、法规检索实现和 HTTP 接口 | 是，需提供数据库账号等本地环境配置 |
| txgy-spring-boot-starter-search | Elasticsearch 8.x 客户端、Lettuce 向量检索与 RRF | 否 |

CMS Server 依赖 CMS API、System API、Infra API 和现有安全、租户、数据库、RPC、任务等组件。
公共检索组件不依赖 CMS 或 AI 业务模块，后续可以被两者复用。
目前没有修改 AI Server 的依赖或知识库实现，CMS 也不直接依赖 AI Server。

## 目录职责

```text
txgy-module-cms/
├── pom.xml
├── txgy-module-cms-api/
│   ├── pom.xml
│   └── src/main/java/cn/iocoder/txgy/module/cms/
│       ├── api/law/              保留法规查询契约
│       ├── api/{standard,material,workmanship,cost}/  四库 Java 契约
│       ├── dto/law/              原有法规请求与响应
│       ├── dto/{common,standard,material,workmanship,projectcase}/
│       ├── dto/{qualification,quota,price,cost}/
│       └── enums/                资料类型、标准/法律效力、发布状态
└── txgy-module-cms-server/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/cn/iocoder/txgy/module/cms/
        │   │   ├── CmsServerApplication.java
        │   │   ├── controller/admin/law/
        │   │   ├── controller/app/law/
        │   │   ├── controller/{admin,app}/library/
        │   │   ├── service/law/
        │   │   ├── service/{standard,material,workmanship,projectcase}/
        │   │   ├── service/{qualification,quota,price}/
        │   │   ├── service/article/
        │   │   ├── service/search/
        │   │   ├── service/interpretation/
        │   │   ├── service/sync/
        │   │   ├── importer/
        │   │   ├── source/
        │   │   ├── dal/dataobject/
        │   │   ├── dal/mysql/
        │   │   └── job/
        │   └── resources/
        │       ├── application.yaml
        │       ├── application-local.yaml
        │       └── mapper/
        └── test/java/cn/iocoder/txgy/module/cms/
            ├── search/
            └── sync/
```

预留包使用 package-info.java 说明职责，便于 Git 和 IDEA 保留目录。
article 表示法规章节/条款，不是商城文章。
CmsLawIndexSyncJob 目前只是任务接口，没有定时调度器或执行实现。
CmsIndexSyncTask / CmsIndexSyncService 是通用 MySQL 同步任务模型与契约，没有建表或任务消费者。
法规查询已暴露管理端和门户 HTTP 接口；四库 API 仍只是 Java 接口，不会出现在可用 HTTP 接口列表中。

## 构建与 IDEA 导入

在项目根目录执行，使用 JDK 25：

```powershell
mvn -pl txgy-module-cms/txgy-module-cms-server -am validate
mvn -pl txgy-module-cms/txgy-module-cms-server -am "-Dtest=SearchBackendAutoConfigurationTest,RedisSearchAutoConfigurationTest,SearchRequestTest,JsonSearchContractTest,CmsLawSearchReqDTOTest,CmsLawSearchServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

第一条只检查 Maven 模型，不代表编译通过；第二条编译并运行契约与参数校验测试，不连接数据库。
Redis 与 Elasticsearch 真实集成测试默认跳过，可分别用 `-Dtxgy.test.redis-vector=true -Dtest=RedisVectorSearchIntegrationTest`、`-Dtxgy.search.elasticsearch.integration=true -Dtest=CmsLawSearchServiceIntegrationTest` 显式运行。
依赖全部已缓存时可加 -o 离线执行；缺少依赖时需先允许 Maven 下载。
构建不会自动启动服务，也不会自动应用数据库变更。
IDEA 中重新加载根 pom.xml 的 Maven 项目，检查 Project SDK、Maven Runner 和各模块使用 JDK 25。

## 运行配置预留

- 服务名：cms-server；主类：cn.iocoder.txgy.module.cms.CmsServerApplication。
- 默认端口：48100，可通过 CMS_SERVER_PORT 调整。
- 业务数据库：复用现有库，使用 TXGY_DB_HOST、TXGY_DB_PORT、TXGY_DB_NAME、TXGY_DB_USERNAME、TXGY_DB_PASSWORD。
- 数据库账号与密码没有默认值，需从已有本地环境提供，不要提交真实密码。
- Lettuce 缓存 Redis：TXGY_CACHE_REDIS_HOST / PORT，默认 127.0.0.1:6379。
- Elasticsearch：TXGY_SEARCH_ES_URIS，默认 http://127.0.0.1:9200；账号密码不写入仓库。
- Elasticsearch 索引前缀为 txgy-cms，后续按资料类型建立版本化索引和读写别名。
- SQL 初始化关闭；法规 Elasticsearch 检索默认开启，Redis 向量检索、Nacos 注册、XXL-Job 默认关闭。
- 完成业务后如需注册服务，设置 CMS_NACOS_ENABLED=true，并配置正确的 Nacos 地址、命名空间和认证信息。
- 网关已预留 /admin-api/cms/** 与 /app-api/cms/** 路由；CMS 尚未启动注册时，不代表这些接口可用。
- 不需要修改 txgy-server 的组合启动依赖。

## Redis 8 向量检索服务

deploy/docker-compose.search.yml 同时启动 Elasticsearch 主检索和 Redis 向量检索服务。
本机开发环境使用 redis:8.2，绑定 127.0.0.1:6380，AOF 数据持久化到 D:/Redis-Stack/data。
它不会替换 deploy/docker-compose.yml 中使用 6379 的普通 Redis 缓存服务。启动命令：

```powershell
docker compose -f deploy/docker-compose.search.yml --profile search up -d
powershell -ExecutionPolicy Bypass -File deploy/elasticsearch/init-cms-law-index.ps1
```

该配置只绑定本机且尚未设置密码，仅用于本地开发，不能直接作为生产部署。
当前已验证 HNSW 索引、Java/Lettuce 向量相似度、租户隔离和 Elasticsearch 精确编号查询。
若 CMS 在容器内运行，须另行配置容器网络和检索 Redis 地址。

## 后续实现顺序

1. 确认已有法规/资料表和条款结构，制定四库字段映射，不盲目创建第二套数据库。
2. 实现资料导入、版本、来源、效力、审核发布和权限校验。
3. 实现原始数据与 MySQL 同步任务同事务保存、幂等处理、失败重试、旧段落移除；Streams 暂不引入。
4. 接入真实 `TextEmbeddingProvider`，并将 Elasticsearch BM25 与 Redis 结果用 RRF 融合；再实现有原文引用的 AI 解释。
5. 增加历史查询、跨租户、下架、乱序同步及引用正确性的验收测试。

直接用 SQL 修改业务表不会自动产生后台业务事件，需要另外实现增量扫描或数据库变更捕获。
平台下架不等于法规废止，历史版本不能简单全部删除。
