package cn.iocoder.txgy.module.member.framework.tenant.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Independent activation gate. Never put a real HMAC key or SMS template in source control. */
@Data
@Component
@ConfigurationProperties(prefix = "mall.tenant.auth")
public class MallTenantAuthProperties {
    private boolean enabled = false;
    @ToString.Exclude
    private String hmacSecret = "";
    private String smsTemplateCode = "";
    private int codeTtlSeconds = 300;
    private int resendSeconds = 60;
    private int maxAttempts = 5;
    private int mobileDailyLimit = 10;
    private int ipHourlyLimit = 100;
    private int mobileVerifyLimit = 30;
    private int ipVerifyLimit = 300;
}
