package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 资质标准登记请求（可版本化）。
 *
 * <p>规范出处：v3.3 表 15-2（req_staff 必填等四要素指标 JSON）。
 */
@Data
public class StandardCreateReqVO {
    @jakarta.validation.constraints.NotBlank(message = "序列不能为空")
    private String qualSerial;
    @jakarta.validation.constraints.NotBlank(message = "类别不能为空")
    private String qualCategory;
    @jakarta.validation.constraints.NotBlank(message = "等级不能为空")
    private String qualGrade;
    @jakarta.validation.constraints.NotBlank(message = "标准版本不能为空")
    private String stdVersion;
    /** 人员指标 JSON（如 {"REGISTERED_BUILDER":2}） */
    @jakarta.validation.constraints.NotBlank(message = "人员指标不能为空")
    private String reqStaff;
    private String reqAssets;
    private String reqPerformance;
    private String reqEquipment;
    private java.time.LocalDate effectiveFrom;
    private java.time.LocalDate effectiveTo;
    private String sourceRef;
}
