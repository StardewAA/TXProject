package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 证照类型 Response VO")
@Data
public class HrmCertificateTypeRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "证照类型名称", example = "高处作业操作证")
    private String name;

    @Schema(description = "编码", example = "HIGH_WORK")
    private String code;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否必持", example = "true")
    private Boolean mandatory;

    @Schema(description = "是否需要复审", example = "true")
    private Boolean needReview;

    @Schema(description = "到期预警提前天数", example = "60")
    private Integer warnDays;

    @Schema(description = "发证机关", example = "应急管理局")
    private String issuingAuthority;

    @Schema(description = "显示顺序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "持证人数")
    private Long certificateCount;

}
