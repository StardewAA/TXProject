package cn.iocoder.txgy.module.member.api.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 商城平台账号租户身份")
@Data
public class MemberPlatformTenantMembershipRespDTO {

    private Long tenantId;
    private Long enterpriseId;
    private String memberType;
}
