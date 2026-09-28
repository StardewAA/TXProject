package cn.iocoder.txgy.module.member.controller.app.tenant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 APP - 当前会员会话的可信租户")
@Data
public class AppMallTenantSessionRespVO {

    private Long tenantId;
    private Long userId;

}
