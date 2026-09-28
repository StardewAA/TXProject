# 商城独立 Docker 部署包（自动配置入口）

目标是在新机器上克隆本仓库后，仅用 Docker 构建和启动商城，首次启动 MySQL 自动导入本目录 `sql/` 中的表结构和演示数据。无需寻找历史补丁 SQL、复制本机 JAR、导入 Nacos 配置或安装 Java/Node。

**已完成本机隔离新库的源码构建、全服务启动和基础订单接口验收；尚不等于另一台物理机器及 Android 真机全部通过。**35 张演示图片已由维护方发布；现有联调环境的真实 OSS 上传、补传与商品图片页面验收见 [OSS 联网交接记录](../../docs/OSS联网部分交接记录-20260925.md)。本轮结果及可重复命令见 [本地部署收尾记录](../../docs/商城本地部署收尾记录-20260925.md)。启动脚本默认再次核验公开图片，不把页面能打开当作全部可用。

**新机器只使用本目录这一套编排。**它从源码构建所有商城依赖服务，不叠加 `deploy/docker-compose.*rollout.yml`，不依赖 `file-acl-*` 等本机历史镜像，也不使用 `deploy/patch-image/build-all.sh` 作为新机入口。已有联调栈保持原样，不能混用两套数据卷和 SQL。

已通过项目、当前阻塞及后续验收步骤见 [VALIDATION.md](VALIDATION.md)。

该编排独立于 `deploy/docker-compose.yml` 的全业务开发环境，包含 MySQL、Redis、Nacos、网关、system/infra/member/pay/product/trade/promotion/statistics/WMS/AI 服务，以及 PC 商城、移动 H5、管理后台，共 17 个常驻服务、14 个源码构建镜像。商城商品库存直接对接同组 WMS，未启用 WMS 预占；不需要 ERP。AI 用于商城客服，不包含历史 AI 管理库与会话导入。

## 首次启动

准备 Git、Docker Engine / Docker Desktop（Linux 容器）和支持构建 secrets 的 Docker Compose v2。建议 Docker 分配至少 12 GB 内存（16 GB 更充裕），磁盘预留 25 GB。首次构建需要联网下载基础镜像、Maven 和前端依赖，耗时取决于网络；启动脚本逐个构建以降低内存峰值。

Windows，在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\mall\start.ps1
```

Linux / macOS，在仓库根目录运行：

```sh
sh deploy/mall/start.sh
```

脚本会从 `.env.example` 生成本目录独立的 `.env`，已有配置不会覆盖。之后依次校验所附凭证配置（不显示值）、核验公开 OSS 图片、构建、启动、等待服务健康，并执行登录和商品目录检查。检查未通过会返回错误。启动检查本身不会上传 OSS、切换存储 master 或覆盖原有数据库；启动后的真实业务操作可按既定权限写入公开 OSS。

也可直接在 `deploy/mall` 目录执行：

```sh
docker compose run --rm --no-deps preflight
docker compose up -d --build --wait --wait-timeout 900
docker compose run --rm --no-deps verify-assets
docker compose run --rm --no-deps verify
```

`verify` 是启动冒烟检查（登录、目录、同源 API 和 OSS 图片），不是下单、支付、上传或 Android 真机的完整业务验收。

## 高德、DeepSeek、OSS：自动加载，无需老师手填

按照仓库所有者的明确要求，课堂交付随附以下真实配置。它们必须随代码一起提交给老师；**只提交 compose 而漏掉配置文件将被启动预检明确拒绝**。

| 文件 | 自动使用位置 | 用途 |
| --- | --- | --- |
| `txgy-mall-web/.env.local` | PC 构建时的 `amap_env` secret | 高德 Web Key 与安全码 |
| `txgy-mall-uniapp-master/.env.local` | H5 构建时的 `amap_env` secret | 高德 Web Key 与安全码 |
| `deploy/oss-keys.env` | 仅 `infra` 服务的 `env_file` | 公开文件提升、读取与补传 |
| `deploy/deepseek-keys.env` | 仅 `ai` 服务的 `env_file` | 商城 AI 客服调用 DeepSeek |

路径均相对仓库；Compose 自动读取。不要复制原开发环境的 `deploy/.env`，其中数据库/Nacos 配置与本包无关。高德/AI 开关默认打开，AI 服务随包构建并启动；商城客服直接使用 `DEEPSEEK_API_KEY`，不需要老师导入旧 AI 整库或在后台再填一次。

这是**所有者授权共享 Key 的课堂交付**，不是“老师没有拿到 Key 却能用”的机制。拿到仓库的人可以读取这些凭证；Key 被撤销、余额不足、RAM 权限或高德域名限制不匹配时，对应云能力会失效。不要公开仓库。删掉 Git 文件不等于撤销已分发的 Key，应在云平台撤销或轮换。地图是浏览器 SDK，Web Key 会进入前端构建产物；OSS/DeepSeek 凭证不注入前端。

需要换账号时，在本目录 `.env` 调整四个 `MALL_*_ENV_FILE` 路径，文件变量名保持不变。更新地图 Key 后同时改 `AMAP_BUILD_REV` 再运行启动脚本，避免构建缓存复用旧前端。OSS/DeepSeek 文件更新后重建对应容器使配置生效；不要运行会输出完整凭证的 `docker compose config`，只用 `docker compose config --quiet`。

只检查配置、不构建或启动商城：

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\mall\start.ps1 -CheckConfigOnly
```

Linux/macOS：`sh deploy/mall/start.sh --check-config-only`。此检查仅创建临时检查容器、读取配置，不调用外部 API、不验证余额/权限/域名，也不等于地图、AI 对话或 OSS 上传验收。实现依据 Docker 的 [Compose 环境变量规则](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)与[构建 secrets 支持](https://docs.docker.com/compose/how-tos/use-secrets/)。

### 隔离验证与 OSS 暂时不可达

本机已有商城服务时，**不要直接使用默认项目名和默认端口启动第二套**。在本目录自己的 `.env` 中更换 `COMPOSE_PROJECT_NAME`（例如 `txgy-mall-check`）和四个 `MALL_*_PORT`，同步修改对应 `MALL_*_ORIGIN`。这会使用独立数据卷，但仍需足够内存；未经所有者授权，不要为测试停止原有业务服务。获准停机时，先记录原容器身份和恢复方式，验收后恢复，不使用已改变的编排文件重建原栈。

**资源不足时连“只构建、不启动第二套”也不安全。**本轮曾在 9.7 GiB Docker/WSL 实例已有微服务运行时安装后台依赖，触发主机 OOM 并导致 MySQL 自动重启。前端 Node 堆参数不等于容器总内存上限；请在空闲或独立构建环境执行，不能只依据空闲 CPU 判断。

只排查本地部署且明确不验 OSS 时，可以执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\mall\start.ps1 -SkipOssCheck
```

```sh
sh deploy/mall/start.sh --skip-oss-check
```

该模式只运行 `verify-local`，输出 `local-only-passed`、`ossVerified=false`，不能作为云端通过的证据。完整交付前仍须执行 `verify-assets` 和默认 `verify`。它不改存储配置、不关闭 TLS 校验，也不伪造通过结果。

## 访问和账号

| 入口 | 默认地址 | 演示账号 | 密码 |
| --- | --- | --- | --- |
| PC 商城 | http://localhost:15175 | buyer01 | admin123 |
| 移动 H5 | http://localhost:3000 | buyer01 | admin123 |
| 管理后台 | http://localhost:5173 | admin（租户 1） | admin123 |
| 科盾采购账号 | PC / H5 | buyer2009 | admin123 |
| 科盾租户管理员 | 管理后台，租户 2009 | tenant2009 | admin123 |
| 科盾旗舰店经营账号 | 管理后台，租户 2009 | shop940501 | admin123 |

所有演示账号与所属租户、店铺见 [demo-accounts.json](demo-accounts.json)。采购会员与店铺后台经营账号分开，权限按租户与店铺授权。租户 3000 保留历史结构，但不开放登录。

演示目录包含 26 个租户、92 家店铺、898 个商品、903 个 SKU；业务图片引用 OSS HTTPS 地址，WMS 同步预置对应物料、成品仓和演示库存。这里使用新建演示账号，本包 SQL 未导入开发数据库中的实际用户、订单、地址、聊天或令牌；第三方演示凭证按上节单独提供，不混入本包 SQL。

## OSS 图片：维护方发布一次，部署方直接读取

`public-assets.json` 记录本包用到的公开商品图片、稳定对象键、SHA-256、大小及商品引用。SQL 同时写入 `infra_file.config_id/path`，绝对 URL 不是唯一定位信息；`OSS_PUBLIC_ORIGIN` 决定本次空库初始化的访问域名。目标 Bucket 默认是方案中提供的 `java-ai-comency`，对象位于独立前缀 `mall-demo/v1/product-images/`。

维护方先准备并发布这批**公开演示商品图片**。准备命令只复制清单内文件到 Git 忽略的暂存目录，不上传、不删除 OSS 对象：

```sh
python deploy/mall/tools/public_assets.py stage
```

确认清单和 Bucket 后，由有授权的维护方使用已安全配置凭证的 ossutil 或 OSS 控制台上传该目录，保留完整对象键。例如在仓库根目录执行：

```sh
ossutil cp deploy/mall/.assets/mall-demo/v1/product-images/ oss://java-ai-comency/mall-demo/v1/product-images/ -r
```

首次发布使用未占用前缀；若已有同名对象，先核对清单，不直接覆盖。历史环境的图片迁移另行执行，需保存旧 URL 与对象键映射和回退记录，本脚本不会批量改动历史数据库。

上传后在 `deploy/mall` 中执行 `docker compose run --rm --no-deps verify-assets`。它逐个通过 HTTPS 下载，检查图片类型、大小和 SHA-256；全部通过后才能对外交付。图片已公开发布后，克隆者只需可访问 OSS，无需图片压缩包或 AccessKey。

新增公开图片写入 OSS 需要 infra 的 `OSS_ACCESS_KEY` / `OSS_ACCESS_SECRET`，本包已从所有者授权的 `deploy/oss-keys.env` 自动注入，老师无需再填。不将密钥传给 PC/H5/admin。凭证缺失会在启动预检报错；云权限不足或网络失败时图片提升可能回退数据库，不能只看到网关图片 200 就宣称已进入 OSS。

存储主配置保持 4、公开 OSS 配置保持 36 非主配置。上传先为私有待绑定；业务服务确认公开后才转存 OSS；失败可追踪并补传。私有订单凭证、询价附件等不进入公共桶。联调环境已经验收的记录不能替代新部署的凭证、网络和页面复验。

本包不会调整现有网址、端口或域名体系；Android APK 及下载能力按第 23.9 节单独验收，见下方说明。Docker/H5 构建通过仍不代表整套业务和真机已经验收。

## PC 下载 Android 安装包

后台“商城首页 → 进入 PC 商城”，PC 首页“下载移动端”进入 `/download/mobile`。仓库随附实际签名 APK 与 `android-release.json`，PC 构建会一起发布；不需要老师安装 HBuilderX、申请 DCloud 账号或提供签名证书。

包名 `cn.txgy.mall`，版本 1.0.2，Android 原生 WebView 容器复用当前 H5，安装后选择商城地址，换电脑无需重打包。手机同网访问需要设置下述 `BIND_ADDRESS`，并在下载页核对 PC/H5 局域网 IP 和端口。可设置 `MALL_H5_ORIGIN`；为空时下载页建议当前主机名与 `MALL_H5_PORT`。不要让手机访问 localhost。App 不常驻“切换商城”栏；地址变化时，从手机浏览器重新打开下载页并点“打开并连接 App”，连接失败页也可切换地址。

扫码打开下载页 → 下载并安装 → 回到浏览器点“安装完成，打开 App 并连接此商城” → 在 App 确认地址 → 使用商城会员登录。HTTP 仅适用于可信局域网课程演示；公网使用 HTTPS。本编排显式开启 PC 下载页的教学模式，保持现有端口不变。

目前已完成 Android 15 模拟器安装、启动、连接、覆盖安装及图片选择验证；实体手机和新机器的完整业务流程仍需验收。完整签名、构建、SHA-256 与操作说明见 [Android 交付记录](../../docs/mall-android-signing-20260924.md)。

## SQL 和文件存储

`sql/001-schema.sql` 是商城结构，`002-demo.sql` 是演示数据，`004-public-origin.sh` 配置图片访问域名，`005-ready.sql` 仅保留旧基础结构标记；`006`～`010` 是文件权限、上传凭证、公开引用重算和 OSS 补传字段；**最后 `011-ready.sql` 写入 `2026-09-25-file-acl` 就绪标记**。健康检查同时检查关键表、补传字段及最终标记，避免不完整数据库启动应用。

Docker 按文件名顺序导入，只在空数据卷首次创建时执行；普通重启不会清空数据。中途失败不会自动补跑剩余 SQL，先看日志，不要盲目删除卷。

如果手动导入，先创建 UTF-8 空数据库 `ruoyi-vue-pro`，依次执行 **001 → 002 → 004（自定义域名时执行 shell 脚本的等效 UPDATE）→ 005 → 006 → 007 → 008 → 009 → 010 → 011**。默认域名无需手动执行 004。不能只导入 001/002/005，否则文件权限和补传字段缺失。SQL 专用于空库，不能当成老环境的重复升级脚本；不要再混入 `deploy/mysql/init`、`repair` 或旧整库备份。

已有旧数据卷不会自动升级：先备份、确认实际缺失的迁移后再执行；不能用补写最终标记来绕过检查。初始化后应有 35 条演示图片记录，公开且位于配置 36；配置 4 为唯一 master，平台账户三张表均存在。

手动导入上述 SQL 默认使用清单里的 OSS 域名；自建 Bucket 时必须同步调整文件配置与 URL，并以 `infra_file.path` 中的稳定对象键重建图片地址。不要只替换配置域名就假定历史 URL 已全部迁移。

## 从其他电脑或手机访问

先复制 `.env.example` 为 `.env`，设置 `BIND_ADDRESS=0.0.0.0`，并把 PC、管理端、API 的 `MALL_*_ORIGIN` 中 `localhost` 改成部署机器的局域网 IP，端口与对应 `MALL_*_PORT` 保持一致；手机入口可补充 `MALL_H5_ORIGIN=http://电脑IP:3000`。然后执行启动脚本。浏览器接口走同源 Nginx，容器之间通过服务名通信。只放行可信网络必要端口，不要将附带演示口令的系统直接暴露公网。

已经初始化过数据库再修改域名时，还需按文件配置及稳定对象键迁移存量 URL，并重新构建相关前端；环境变量不会回写已有文件 URL。数据库、Redis 和 Nacos 默认不映射主机端口。

## 功能边界

- 商品浏览、分类、店铺、账号登录、购物车、订单、后台经营使用同一套商城服务。
- 新库须保留空的 `trade_erp_inventory_request` 历史审计表，订单详情仍读取它；这不需要启动 ERP，也不导入历史订单。不能因停用 ERP 履约就删除该表。
- 演示 WMS 单位直接使用 `WmsUnitCodeDictionary` 的代码，例如桶为 `BUCKET`、罐为 `CAN`、个为 `PC`；不能随意替换或跳过结算单位校验。
- 默认只配置模拟支付和商城余额支付；钱包充值只配模拟支付，不允许用余额给自己充值。模拟支付不会产生真实扣款。
- 高德地图与 DeepSeek 商城客服默认启用并自动加载所附凭证；它们仍依赖云服务可达、Key 有效及账户权限。地址仍可手填，AI 不可用时不能宣称智能回复成功。
- 短信及真实微信/支付宝支付未自动配置，不要把模拟支付当作真实支付开通。
- 此包为独立本地/联调环境，默认只监听本机，附带公开演示口令。对外正式发布前需更换演示账号与部署密钥，并配置真实支付渠道。

## 重启、检查

在本目录运行：

```sh
docker compose ps
docker compose logs --tail 100 product
docker compose run --rm --no-deps verify
docker compose stop
docker compose up -d --wait --wait-timeout 900
```

不要为了重启删除数据卷。数据库初始化失败时先查看 `docker compose logs mysql`；后端首次构建失败先查看构建日志中的第一条 Maven 错误。后续代码升级需重新构建受影响服务；本包的空库初始化 SQL 不自动升级已使用的数据库。

编排依据 Docker 的[服务就绪依赖机制](https://docs.docker.com/compose/how-tos/startup-order/)；SQL 自动导入遵循 [MySQL 官方镜像的初始化规则](https://hub.docker.com/_/mysql)。
