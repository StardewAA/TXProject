package cn.iocoder.txgy.module.hrm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 合同模板 Response VO")
@Data
public class HrmContractTemplateRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板分类：1 劳动合同，2 个人电子档案，3 培训协议", example = "1")
    private Integer category;

    @Schema(description = "模板分类名称", example = "劳动合同")
    private String categoryName;

    @Schema(description = "合同类型", example = "5")
    private Integer contractType;

    @Schema(description = "适用用工形式")
    private String employType;

    @Schema(description = "适用用工主体编号")
    private Long employerId;

    @Schema(description = "适用用工主体名称")
    private String employerName;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "状态：0 启用，1 停用")
    private Integer status;

    @Schema(description = "是否该类型默认模板")
    private Boolean defaultStatus;

    @Schema(description = "模板文件地址")
    private String fileUrl;

    @Schema(description = "模板文件 SHA-256")
    private String fileHash;

    @Schema(description = "缩略图地址（PNG）")
    private String thumbnailUrl;

    @Schema(description = "预览文件地址（PDF）")
    private String previewUrl;

    @Schema(description = "显示顺序")
    private Integer sort;

    @Schema(description = "来源目录")
    private String sourceDir;

    @Schema(description = "模板变量清单")
    private String variables;

    @Schema(description = "本次变更说明")
    private String changeNote;

    @Schema(description = "变更人编号")
    private Long changeUserId;

    @Schema(description = "变更时间")
    private LocalDateTime changeTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
