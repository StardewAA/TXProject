package cn.iocoder.txgy.module.member.framework.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 商城目录的显式白名单。配置目录不代表已具备跨租户注册或切换能力。 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.tenant")
public class MallTenantProperties {

    private boolean enabled = false;
    /** Explicit rollout for isolated anonymous browsing only; does not enable shopping or registration. */
    private boolean browsePreviewEnabled = false;
    /** Opt-in full reload entry. Keep disabled until both clients and tenant resources are accepted. */
    private boolean scopedEntryEnabled = false;
    private Long publicTenantId;
    /** Server-only customer service owner; never an employee or sales authorization. */
    private Long platformEnterpriseId;
    private List<Entry> entries = new ArrayList<>();

    @Data
    public static class Entry {
        private Long tenantId;
        private String name;
        private boolean publicVisible = false;
        private boolean registrationEnabled = false;
    }

}
