package cn.iocoder.txgy.module.product.framework.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 旧 member 端供应经营接口兼容开关。
 *
 * <p>第 21 节后，供应经营必须使用 system_user 后台。该开关默认关闭，仅用于有明确
 * 回退窗口、完成风险评估的短期兼容；它不会影响 member 端采购询价、接受报价或沟通。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.legacy-member-sales")
public class LegacyMemberSalesProperties {

    /** 是否临时允许旧 member 端供应经营写接口。默认必须为 false。 */
    private boolean enabled = false;
}
