package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 资格预审保存 Request（招标方发起）")
@Data
public class PrequalSaveReqVO {

    @Schema(description = "主键（编辑时必填）")
    private Long id;

    @Schema(description = "招标项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "I6101002026000001001")
    @NotBlank(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（空=招标项目级资格预审）", example = "I6101002026000001001001")
    private String bidSectionCode;

    @Schema(description = "资格预审项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资格预审项目名称不能为空")
    private String title;

    @Schema(description = "关联资格预审公告编号（bidding_notice.notice_code）")
    private String noticeCode;

    @Schema(description = "资格预审文件 FileApi 主键")
    private Long fileId;

    @Schema(description = "申请开始时间（资格预审文件停止发售时间）")
    private LocalDateTime applyStartTime;

    @Schema(description = "申请截止时间（提交资格预审申请文件截止）")
    private LocalDateTime applyDeadline;

    @Schema(description = "备注")
    private String remark;
}
