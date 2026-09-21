package cn.iocoder.txgy.module.hrm.controller.admin.employment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 用工形式 Response VO")
@Data
public class HrmEmploymentTypeRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "用工形式名称", example = "临时工")
    private String name;

    @Schema(description = "用工形式编码", example = "TEMPORARY")
    private String code;

    @Schema(description = "默认合同模板编号", example = "5")
    private Long contractTemplateId;

    @Schema(description = "默认合同模板名称", example = "以完成一定工作任务为期限劳动合同")
    private String contractTemplateName;

    @Schema(description = "默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件", example = "2")
    private Integer salaryMode;

    @Schema(description = "是否按工日统计出勤", example = "true")
    private Boolean needWorkDay;

    @Schema(description = "是否要求人脸打卡", example = "true")
    private Boolean needFaceClock;

    @Schema(description = "是否参与自动转正", example = "false")
    private Boolean joinRegular;

    @Schema(description = "显示顺序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "项目制短工")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
