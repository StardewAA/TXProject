package cn.iocoder.txgy.module.product.controller.app.enterprise;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class EnterpriseApplyReqVO {
    @NotBlank @Size(max=100) private String name;
    @NotBlank @Pattern(regexp="[0-9A-Z]{18}", message="信用代码须为18位大写字母或数字") private String creditCode;
    @NotBlank @Size(max=1500000) private String licenseData;
    @NotBlank @Size(max=32) private String contactName;
    @NotBlank @Pattern(regexp="1[0-9]{10}", message="联系电话须为手机号") private String contactMobile;
    @NotBlank @Size(max=255) private String address;
    @Size(max=1024) private String introduction;
}
