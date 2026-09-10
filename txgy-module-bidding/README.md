# txgy-module-bidding（招投标业务模块）

> 公共资源交易招投标业务，按国家《公共资源交易平台系统数据规范（V2.0）》和陕西省 V2.0 规范来对接。
> **想了解这个模块怎么开发，只看一份文档：`docs/招投标模块开发规范.md`**——全量 VO 怎么定位、自己建 VO 怎么建、前后端怎么配合、哪些坑不能踩，都在里面。

## 目录结构

```text
txgy-module-bidding
├── pom.xml                              # 聚合 POM（api + server）
├── README.md
├── docs/
│   └── 招投标模块开发规范.md             # 唯一规范，入口在这
├── VO数据规范/                          # 国家/省规范原文 + 整理版 VO 文件（原文不要动）
├── 千问中间脚本和文件存放位置/           # 生成脚本和中间产物
├── txgy-module-bidding-api/            # API 契约层
│   └── src/main/java/cn/iocoder/txgy/module/bidding/
│       ├── api/vo/                     # 全量 VO（170 个）：命名和字段的字典，页面别直接用
│       ├── api/enums/                  # 代码集常量（125 个）+ PublicLevel 公开级别注解
│       ├── api/util/                   # PublicLevelFilter：按公开级别裁字段
│       └── enums/ApiConstants.java     # 服务名、RPC 前缀
└── txgy-module-bidding-server/          # 业务服务（企业后台 / 监管 / 公共服务大厅）
    └── src/main/java/cn/iocoder/txgy/module/bidding/
        ├── controller/admin/           # REST 接口，出参用自建 VO
        ├── service/                    # 业务逻辑，自建 VO ↔ 全量 VO 在这里转换
        ├── dal/                        # DO + Mapper（表还没建，见规范里的冗余约定）
        ├── enums/                      # ErrorCodeConstants、DictTypeConstants
        └── framework/                  # security / rpc / web 配置
```

## 几句话说明

1. **全量 VO 是字典，不是页面 DTO**：`api/vo/` 里 170 个类是规范原样的映射（类名 = 规范数据集名、字段名照录英文名、统一 String），只在上报、对接第三方、跨系统交换时用。页面的 VO 自己建（规范第三章有约定）。
2. **公开范围跟着规范走**：规范里写了"社会公开/政务公开"的字段才标 `@PublicLevel`，没写的不标；对外出参过一遍 `PublicLevelFilter`，政务字段到不了大厅 / ES。
3. **前后端分离**：前端在 vben（apps/web-antd）的 `src/api/bidding/<domain>` + `src/views/bidding/<menu>`，后端 controller → service → dal；跨模块走 api 契约 + RPC。
4. **代码字典表**：`api/enums/` 下 125 个代码集常量类，配 `DictTypeConstants` 的 125 个 key；国家、陕西都有的概念以陕西 V2.0 为准。
5. **现在不建表**：数据层的冗余约定（超长字段、编码长度预留那些）先写在规范文档里，等设计评审过了再落地。

## 怎么启用

1. 根 `pom.xml` 已经挂了 `txgy-module-bidding` 模块；
2. `txgy-server/pom.xml` 里 `txgy-module-bidding-server` 的依赖默认注释着，按需打开；
3. 单独启动：`bidding-server`，端口 `48110`。
