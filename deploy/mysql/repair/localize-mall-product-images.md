# 商城商品图片本地化脚本

`localize-mall-product-images.py` 将已随 PC 前端打包的商品图片上传到现有文件服务，并更新对应图片地址。默认仅检查；只有传入 `--apply` 才上传并执行更新。

## 操作范围

- 固定租户 `tenant_id=1`、未删除的商品，SPU 编号为 `910001..910030`。
- 仅处理封面地址属于脚本 `HOSTS` 白名单的商品，不下载第三方图片。
- 本地文件默认位于 `txgy-mall-web/public/product-images/<SPU编号>.jpg`。
- 更新商品封面、包含相同旧地址的轮播图及详情，以及对应 SKU、订单明细和售后记录中仍使用相同旧地址的图片字段。
- 不修改商品价格、库存、企业资料、WMS 或 ERP 表。

## 并发安全修复（2026-09-15）

旧实现先提交 SQL，再另开连接核对。若管理员在执行期间编辑详情，部分 UPDATE 会跳过，但其他图片字段已提交；重跑又会因为封面已变为本地地址而跳过商品。

现在整批操作在同一连接、同一事务内完成：

1. 检查涉及的表都使用 InnoDB；不满足时不执行图片修改。
2. 按固定表名、编号、字段顺序执行带原值条件的 UPDATE，使用字节级比较识别大小写、尾部空格等差异。
3. 每条 UPDATE 立即检查受影响行数，并在提交前复核所有最终字段值，避免静默截断。
4. 全部检查通过才提交；任一字段冲突、记录删除或租户不匹配，整批回滚，保留管理员已经提交的编辑。
5. `restore.sql` 同样整批校验和回滚，避免只恢复部分字段或覆盖后续人工修改。

MySQL 客户端显式关闭遇错继续执行及自动重连。出现 SQL 错误时终止会话，不继续执行提交。失去提交确认等不确定情况不会被报告为“已回滚”；需结合备份检查实际状态。

## 使用方式

从项目根目录执行，数据库凭据通过本地环境变量 `MYSQL_PWD` 提供，不把真实密码放进 Git 或命令示例：

```powershell
# 仅检查：不上传、不修改图片字段
python -B deploy/mysql/repair/localize-mall-product-images.py --output E:/txgy/local-backups/mall-images-check

# 确认范围和运行环境后再应用；输出目录必须尚不存在
python -B deploy/mysql/repair/localize-mall-product-images.py --output E:/txgy/local-backups/mall-images-apply-20260915 --apply
```

可用 `--container`、`--database`、`--gateway`、`--source` 覆盖默认本地连接及图片目录。执行前核对目标，勿在迁移过程中同时变更相关表结构或存储引擎。

应用前写出 `image-fields-before.json`、`apply.sql`、`restore.sql`。如果返回 `MALL_IMAGES_ROLLED_BACK`，本批图片字段均未提交；核实原因后，用新的输出目录重跑，让脚本重新读取人工编辑后的内容。已正常完成的商品重跑时会跳过，不重复上传。

需要单独执行生成的 SQL 时，必须在同一连接中完整执行文件，并使用 `mysql --skip-force --skip-reconnect`，检查最终结果为 `MALL_IMAGES_COMMITTED`；不能只复制中间 UPDATE、追加无条件 COMMIT 或开启遇错继续执行。

事务只覆盖图片地址字段。先前已经上传的文件不随数据库回滚自动删除，避免误删被其他记录引用的文件；可结合备份后续核实。此修复不会自动恢复旧版本脚本已经产生的半完成记录，需要先检查其原备份。

## 测试

```powershell
# 标准库离线测试；默认跳过 MySQL 测试
python -B -m unittest discover -s deploy/mysql/repair/tests -p test_localize_mall_product_images.py -v

# 已通过本地环境配置 MYSQL_PWD 后，启用隔离 MySQL 测试
$env:MALL_IMAGE_TEST_MYSQL_CONTAINER = 'deploy-mysql-1'
python -B -m unittest discover -s deploy/mysql/repair/tests -p test_localize_mall_product_images.py -v
```

MySQL 测试只在自动创建的 `mall_image_guard_test_<UUID>` 数据库内写测试数据，不上传图片、不操作商城业务表；结束后删除这个测试库。测试账号需要建库权限，未显式启用时不会访问 Docker。

覆盖正常更新与恢复、详情并发修改后整批回滚及重试、后续历史记录冲突、删除/跨租户/缺失记录、重复执行、大小写与尾空格、非事务表、SQL 执行失败、静默截断、完整脚本重跑和只读检查。

## 实现参考

使用 find-docs 核对事务规则；查询配额不足时改查 MySQL 官方资料，采用提交前行数校验和显式关闭重连，未增加 Python 数据库依赖或创建持久化存储过程。

- [MySQL ROW_COUNT 与 UPDATE 受影响行数](https://dev.mysql.com/doc/refman/8.4/en/information-functions.html#function_row-count)
- [MySQL 客户端选项](https://dev.mysql.com/doc/refman/8.4/en/mysql-command-options.html)
- [MySQL 可预处理的 SQL 语句](https://dev.mysql.com/doc/refman/8.4/en/sql-prepared-statements.html)
