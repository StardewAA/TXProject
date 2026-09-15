# Flowable 8 表结构初始化（本地开发环境专用）

## 背景

本地开发库 `ruoyi-vue-pro` 没有 Flowable（BPM 工作流引擎）的表，而 BPM 服务的
`flowable.database-schema-update` 在主配置里被设为 `false`（注释：库表由初始化脚本建立）。

但仓库的 `deploy/mysql/init/0011-source-bpm.sql` 只包含 BPM 业务表（`bpm_form` 等），
**不含 Flowable 引擎的 `ACT_*` 表**。

直接改成 `true` 让 Flowable 自动建表，在 Flowable 8.0.0 + MySQL 8.4 下会反复失败，原因有：

1. **建表脚本按引擎拆分**：`common` / `engine` / `history` / `identity` / `eventregistry` 各自独立，
   且脚本内部建表语句的顺序存在跨表外键依赖，`FOREIGN_KEY_CHECKS=1` 时先建的表会因引用后建的表而失败。
2. **mysql 客户端默认遇错即停**：一条语句失败，后面的建表语句全部跳过。
3. **版本数据分散**：各引擎启动时校验自己的 `xxx.schema.version`，缺失任何一个都会报
   `FlowableWrongDbException: version mismatch` 并崩溃循环。

## 初始化步骤（一次性）

在 **同一 mysql 会话内**、关闭外键检查的前提下，按顺序执行 5 个脚本各两遍
（第二遍补齐第一遍因顺序失败而遗漏的索引/外键）：

```bash
docker exec -i txgy-mysql mysql -f --default-character-set=utf8mb4 -uroot -p'密码' ruoyi-vue-pro <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
SOURCE flowable.mysql.create.common.sql
SOURCE flowable.mysql.create.engine.sql
SOURCE flowable.mysql.create.history.sql
SOURCE flowable.mysql.create.identity.sql
SOURCE flowable.mysql.create.eventregistry.sql
SOURCE flowable.mysql.create.common.sql
SOURCE flowable.mysql.create.engine.sql
SOURCE flowable.mysql.create.history.sql
SOURCE flowable.mysql.create.identity.sql
SOURCE flowable.mysql.create.eventregistry.sql
SET FOREIGN_KEY_CHECKS = 1;
SQL
```

> 注意：`SOURCE` 需要 SQL 文件在 mysql 容器内（先 `docker cp` 进去），或先拼接成一个文件后
> 通过 `< file.sql` 重定向（务必使用 UTF-8 无 BOM）。

执行后校验：

```sql
SELECT COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA='ruoyi-vue-pro' AND TABLE_NAME LIKE 'ACT_%';   -- 应 >= 39

SELECT NAME_, VALUE_ FROM ACT_GE_PROPERTY
WHERE NAME_ IN ('schema.version','common.schema.version','eventregistry.schema.version');
-- 三个版本行都应存在，VALUE_ = '8.0.0.0'
```

## 启动 BPM 服务

表结构初始化完成后，BPM 服务启动时用 `false`（仅校验版本、不改表），
通过 JVM 系统属性显式声明（`application.yaml` 因 Nacos import 的处理顺序会覆盖 profile 文件，
环境变量的 relaxed binding 在 Spring Boot 4 下又无法映射中划线属性）：

```yaml
# docker-compose.local.yml 的 bpm 服务
JAVA_OPTS: -Xms256m -Xmx512m -XX:+UseG1GC -Dflowable.database-schema-update=false
```
