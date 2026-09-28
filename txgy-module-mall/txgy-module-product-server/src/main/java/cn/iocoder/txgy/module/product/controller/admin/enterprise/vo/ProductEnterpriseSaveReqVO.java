package cn.iocoder.txgy.module.product.controller.admin.enterprise.vo;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class ProductEnterpriseSaveReqVO { private Long id; @NotBlank(message="企业名称不能为空") private String name; @NotBlank(message="统一社会信用代码不能为空") private String creditCode; private Integer type; private String licenseUrl; private String contactName; private String contactMobile; private String address; private String introduction; private Integer auditStatus; private String auditRemark; }
