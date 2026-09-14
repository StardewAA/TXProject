package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 岗位工种新增/修改 Request VO")
@Data
public class JobSaveReqVO {

    @Schema(description = "工种 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "工种编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "OP-02")
    @NotEmpty(message = "工种编码不能为空")
    @Size(max = 20, message = "工种编码长度不能超过 20 个字符")
    private String code;

    @Schema(description = "工种名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐涂装工")
    @NotEmpty(message = "工种名称不能为空")
    @Size(max = 100, message = "工种名称长度不能超过 100 个字符")
    private String name;

    @Schema(description = "序列编码：OP 操作 / TC 技术 / QC 质检检测 / MG 管理 / SP 支持", requiredMode = Schema.RequiredMode.REQUIRED, example = "OP")
    @NotEmpty(message = "序列编码不能为空")
    @Size(max = 10, message = "序列编码长度不能超过 10 个字符")
    private String seqCode;

    @Schema(description = "序列名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "操作序列")
    @NotEmpty(message = "序列名称不能为空")
    @Size(max = 50, message = "序列名称长度不能超过 50 个字符")
    private String seqName;

    @Schema(description = "业务类目（文档章节号）", example = "一/二/三/二十三")
    @Size(max = 200, message = "业务类目长度不能超过 200 个字符")
    private String bizCategory;

    @Schema(description = "学习域/课程组（原始文本）", example = "A,B01-B03")
    @Size(max = 200, message = "学习域长度不能超过 200 个字符")
    private String learnDomain;

    @Schema(description = "规范化学习域/课程组编码（逗号分隔，关联课程分类 code；* 表示全域）", example = "A,B01,B02,B03")
    @Size(max = 500, message = "学习域编码长度不能超过 500 个字符")
    private String learnDomainCodes;

    @Schema(description = "关键技能", example = "除锈等级判定、喷砂设备操作")
    @Size(max = 500, message = "关键技能长度不能超过 500 个字符")
    private String keySkills;

    @Schema(description = "最高成长等级（关联成长等级字典 code）", example = "SENIOR_TECHNICIAN")
    @Size(max = 30, message = "最高成长等级长度不能超过 30 个字符")
    private String maxLevel;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：active 启用 / disabled 停用", example = "active")
    @Size(max = 20, message = "状态长度不能超过 20 个字符")
    private String status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
