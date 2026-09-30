package cn.iocoder.txgy.module.cms.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 法律效力与官方来源健康核验配置。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "txgy.cms.validity-check")
public class CmsLawValidityCheckProperties {

    /** 生产环境开启；本地默认关闭，避免开发时主动访问政府网站。 */
    private boolean enabled;

    /** 默认每天凌晨 03:20 执行。 */
    private String cron = "0 20 3 * * ?";

    @Min(1)
    @Max(500)
    private int batchSize = 100;

    @Min(3)
    @Max(60)
    private int requestTimeoutSeconds = 12;

    /** 测试环境可覆盖，生产保持国家法律法规数据库官方地址。 */
    private String nationalLawBaseUrl = "https://flk.npc.gov.cn";

}
