package cn.iocoder.txgy.module.product.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商城平台租户目录 Response VO")
@Data
public class MallPortalTenantRespVO {

    private Long id;
    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private String tenantLogo;
    private String mainCategories;
    private Integer openStatus;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
