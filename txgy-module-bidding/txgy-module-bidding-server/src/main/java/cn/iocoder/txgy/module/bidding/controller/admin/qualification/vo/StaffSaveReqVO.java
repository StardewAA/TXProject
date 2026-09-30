package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 资质人员录入请求。
 *
 * <p>规范出处：v3.3 表 15-3。
 */
@Data
public class StaffSaveReqVO {
    @jakarta.validation.constraints.NotNull(message = "企业不能为空")
    private Long entId;
    private Long qualId;
    @jakarta.validation.constraints.NotBlank(message = "角色不能为空")
    private String staffRole;
    @jakarta.validation.constraints.NotBlank(message = "姓名不能为空")
    private String personName;
    private String major;
    private String regNo;
    private String titleLevel;
    private Boolean isFullTime;
    private java.time.LocalDate regValidTo;
    private Long bindedCertId;
}
