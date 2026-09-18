package cn.iocoder.txgy.module.member.controller.app.address.vo;

import lombok.Value;

/** Public capabilities only. No provider secrets, arbitrary URLs or real-address lookup. */
@Value
public class AppAddressLocationCapabilityRespVO {
    String status = "NOT_CONFIGURED";
    String provider = "NONE";
    String coordinateSystem = "GCJ02";
    boolean enabled = false;
    boolean manualInputEnabled = true;
    String message = "地图服务暂未配置，请手工填写收货地址";
}
