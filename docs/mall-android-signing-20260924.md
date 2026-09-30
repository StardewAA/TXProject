# 商城 Android 教学演示版：构建、签名和安装

本次交付采用 **Android 原生 WebView 容器 + 现有移动商城 H5**。APK 是实际编译、签名的 Android 应用，不是网页文件改扩展名；商城业务页面和接口继续由部署电脑提供，所以包体很小，不能离线采购。

## 包身份和适用范围

| 项目 | 值 |
| --- | --- |
| 包名 / applicationId | `cn.txgy.mall`（需求方确认） |
| 显示名称 | 防腐保温商城 |
| 当前版本 | 1.0.2 / versionCode 102 |
| 系统声明 | Android 6.0（API 23）及以上，target API 35；手机 WebView 需保持更新 |
| 签名 | 项目自有 RSA-2048 / SHA256withRSA，别名 `txgymall`，有效期 30 年 |
| 权限 | INTERNET、ACCESS_NETWORK_STATE；没有通讯录、短信、定位、相机或广泛存储读取权限 |
| 使用范围 | 非商用课堂演示，不是应用商店正式发布版；不提供 iOS 安装包 |

签名证书 SHA-256：`71:E2:CE:9B:BF:87:0A:4D:88:2F:CB:76:50:B5:81:6F:26:59:92:CA:65:81:3A:EC:FD:2D:28:F5:51:C8:CE:F0`。证书指纹不同于每次 APK 文件的 SHA-256，勿混淆。

2026-09-24 已通过 HBuilderX 5.24 的 `cli user info` 确认登录；实际执行发行时，工具提示当前“国际区”账号暂不支持中国大陆用户发行。未提交收费任务。为不依赖云打包账号，改用官方 Android SDK 在本机生成 APK；**老师下载安装不需要 HBuilderX、DCloud 账号或签名证书**。

## 老师拉取后怎么用

1. 按 [独立部署说明](../deploy/mall/README.md) 启动商城。OSS 图片与整套后端交付仍有独立未验收项，见 [VALIDATION](../deploy/mall/VALIDATION.md)，本 APK 不会补齐未启动的后端。
2. 如需手机访问，在 `deploy/mall/.env` 设置 `BIND_ADDRESS=0.0.0.0`，把相关入口的 `localhost` 换成电脑的局域网 IP；`MALL_H5_ORIGIN` 可填 `http://电脑IP:3000`。仅按需放行可信局域网的 PC/H5 端口，不开放数据库、Redis、Nacos。
3. 管理后台“商城首页 → 进入 PC 商城”；PC 首页“下载移动端”进入 `/download/mobile`。
4. 下载页核对 PC 地址（默认端口 15175）和 H5 地址（默认 3000）。不要给手机使用 `localhost`、`127.0.0.1`、Docker 服务名或虚拟网卡地址。页面不会为 localhost 生成可误用的二维码。
5. 手机与电脑同网，用手机相机或浏览器扫码打开下载页，下载 APK 并安装。微信内受限时选择“在浏览器中打开”；系统要求时只临时允许该浏览器安装应用。
6. 回到手机浏览器下载页，点击“第 2 步：安装后返回本页，点此进入商城”。首次连接不再弹确认框，也无需手输 H5 地址；以后从桌面打开会直接重连上次商城。安装器自带的“打开”按钮不能把下载页的地址传给新装 App，若直接点它仍会看到手动连接页。
7. 第一次以商城会员身份登录。后台管理员登录不会传递到 App。电脑更换 IP 后，用手机浏览器重新打开 PC 下载页并点击“打开并连接 App”；无法连接时也可在 App 错误页选择“切换商城地址”。更换连接入口会清除容器内原有网站会话并要求重新登录。商城首页不再显示原生顶部白栏和常驻“切换商城”按钮。

安装包本身不硬编码开发机 IP、用户密码、Token 或 OSS 密钥。相同 APK 可连接另一台教师电脑的商城，无需重打包；因此离线安装包不可能从浏览器下载链接自动得知各台教师电脑的 IP，首次仍需回下载页点一次“进入商城”。电脑必须开机且商城服务启动；学校网络的客户端隔离、防火墙或手机流量网络可能阻断访问，需要先在手机浏览器测试 H5。

HTTP 只用于可信私有局域网教学演示；真实敏感数据或公网部署使用有效 HTTPS。本方案不关闭 TLS 验证，不修改现有端口/域名。IPv6 入口暂未纳入本版，使用局域网 IPv4。

## 下载文件和版本管理

- APK：以 `txgy-mall-web/public/downloads/android-release.json` 的 `apkUrl` 和 `sha256` 为准；升级版采用新文件名，不覆盖历史包。
- 它使用手机系统 WebView，因此不内置浏览器引擎或商城页面资源。
- 机器可读发布记录：`txgy-mall-web/public/downloads/android-release.json`，含大小、校验值、版本、最低系统和权限说明。
- PC 构建会自动携带这两个文件。下载页验证记录结构和安装包存在/长度；Nginx 对缺失 APK 返回 404，不用 `index.html` 冒充安装包。文件名包含内容摘要，元数据不缓存。
- 课堂编排显式设置 `VITE_MALL_DEMO_MODE=true`，允许现有局域网 HTTP 下载；默认生产配置不放宽公网 HTTP。外部 APK 发行可显式填写 `VITE_ANDROID_*` 覆盖配置，未填则使用仓库随附包。

## 本机构建与证书保管

Android 源码在 `txgy-mall-android/app/src/main/`，不依赖 Gradle 或第三方原生 SDK。首次准备证书：

```powershell
powershell -ExecutionPolicy Bypass -File .\txgy-mall-uniapp-master\scripts\prepare-android-signing.ps1
```

默认私有目录 `%LOCALAPPDATA%\TxgyMall\android-signing`：

- `cn.txgy.mall.keystore`：证书和私钥，不能提交 Git。
- `cn.txgy.mall.password.dpapi`：当前 Windows 用户 DPAPI 加密的密码。
- `certificate-info.json`：公开指纹与别名，不含密码。

脚本复用已有证书；证书存在但密码丢失时停止，不能偷偷更换签名。若仅保存密码而证书生成中断，允许用已保存密码续建。目录权限限当前用户及 SYSTEM。老师安装无需这几个文件。

本机证书位于 `C:\Users\ltr33\AppData\Local\TxgyMall\android-signing`。DPAPI 文件不能直接拿到另一台电脑解密；迁机前将 keystore 与密码通过个人密码管理器分别备份。`-CopyPassword` 可按需复制密码，使用后清空剪贴板，勿发聊天或写进仓库。

维护者准备 Windows JDK 17+、Android SDK platform 35 和 build-tools 35.0.0 后运行：

```powershell
.\txgy-mall-android\build.ps1 -SdkRoot 'E:\txgy\tools\android-sdk' -Publish
```

脚本依次执行地址校验测试、aapt2 资源编译、javac、D8、zipalign、apksigner 签名/验证，再发布 APK 与元数据。密码仅以临时进程环境变量传给签名工具，不作为命令行实参打印。无 `-Publish` 时仅在被 Git 忽略的 `build/` 生成结果。流程可复现；ZIP 时间戳会影响每次文件哈希，不保证逐字节相同。

后续修改应提升 manifest 的 `versionCode`，复用证书，重新生成元数据并构建 PC。已安装旧包的手机只有相同签名才能覆盖升级；不要把密码或证书加入下载目录。

## 容器安全和业务边界

- 连接前请求 H5 的 `static/mall-app.json`，核对应用标识、限制响应大小并设置超时；未更新 H5 或地址错误时显示可重试提示，不停留空白页。连接过的地址启动时先显示加载状态，不闪出网址表单。
- 连接 URL 仅允许合法 HTTP(S) 入口，拒绝凭据、查询参数、片段、回环/链路本地地址、异常路径；深链只携带商城入口，不携带租户授权或登录令牌。
- 不开放 JavaScript 原生桥；WebView 禁止直接读文件/内容协议、禁止混合内容；HTTPS 证书错误取消请求。单张跨域图片的证书错误不再销毁商城页面和个人中心；主页面错误显示重试/切换地址，不直接跳回输入框。不同来源的导航需要用户确认后交给外部浏览器，电话链接只打开拨号界面。
- 图片/文件选择走 Android 系统选择器，App 只能读取用户选中的 `content://` 资源，不申请整个相册读取。用户取消选择会正确回调。
- 原有 H5 的登录、商品、购物车、地址、订单、询价、客服仍受服务端授权与租户校验。壳本身不新增自动登录、真实支付、原生定位、推送或相机扫码。
- 地图、短信、AI、真实支付和 OSS 上传仍依赖既有服务配置；未验证的外部能力不能因 APK 能打开首页就算验收通过。

## 验证记录与剩余项

已验证：API 35（Android 15）模拟器安装、启动、深链确认连接、现有商城首页加载、相同签名覆盖安装；系统图片选择器回传图片并读取实际字节（166,788 字节）；APK v1/v2/v3 签名与 zipalign 通过，权限只有上述两个。PC 下载页通过桌面/390px 移动视口检查、二维码/深链地址校验、实际 APK 下载大小/SHA-256 和缺失文件 404 检查。PC 全部 67 个测试文件、696 个测试通过，类型检查和生产构建通过；H5 生产构建通过。

**仍需真机验收**：目前没有连接实体 Android 手机，模拟器不等同真机。实际手机/校园 Wi-Fi 下载、品牌安装提示、最低 Android 版本、登录后业务闭环、地图/支付/客服等外部能力要逐项抽验。OSS/TLS 及新机器全栈部署沿用独立交接清单，未在本记录宣称完成。后台已有其他模块的类型错误仍需另行处理。

2026-09-24 追加验证：1.0.2 / versionCode 102 已在 API 35 模拟器覆盖安装，截图确认只保留 H5 自身导航，顶部重复白栏和常驻切换入口消失。当前 APK 文件名为 `txgy-mall-1.0.2-8df324973c31.apk`，SHA-256 为 `8df324973c31d6ac24dd7aa42f6b1eef3409cc93e51d834c31a6371abe831055`；下载页发布记录和本地下载响应一致。H5 登录校验提示完成 390px 视口几何回归测试。实体手机仍需复验。

参考：[Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)、[apksigner](https://developer.android.com/tools/apksigner)、[DCloud 免费生成证书说明](https://ask.dcloud.net.cn/article/35777)。
