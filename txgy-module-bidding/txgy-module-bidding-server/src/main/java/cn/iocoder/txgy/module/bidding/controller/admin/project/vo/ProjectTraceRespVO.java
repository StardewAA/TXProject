package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2（页面/内部接口另建 VO）+ 数据规范 V2.0 追溯口径（项目→招标项目→标段三级编号）
// [业务目标] 一个项目（或招标项目/标段）追溯到全部信息：主档 + 标段 + 内部公告 + 外部公告 + 备案 + 关注 + 参与主体 + 统计
// [为什么] 各业务域数据分散存储且编号关联（project_code/tender_project_code/bid_section_code），
//           前端若逐域调接口需 6+ 次请求且口径易不一致；本 VO 由服务端一次聚合，编号命中即精确关联，避免 LIKE 拼装

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 项目全景追溯 Response")
@Data
public class ProjectTraceRespVO {

    @Schema(description = "追溯入口编号", example = "0866-26E1SXQY0885")
    private String queryCode;
    @Schema(description = "入口维度：PROJECT/TENDER_PROJECT/BID_SECTION")
    private String queryType;

    @Schema(description = "项目主档（入口为项目编号或经招标项目上溯命中时返回）")
    private ProjectRespVO project;
    @Schema(description = "招标项目列表（同级/下级）")
    private List<TenderProjectRespVO> tenderProjects;
    @Schema(description = "标段（包）列表")
    private List<BidSectionRespVO> sections;
    @Schema(description = "内部公告（T 轨，本租户）")
    private List<NoticeItem> notices;
    @Schema(description = "外部公告（G 轨采集，全平台）")
    private List<ExternalNoticeItem> externalNotices;
    @Schema(description = "备案记录（T 轨）")
    private List<FilingItem> filings;
    @Schema(description = "关注项（当前用户）")
    private List<FollowItem> follows;
    @Schema(description = "参与主体（招标人快照 + 本租户平台授权主体）")
    private List<SubjectItem> subjects;
    @Schema(description = "统计与最近动态")
    private Stats stats;

    @Data
    @Schema(description = "内部公告追溯项")
    public static class NoticeItem {
        private Long id;
        private String noticeCode;
        private String noticeType;
        private String noticeTypeName;
        private String title;
        private String status;
        private String projectCode;
        private String tenderProjectCode;
        /** 公告关联的标段编号（首段）；未关联标段（公告级/项目级）为 null（契约：追溯公告标段级关联-2026-10-02，A 部分） */
        private String bidSectionCode;
        /** 公告关联的标段编号全集（fields_json BID_SECTION_CODES 原样，半角分号分隔；未关联标段为 null）（契约 B 部分，前端「标段行直达申请」依赖） */
        private String bidSectionCodes;
        private Long publishTime;
    }

    @Data
    @Schema(description = "外部公告追溯项")
    public static class ExternalNoticeItem {
        private Long id;
        private String title;
        private String sourceCode;
        private String sourceName;
        private String contentType;
        private String status;
        private String auditStatus;
        private String origUrl;
        private String projectCode;
        private String tenderProjectCode;
        private String bidSectionCodes;
        private Long publishTime;
        /** 平台详情可达（ACTIVE 才可在平台内打开） */
        private Boolean available;
    }

    @Data
    @Schema(description = "备案追溯项")
    public static class FilingItem {
        private Long id;
        private String filingType;
        private String projectCode;
        private String tenderProjectCode;
        private String status;
        private Long submitTime;
    }

    @Data
    @Schema(description = "关注追溯项")
    public static class FollowItem {
        private Long id;
        private String followType;
        private String followKey;
        private String displayName;
        private String followStatus;
        private Long latestDynamicTime;
        private Integer unreadCount;
    }

    @Data
    @Schema(description = "参与主体追溯项")
    public static class SubjectItem {
        /** 来源：TENDER_PROJECT_SNAPSHOT=招标项目主档快照 / TENANT_AUTH=本租户平台授权主体 */
        private String source;
        private String subjectRole;
        private String subjectName;
        private String codeType;
        private String code;
        private String authStatus;
        private Boolean profileComplete;
    }

    @Data
    @Schema(description = "追溯统计")
    public static class Stats {
        private Integer tenderProjectCount;
        private Integer sectionCount;
        private Integer noticeCount;
        private Integer externalNoticeCount;
        private Integer filingCount;
        private Integer followCount;
        /** 各域最近动态时间（epoch 毫秒，无则 null） */
        private Long latestDynamicTime;
    }
}
