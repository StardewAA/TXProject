package cn.iocoder.txgy.module.member.api.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "RPC 服务 - 商城平台账号")
@Data
public class MemberPlatformAccountRespDTO {

    private Long platformAccountId;
    private String platformNo;
    private Long canonicalMemberId;
    private Long legacyMemberId;
    private Long sourceTenantId;
    private List<MemberPlatformTenantMembershipRespDTO> memberships;
}
