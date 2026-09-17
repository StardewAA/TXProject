package cn.iocoder.txgy.module.member.controller.app.tenant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Schema(description = "用户 APP - 商城租户目录与实际可用能力")
@Data
public class AppMallTenantConfigRespVO {

    private boolean enabled;
    private Long legacyTenantId;
    private Long publicTenantId;
    private List<Tenant> tenants = List.of();
    private final Capabilities capabilities = new Capabilities();

    @Data
    public static class Tenant {
        private Long tenantId;
        private String name;
        /** 仅展示显式白名单中的注册选项；提交时仍由认证策略再次核验。 */
        private boolean registrationEnabled = false;
    }

    @Getter
    public static class Capabilities {
        @lombok.Setter
        private boolean browsePreview = false;
        @lombok.Setter
        private boolean scopedEntry = false;
        @lombok.Setter
        private boolean browseSwitch = false;
        @lombok.Setter
        private boolean registrationSelection = false;
    }

}
