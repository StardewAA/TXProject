package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Safe public capability: no database, provider, session or grant lookups. */
@Component
public class KeFuChatCapability {
    @Value("${mall.customer-service.scoped-enabled:false}") private boolean enabled;
    public KeFuChatModels.Capability get() {
        return new KeFuChatModels.Capability(enabled,"scoped-chat-v1","FAQ_HUMAN",false,
                enabled?"提供固定常见问题与范围授权人工接待；未接入大模型":"新店铺客服暂未开放，保留现有客服入口");
    }
}
