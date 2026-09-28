package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口1 ProcessTrackItemVO
// 雪花 ID 一律 String（doc 24 §2.1：超 JS 安全整数，禁止 Number()）

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 投标流程跟踪总览项（项目粒度，出参自带公司信息）")
@Data
public class ProcessTrackItemVO {

    @Schema(description = "跟踪行主键（雪花 ID String）")
    private String id;
    @Schema(description = "公司维度租户ID（String）")
    private String tenantId;
    @Schema(description = "公司名")
    private String tenantName;
    @Schema(description = "关联关注行ID（意向来源，可追溯）")
    private String followItemId;
    @Schema(description = "维度代码（标段编号）")
    private String followKey;
    @Schema(description = "维度类型（PROJECT/SECTION）")
    private String followType;
    @Schema(description = "项目名称")
    private String projectName;
    @Schema(description = "项目编号（招标项目编号）")
    private String projectCode;
    @Schema(description = "招标人（发布单位）")
    private String issuerName;
    @Schema(description = "地区")
    private String region;
    @Schema(description = "预算金额")
    private String amount;
    @Schema(description = "投标截止（epoch 毫秒，Long）")
    private Long deadline;
    @Schema(description = "阶段 code")
    private String state;
    @Schema(description = "阶段中文名（前端优先用字典，回退用此列）")
    private String stateName;
    @Schema(description = "负责人用户ID（String）")
    private String ownerUserId;
    @Schema(description = "负责人姓名")
    private String ownerName;
    @Schema(description = "负责人职务（可空）")
    private String ownerPost;
    @Schema(description = "负责人联系方式（可空）")
    private String ownerPhone;
    @Schema(description = "意向投标时间")
    private LocalDateTime intentionTime;
    @Schema(description = "报名时间")
    private LocalDateTime registerTime;
    @Schema(description = "审核完成时间")
    private LocalDateTime reviewTime;
    @Schema(description = "已投标时间")
    private LocalDateTime biddedTime;
    @Schema(description = "流程结束时间")
    private LocalDateTime terminatedTime;
    @Schema(description = "审核结果 APPROVED/REJECTED")
    private String reviewResult;
    @Schema(description = "审核说明/不通过原因")
    private String reviewReason;
    @Schema(description = "标书名")
    private String documentName;
    @Schema(description = "备注")
    private String remark;

    /** 投标文件集附件清单（主件 document_file_id 之外的附加文件；契约《投标与资格预审多文件化-2026-10-02》R2，可为空数组） */
    @Schema(description = "投标文件集附件清单（attachment/list?bizType=BID_TRACK&bizCode=trackId，按 seq 排序；可为空数组）")
    private List<AttachmentRespVO> attachments;
}
