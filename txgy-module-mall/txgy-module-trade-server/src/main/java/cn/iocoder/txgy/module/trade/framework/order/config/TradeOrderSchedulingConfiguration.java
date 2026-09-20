package cn.iocoder.txgy.module.trade.framework.order.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商城交易本地兜底调度不依赖 XXL-JOB 调度中心开关。
 * 默认关闭，仅在显式开启 local-auto-cancel-enabled 后注册。
 * 历史积分订单的跨服务补偿幂等及对账完成前，不得开启自动扫描。
 * 仅作用于 trade 服务自身的应用上下文，不启用或修改其他服务的调度中心。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "txgy.trade.order", name = "local-auto-cancel-enabled",
        havingValue = "true", matchIfMissing = false)
@EnableScheduling
public class TradeOrderSchedulingConfiguration {
}
