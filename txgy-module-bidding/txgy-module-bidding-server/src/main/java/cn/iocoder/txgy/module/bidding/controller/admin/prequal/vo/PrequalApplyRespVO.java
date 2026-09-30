package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 资格预审申请响应")
@Data
public class PrequalApplyRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "资格预审ID")
    private Long prequalId;

    @Schema(description = "资格预审编号")
    private String prequalCode;

    @Schema(description = "招标项目编号")
    private String tenderProjectCode;

    @Schema(description = "标段编号")
    private String bidSectionCode;

    @Schema(description = "投标主体ID")
    private Long subjectId;

    @Schema(description = "申请人用户ID")
    private Long userId;

    @Schema(description = "申请文件 FileApi 主键")
    private Long fileId;

    @Schema(description = "申请提交时间")
    private LocalDateTime applyTime;

    @Schema(description = "状态（PrequalApplyStatusEnum）")
    private String status;

    @Schema(description = "审查结果（PASSED/FAILED）")
    private String reviewResult;

    @Schema(description = "审查说明/不通过原因")
    private String reviewReason;

    @Schema(description = "审查完成时间")
    private LocalDateTime reviewTime;

    /** 资格预审材料集附件（契约《投标与资格预审多文件化-2026-10-02》R3，可为空数组） */
    @Schema(description = "资格预审材料集附件（attachment/list?bizType=PREQUAL_APPLY&bizCode=applyId，按 seq 排序；可为空数组）")
    private List<AttachmentRespVO> attachments;
}
