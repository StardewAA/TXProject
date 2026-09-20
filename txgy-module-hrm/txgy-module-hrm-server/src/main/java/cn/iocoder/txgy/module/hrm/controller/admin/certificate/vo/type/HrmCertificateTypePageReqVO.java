package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - HRM 证件类型分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmCertificateTypePageReqVO extends PageParam {

    @Schema(description = "证件类型名称，模糊匹配", example = "特种作业")
    private String name;

    @Schema(description = "证件类型编码，模糊匹配", example = "SPECIAL")
    private String code;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否需要复审", example = "true")
    private Boolean needReview;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

}
