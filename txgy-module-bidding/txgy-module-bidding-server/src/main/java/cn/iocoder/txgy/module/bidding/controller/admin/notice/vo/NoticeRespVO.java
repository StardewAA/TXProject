package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告/公示列表项（列表/分页返回）。
 */
@Schema(description = "管理后台 - 公告/公示列表项")
@Data
public class NoticeRespVO {

    @Schema(description = "公告 ID")
    private Long id;
    @Schema(description = "公告内部编号")
    private String noticeCode;
    @Schema(description = "公告/公示类型 code")
    private String noticeType;
    @Schema(description = "公告/公示类型展示名")
    private String noticeTypeName;
    @Schema(description = "公告名称")
    private String title;
    @Schema(description = "项目编号")
    private String projectCode;
    @Schema(description = "项目名称")
    private String projectName;
    @Schema(description = "发布单位名称")
    private String publisherName;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "公告生命周期状态（AnnouncementStatusEnum，招标全流程标准模型）")
    private String announcementStatus;
    @Schema(description = "企业内部公告标记")
    private Boolean internalFlag;
    @Schema(description = "公开级别")
    private String publicLevel;
    @Schema(description = "正文预览（当前生效版本前 200 字）")
    private String contentPreview;

}
