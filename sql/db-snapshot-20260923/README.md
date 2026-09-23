# 数据库快照 2026-09-23

本目录保存天信管业防腐保温智慧平台在 **2026-09-23 08:38（GMT+8）** 的数据库全量导出，
用于与同一分支上的 HRM 代码快照配套留存。

## 导出来源

| 项 | 值 |
|---|---|
| 容器 | `txgy-mysql`（`mysql:8.4`，宿主机映射 `3307 -> 3306`） |
| 导出时间 | 2026-09-23 08:38:22 ~ 08:38:26 |
| mysqldump 参数 | `--default-character-set=utf8mb4 --single-transaction --routines --triggers --events --databases` |

> 必须带 `--default-character-set=utf8mb4`，否则中文会导出成乱码。

## 文件清单

| 文件 | 库 | 表数 | 大小 |
|---|---|---|---|
| `ruoyi-vue-pro.sql` | `ruoyi-vue-pro`（主业务库） | 545（其中 `hrm_*` 66 张） | 29 MB |
| `txgy_tenant_import.sql` | `txgy_tenant_import` | 9 | 3.7 MB |
| `xxl_job.sql` | `xxl_job`（调度中心） | 8 | 16 KB |

HRM 相关数据全部位于 `ruoyi-vue-pro`：`hrm_*` 业务表、以及 `system_menu` 中的 HRM 菜单与权限点。

三个文件均以 `-- Dump completed on ...` 结尾，可据此判断导出是否完整。

## 恢复方式

```bash
cd txgycloud/deploy
PW=$(grep -E "^MYSQL_ROOT_PASSWORD=" .env | cut -d= -f2-)

docker exec -i txgy-mysql mysql --default-character-set=utf8mb4 -uroot -p"$PW" \
  < ../sql/db-snapshot-20260923/ruoyi-vue-pro.sql
```

三个库彼此独立，可按需单独恢复。

## 注意事项

- **这是带数据的快照，不是结构变更脚本。** 日常增量变更请继续按 `sql/2026-09-XX-*.sql` 的约定新增脚本，
  不要把本目录当作迁移脚本使用（文件里含 `DROP TABLE IF EXISTS`，重复执行会覆盖数据）。
- 快照包含用户账号、角色、菜单等真实数据，属内部资料，不要外发。
- 本目录未被 `.gitignore` 排除。仓库 `.gitignore` 刻意忽略的是 `sql/mysql/`、
  `deploy/mysql/init/*mysql-all-databases*.sql*` 与 `deploy/backup_*.sql` 这几类历史全库导出，
  本快照是单独指定的留存位置。
