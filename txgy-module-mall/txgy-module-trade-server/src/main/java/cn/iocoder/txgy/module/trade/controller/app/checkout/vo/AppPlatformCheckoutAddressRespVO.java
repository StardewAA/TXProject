package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "用户 App - 平台结算收货地址快照")
@Data
public class AppPlatformCheckoutAddressRespVO {

    private Long addressId;
    private Long sourceTenantId;
    private String name;
    private String mobile;
    private Long areaId;
    private String detailAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordSystem;

}
