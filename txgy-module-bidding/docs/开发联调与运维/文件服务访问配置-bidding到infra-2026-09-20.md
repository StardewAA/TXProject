# 文件服务访问配置（bidding → infra）

> 适用：《文件接口收敛方案-后端实施篇-2026-09-20》落地后的部署要求。
> 背景：`GET /bidding/file/download?id=` 由 **bidding 后端代取文件字节**（前端不下发 url），
> 因此 bidding 服务必须能访问 infra 的文件下载端点。

## 一、配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `bidding.file.service-base-url` | `http://127.0.0.1:48088` | infra 文件服务基址；**容器部署必须覆盖** |

端点拼接规则（与 infra `FileController` 对齐）：

```
{base-url}/admin-api/infra/file/{configId}/get/{path}
```

## 二、各环境取值

| 环境 | 取值 | 说明 |
| --- | --- | --- |
| 本地开发（宿主机 JVM） | `http://127.0.0.1:48088` | 默认值即可（infra 容器映射端口） |
| 容器部署（同 compose 网络） | `http://infra:48080` | 服务名访问，端口为容器内端口 |
| 网关统一入口 | `http://gateway:48080` | 若要求所有出站走网关，可改此项 |

启动参数示例：

```bash
java -jar txgy-module-bidding-server.jar \
  --bidding.file.service-base-url=http://infra:48080
```

## 三、为什么不由前端直连

- infra 下载端点标注 `@PermitAll`：**URL 即凭据**，谁持有谁能下载；
- 《招投标模块开发规范》红线 8：业务侧**不存裸 url、不下发裸 url**；§5 三-5 明确 url 仅作"回显快照"；
- 故由 bidding 后端代取字节，**权限/病毒/审计收口在业务侧**，前端只认一个受控地址。

## 四、排障

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| `1-060-101-023 文件内容读取失败` | bidding 访问不到 infra（base-url 配错/网络不通） | 核对本配置；容器内用服务名而非 127.0.0.1 |
| `1-060-101-020 文件不存在或未登记` | 该 fileId 未走 `/bidding/file/upload` 登记（历史数据） | 用 `/bidding/file/register-by-url` 补登记（需该文件 url） |
| `1-060-101-021 病毒扫描未通过` | `virus_status=INFECTED` | 按安全流程处置，不绕过 |
