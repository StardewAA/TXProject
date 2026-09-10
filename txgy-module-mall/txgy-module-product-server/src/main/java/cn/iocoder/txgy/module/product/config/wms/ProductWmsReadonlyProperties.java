package cn.iocoder.txgy.module.product.config.wms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商城侧 WMS 只读查询配置。
 *
 * <p>默认使用 mock，本地直接读同库 WMS 表；后续切换 remote 时，改为调用文档里的
 * {@code /admin-api/wms/inventory/sku-summary} 接口。</p>
 */
@Data
@ConfigurationProperties(prefix = "txgy.integration.wms.readonly")
public class ProductWmsReadonlyProperties {

    /**
     * 运行模式：mock / remote
     */
    private String mode = "mock";

    /**
     * 远程模式下调用 WMS 只读接口时使用的 Authorization 头。
     *
     * <p>示例：Bearer xxx</p>
     */
    private String authorization;

}
