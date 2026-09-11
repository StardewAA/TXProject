package cn.iocoder.txgy.module.trade.framework.marketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商城营销开关。
 *
 * <p>防腐保温类商品以企业采购为主，默认只启用普通折扣、满额优惠、优惠券和
 * 普通订单的积分抵扣。秒杀、砍价、拼团和积分商城仍保留原有 API，
 * 但在简单模式下不会参与订单价格计算。需要恢复完整营销能力时，
 * 可在配置中心将 {@code txgy.trade.marketing.simple-mode} 设为
 * {@code false}。</p>
 */
@Component
@ConfigurationProperties(prefix = "txgy.trade.marketing")
@Data
public class TradeMarketingProperties {

    /**
     * 是否使用企业采购的简单营销模式，默认开启。
     */
    private boolean simpleMode = true;

    /**
     * 普通商品折扣（含会员等级折扣）是否启用。
     */
    private boolean discountEnabled = true;

    /**
     * 满额优惠是否启用。
     */
    private boolean rewardEnabled = true;

    /**
     * 普通优惠券是否启用。简单模式保留固定金额/满额优惠券；
     * 秒杀、砍价、拼团等特殊订单仍然会被单独拦截。
     */
    private boolean couponEnabled = true;

    /**
     * 普通订单积分抵扣是否启用。积分商城活动不受此开关影响，
     * 仍会在简单模式下被禁止。
     */
    private boolean pointDeductEnabled = true;

    /**
     * 订单赠送积分是否启用。它不改变订单价格，默认保留基础能力。
     */
    private boolean pointGiveEnabled = true;

    /**
     * 分销佣金是否启用。企业采购默认不启用分销，避免生成无意义的佣金记录。
     */
    private boolean brokerageEnabled = false;

}
