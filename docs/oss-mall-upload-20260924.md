# 商城 OSS 图片存储第一阶段实施记录（2026-09-24）

## 本批实现

- 核对到项目已有统一 `infra/file` 文件存储抽象，PC 商城上传 API 已走 `/infra/file/upload`；移动 H5 上传也通过同一后端基础设施服务，不需要让前端直接持有 OSS 密钥。
- 复用现有 S3 兼容文件客户端接入阿里云 OSS，没有新增 SDK 或另造上传协议。
- 新增 S3 配置凭证引用解析：`accessKey=env:OSS_ACCESS_KEY`、`accessSecret=env:OSS_ACCESS_SECRET` 时从 infra 进程环境变量读取；未配置或引用格式错误会安全失败，错误信息不包含密钥值。旧的直接字面值配置继续兼容。
- OSS 环境变量只注入 `infra` 服务（单体部署模式注入 `server`），不注入商城前端/移动端，也不写入镜像。
- 更新 `.env.example` 和 Docker 运维说明；要求新建独立 OSS 文件存储配置后再切主配置，不覆盖已有数据库文件配置，避免历史文件因 configId 映射改变而无法读取。
- 加入凭证解析测试以及公共 HTTPS OSS URL 无端口测试。

## 当前 OSS 配置参数

| 配置 | 值 |
| --- | --- |
| Bucket | `java-ai-comency` |
| Region | `cn-beijing` |
| Endpoint | `oss-cn-beijing.aliyuncs.com` |
| Domain | 未提供自定义域名；留空后按 Bucket HTTPS 默认域名拼接 |
| 公共读取 | Bucket 当前为公共读；仅用于确认为公开的商品/运营图片 |
| CDN | 暂无 |

AccessKey ID/Secret 不写入此记录。按用户明确要求，当前开发机仅在 Git 忽略的 `deploy/.env` 保存了聊天中提供的凭证，并由 Compose 只注入 infra；没有进入源码、镜像层、前端配置或本记录。由于凭证已在聊天中暴露，仍建议在生产使用前轮换，并改用 RAM 最小权限凭证。OSS 的公开读取设置不限制持有 AccessKey 的写/删权限，不能据此判断凭证权限安全。

## 验证结果

- `S3CredentialResolverTest`：4 项通过，覆盖环境引用解析、缺失变量安全失败、旧值兼容和非法引用拒绝。
- 定向单元测试共 15 项、7 项跳过、0 失败；凭证解析与 HTTPS OSS URL 单测通过。
- TUN 打开时主机 DNS 曾返回 `198.18.x.x` 并导致 OSS TLS 失败；用户关闭 TUN、保留系统代理后，Windows 宿主机直连可达。宿主机上的 S3 Java smoke test 成功完成签名上传、匿名 HTTPS 读取和删除（1 项通过）。显式通过系统代理访问 OSS 仍在 TLS 阶段失败，但经代理访问普通阿里云站点成功。
- 运行中的 Docker infra 容器当前直连 OSS 以及显式经 `host.docker.internal` 系统代理访问 OSS 均 TLS 失败；infra 不自动继承 Windows 系统代理。实际商城上传发生在 infra 容器，因此容器内应用链路尚未验收。
- `docker compose ... config --quiet`：主 Compose、`docker-compose.hostjar.yml` 组合及 standalone 均通过；主 Compose 有既存 `REPORT_DB_PASSWORD` 未配置提示，与本批 OSS 配置无关。
- infra 镜像已重建并仅重启 infra 容器；补齐运行镜像的 `curl` 健康检查依赖后，容器报告 `healthy`，且 OSS 环境变量只注入 infra。
- 数据库新增独立 OSS 配置 `infra_file_config.id=36`（storage=20，凭证字段仅为 `env:` 引用）；因 Docker infra 的 OSS 网络路径未通，已将 ID 4 恢复为 master，ID 36 保留为非 master，避免影响当前上传。两条配置均保留，历史文件未删除或迁移；infra 重启后状态为 healthy。

## 下一步

1. 让 Docker infra 到 OSS 具备可用网络路径：直连 TLS，或为 infra 配置可访问 OSS 的显式代理（当前系统代理的 OSS TLS 测试失败，需先调整 Verge 域名路由并验证）。系统代理可继续服务其他应用。
2. infra 容器内 OSS 连通后，重测配置 ID 36，再切换为 master；随后从 PC、H5 分别上传商品/头像测试图，检查数据库 URL、对象可读和跨租户引用。
3. 生产部署前轮换聊天中暴露的 AccessKey，并创建 RAM 最小权限凭证；凭证只放在部署密钥管理器或未跟踪环境文件中，不提交 `.env`。
4. 统计遗留商品、店铺、会员头像等图片引用；制定对象迁移和数据库 URL/资源键更新方案。切换 master 只改变新上传目标，不会自动迁移历史对象。
