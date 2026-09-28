package cn.iocoder.txgy.module.member.api.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "RPC 服务 - 经平台账号校验的旧会员收货地址")
@Data
public class MemberPlatformAddressRespDTO {

    private Long platformAccountId;
    private Long legacyMemberId;
    private Long sourceTenantId;
    private Long addressId;
    private String name;
    private String mobile;
    private Long areaId;
    private String detailAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordSystem;

}
