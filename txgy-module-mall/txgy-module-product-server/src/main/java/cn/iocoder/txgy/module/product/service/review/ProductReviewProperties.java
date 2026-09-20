package cn.iocoder.txgy.module.product.service.review;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 存量切换必须显式配置，不以 null 审核状态伪造批准。 */
@Data
@Component
@ConfigurationProperties(prefix = "txgy.product.review")
public class ProductReviewProperties {
    private boolean adminWriteEnabled = true;
    /** 已核实经营账号的试点店铺；部署配置显式指定，源码不硬编码业务店铺。 */
    private java.util.List<Long> adminWriteLockedShopIds = new java.util.ArrayList<>();
    private boolean reviewEnabled = true;
    private String legacyStrategy = "KEEP_EXISTING_UNMODIFIED";
}
