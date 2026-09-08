package cn.iocoder.txgy.module.product.controller.admin.enterprise.vo;
import lombok.Data; import java.time.LocalDateTime;
@Data public class ProductEnterpriseRespVO { private Long id; private String name; private String creditCode; private Integer type; private String licenseUrl; private String contactName; private String contactMobile; private String address; private String introduction; private Integer auditStatus; private String auditRemark; private LocalDateTime createTime; }
