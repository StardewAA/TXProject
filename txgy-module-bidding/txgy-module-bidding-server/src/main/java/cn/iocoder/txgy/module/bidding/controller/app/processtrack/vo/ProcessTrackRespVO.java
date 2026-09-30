package cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo;

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 投标流程跟踪 Response")
@Data
public class ProcessTrackRespVO {

    @Schema(description = "流程跟踪ID")
    private Long id;
    @Schema(description = "标段编号")
    private String bidSectionCode;
    @Schema(description = "招标项目编号")
    private String tenderProjectCode;
    @Schema(description = "项目名称（快照）")
    private String projectName;
    @Schema(description = "发布单位（快照）")
    private String issuerName;
    @Schema(description = "当前状态：INTENTION/QUALIFICATION_REVIEW/PENDING_BID/BIDDED/TERMINATED")
    private String currentState;
    @Schema(description = "关注条目ID（关联 bidding_follow_item；门户据此与「我的关注」精确对账）")
    private Long followItemId;

    @Schema(description = "维度代码（标段编号；建档时即关注键 followKey）")
    private String followKey;

    @Schema(description = "维度类型（PROJECT/SECTION）")
    private String followType;

    @Schema(description = "意向投标时间（= 关注时间）")
    private LocalDateTime intentionTime;

    @Schema(description = "报名时间")
    private LocalDateTime registerTime;

    @Schema(description = "审核完成时间")
    private LocalDateTime reviewTime;

    @Schema(description = "进入待投标时间")
    private LocalDateTime pendingBidTime;
    @Schema(description = "审核结果 APPROVED/REJECTED")
    private String reviewResult;
    @Schema(description = "审核说明")
    private String reviewReason;
    @Schema(description = "投标文件（加密）fileId")
    private Long documentFileId;

    @Schema(description = "投标文件名")
    private String documentName;
    @Schema(description = "投标文件大小（字节）")
    private Long documentSize;
    @Schema(description = "投标文件 SM3 指纹")
    private String documentHash;
    @Schema(description = "指纹算法（SM3，国密 GB/T 32905）")
    private String documentHashAlgo;
    @Schema(description = "投标文件数字签名（CA SDK 签名 base64，SM2）")
    private String documentSignature;
    @Schema(description = "CA 证书序列号")
    private String caCertNo;
    @Schema(description = "已投标时间")
    private LocalDateTime biddedTime;
    @Schema(description = "流程结束时间")
    private LocalDateTime terminatedTime;
    @Schema(description = "备注")
    private String remark;

    /** 投标文件集附件清单（契约《投标与资格预审多文件化-2026-10-02》R2，可为空数组） */
    @Schema(description = "投标文件集附件清单（bizType=BID_TRACK，可按文件类型区分投标函/报价表/技术标/商务标等；可为空数组）")
    private List<AttachmentRespVO> attachments;
}
