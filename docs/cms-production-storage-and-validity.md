# CMS 生产原件存储与法律效力核验

## 生产存储

使用 `prod` profile 时，CMS 默认采用 `infra` 模式。CMS 只在数据库保存 `infra:` URI，文件内容由基础设施模块的主文件存储负责。

上线前必须在管理后台“基础设施 → 文件管理 → 文件配置”完成以下设置：

1. 新建 S3 存储配置；MinIO、阿里云 OSS 以及其他 S3 兼容服务均使用该类型。
2. 配置 endpoint、bucket、access key、secret key、region 和访问域名。
3. 将该配置设置为“主配置”。
4. 上传一个测试 PDF，并确认能够生成预签名地址和下载。
5. 使用生产账号提交一份 CMS 文档，确认 `cms_document_review.original_file_path` 以 `infra:` 开头。

启动参数示例：

```text
--spring.profiles.active=prod
CMS_STORAGE_MODE=infra
CMS_SNAPSHOT_BROWSER=/usr/bin/chromium
```

不要在生产容器内使用默认的 `./data/cms-knowledge`，容器重建会导致文件丢失。

## 对象存储备份

应用代码不能代替对象存储备份。生产环境至少配置：

- bucket 版本控制；
- 生命周期规则，禁止误删后立即永久清除；
- 每日增量复制到第二 bucket 或第二存储区域；
- 每月执行一次随机文件恢复演练；
- 备份监控告警和失败重试；
- MinIO 使用纠删码时仍需异地备份，纠删码不等于备份。

## 法律效力核验

执行 `sql/cms_law_validity_check.sql` 创建待办表。`prod` profile 默认每天 03:20 核验一次，可通过以下变量调整：

```text
CMS_VALIDITY_CHECK_ENABLED=true
CMS_VALIDITY_CHECK_CRON=0 20 3 * * ?
CMS_VALIDITY_CHECK_BATCH_SIZE=100
CMS_VALIDITY_CHECK_TIMEOUT_SECONDS=12
```

核验规则：

- 国家法律法规数据库详情链接：读取官方状态码；状态不再是代码 `3` 时生成“远端状态待复核”。
- 其他 HTTPS 政府网站：只检查来源能否访问，不自动推断法律效力。
- 网络失败或官网迁移：生成“来源不可访问”待办。
- 定时任务绝不自动归档。管理员在 CMS 发布管理的“效力核验待办”中确认后，才会归档并清除 ES 切片。

归档保留 MySQL 历史和 MinIO/OSS 原件。恢复发布时会重新写入关键词与向量索引。
