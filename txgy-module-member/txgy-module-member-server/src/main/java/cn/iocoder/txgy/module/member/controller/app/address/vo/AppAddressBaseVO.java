package cn.iocoder.txgy.module.member.controller.app.address.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

// TODO 芋艿：example 缺失
/**
* 用户收件地址 Base VO，提供给添加、修改、详细的子 VO 使用
* 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
*/
@Data
public class AppAddressBaseVO {

    @Schema(description = "收件人名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收件人名称不能为空")
    private String name;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "手机号不能为空")
    private String mobile;

    @Schema(description = "地区编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "地区编号不能为空")
    private Long areaId;

    @Schema(description = "收件详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收件详细地址不能为空")
    private String detailAddress;

    @Schema(description = "可选经度；必须与纬度成对提供")
    private BigDecimal longitude;
    private BigDecimal latitude;
    @Schema(description = "坐标系，首版仅支持 GCJ02")
    private String coordSystem;
    @Schema(description = "用户申报的选点来源：MAP_PICKER / DEVICE_LOCATION / GEOCODER；不代表平台核验")
    private String locationSource;
    @Schema(description = "点位精度级别：ADDRESS / BUILDING / APPROXIMATE")
    private String locationPrecision;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime locationConfirmedAt;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "本次明确确认点位与文字地址一致；服务端记录确认时间")
    private Boolean locationConfirmed;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "明确清除已有点位，仍保留合法手工地址")
    private Boolean clearLocation;

    @Schema(description = "是否默认地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否默认地址不能为空")
    private Boolean defaultStatus;

}
