package cn.iocoder.txgy.module.member.controller.app.address.vo;

import lombok.Getter;

/** Public capabilities only. No provider secrets, arbitrary URLs or real-address lookup. */
@Getter
public class AppAddressLocationCapabilityRespVO {
    private final String status;
    private final String provider;
    private final String coordinateSystem = "GCJ02";
    private final boolean enabled;
    private final boolean manualInputEnabled = true;
    private final String message;

    public AppAddressLocationCapabilityRespVO() {
        this(false, "NONE");
    }

    public AppAddressLocationCapabilityRespVO(boolean enabled, String provider) {
        this.enabled = enabled;
        this.provider = enabled && "AMAP".equalsIgnoreCase(provider) ? "AMAP" : "NONE";
        this.status = enabled && "AMAP".equals(this.provider) ? "READY" : "NOT_CONFIGURED";
        this.message = enabled && "AMAP".equals(this.provider)
                ? "高德地图选点已启用；仍可继续使用手工地址"
                : "地图服务暂未配置，请手工填写收货地址";
    }
}
