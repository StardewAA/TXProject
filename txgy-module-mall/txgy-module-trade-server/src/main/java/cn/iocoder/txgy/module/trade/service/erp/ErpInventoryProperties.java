package cn.iocoder.txgy.module.trade.service.erp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "txgy.trade.erp-inventory")
public class ErpInventoryProperties {
    /** pending 永远不伪造成功；mock 仅用于显式开启的本地测试。 */
    private String mode = "pending";
    private boolean mockEnabled;
    private boolean callbackEnabled;
    @lombok.ToString.Exclude
    private String callbackSecret;
}
