package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证件类型 Response VO")
@Data
@Accessors(chain = true)
public class HrmCertificateTypeRespVO {

    @Schema(description = "证件类型编号", example = "1024")
    private Long id;

    @Schema(description = "证件类型名称", example = "特种作业操作证")
    private String name;

    @Schema(description = "证件类型编码", example = "SPECIAL_OPERATION")
    private String code;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否需要复审", example = "true")
    private Boolean needReview;

    @Schema(description = "复审周期（月）", example = "36")
    private Integer reviewCycleMonths;

    @Schema(description = "默认提前预警天数", example = "30")
    private Integer advanceDays;

    @Schema(description = "是否必须上传证件附件", example = "false")
    private Boolean requireAttachment;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

    @Schema(description = "显示顺序", example = "10")
    private Integer sort;

    @Schema(description = "备注", example = "应急管理部门颁发，需按期复审")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
