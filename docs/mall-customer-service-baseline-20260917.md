# 客服现有接口与下一阶段接续清单（2026-09-17）

历史阶段说明：本文保存客服盘点时的源码事实及当时待办，不是最新完成度。后续已完成简化注册、范围权限、消息/FAQ 与双端/后台接待页面；最新实现、默认关闭及真实验收边界见 [简化店铺客服闭环](./mall-customer-chat-20260917.md)，不要依据下方历史“未实现”段落重复开发。

本地源码初步核验，未查询远程、未拉取/覆盖组员成果、未操作真实客服数据。本次不认定全部交付盘点或客服专项验收完成。

## 已核对的现状

| 部位 | 本地源码事实 | 下一阶段需要补齐 |
| --- | --- | --- |
| promotion 会话模型 | KeFuConversationDO 继承 BaseDO，业务字段是 userId、末条消息、管理员置顶/删除/未读；未见 shopId、scope 或坐席分配字段 | 显式 PLATFORM/SHOP 范围、店铺归属及坐席关系；另核验租户拦截配置与实际 DDL，不能仅凭 DO 推断底层完全没有租户过滤 |
| 创建/复用会话 | KeFuConversationServiceImpl.getOrCreateConversation 以 userId 查询/创建 | 以服务端确认的 tenant + scope + shop + member 查找/幂等创建；旧会话兼容为平台会话，不猜测归属店铺 |
| 会员发送接口 | POST /promotion/kefu-message/send 接收 contentType/content；控制器覆盖 senderId/senderType 为当前会员 | 新范围契约和发送权限；资源卡片只提交引用 ID，由服务端鉴权并组装 |
| 消息读取/已读 | GET /promotion/kefu-message/list、PUT /promotion/kefu-message/update-read-status 存在；已读服务检查会员是会话 userId | 店铺坐席授权、成员类型及租户/店铺二次鉴权、游标补拉及多端已读 |
| 发送服务 | 会员消息 getOrCreateConversation(senderId)，保存后通知管理员和会员；现有逻辑没有按店铺选坐席 | 按授权接待范围派发，不能把所有店铺消息继续广播给普通管理员 |
| H5 消息内容 | kefu.js 走现有 HTTP 请求；页面把图片 URL、商品/订单对象 JSON 作为 content | 不能信任客户端完整订单/价格正文；商品、订单、售后引用需服务端核验并脱敏 |
| H5 实时连接 | 原租户使用现有 WebSocket；其他租户禁止旧实时通道；本轮增加换号关闭与晚消息丢弃 | 连接身份、店铺订阅、权限撤回与消息幂等协议，不沿用普通登录即能接待的假设 |
| 图片附件 | 复用普通 infra 上传，返回可访问地址 | 私有附件的归属、访问签名/授权、内容与大小限制、脱敏、过期清理和审计 |

关键源码位于：
- E:/txgy/txgycloud/txgy-module-mall/txgy-module-promotion-server/src/main/java/cn/iocoder/txgy/module/promotion/dal/dataobject/kefu/
- 同模块 service/kefu/KeFuConversationServiceImpl.java、KeFuMessageServiceImpl.java。
- 同模块 controller/app/kefu/AppKeFuMessageController.java、vo/message/AppKeFuMessageSendReqVO.java。
- E:/txgy/txgycloud/txgy-mall-uniapp-master/sheep/api/promotion/kefu.js。
- E:/txgy/txgycloud/txgy-mall-uniapp-master/pages/chat/index.vue、util/useWebSocket.js。

## 建议的下一开发批次（遵循原方案）

1. 完成 S0 剩余盘点：实际客服表结构/索引、租户处理、管理员权限与消息推送服务、PC/后台接待台、组员可复用交付及敏感附件存储。
2. 定义兼容的会话打开/读取/发送契约：PLATFORM 与 SHOP 显式区分；后端确认租户、会员、店铺及接待授权；旧平台消息继续可读。
3. 先开发 S1 人工店铺会话与坐席授权，测试跨租户、跨店铺、会员/管理员相同数字 ID 和权限撤回。企业经营授权不自动变成客服授权。
4. 再接 S2 商品/订单/售后卡片和双端入口；卡片内容由商品/交易服务读取并验证，敏感个人信息默认不公开。
5. 以同日新增范围调整为准：首版固定 FAQ＋人工转接，真实 AI 后置。S3 消息可靠性和私有附件仍须补齐；模型预算/数据发送范围后续另行确认，不作为首版固定问答前置条件。

涉及数据库扩展时先写增量迁移、兼容及回退方案，备份和隔离验证后按用户要求及时执行必要脚本。不把已有平台历史会话批量分配给某个店铺。

本轮没有新增会话/坐席表或启用店铺客服；只完成 H5 上传和旧连接的账号生命周期保护。公共租户/名单及客服测试账号范围尚未确认，不沿用旧回合的 316/admin 临时验证码授权。

后续批次补充：范围/坐席新表及服务端基础已实现，详见 [店铺客服范围与坐席基础](./mall-kefu-scope-20260917.md)。以上“本轮”是 H5 上传批次的历史范围，不代表最新源码仍无新表。新会话默认未开放、前端接待及消息闭环尚未完成。
