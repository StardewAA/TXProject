package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7.2/§15.6；《招投标模块开发规范》§3.1（VO 规约）、§4.4（管理端隔离）；03 §3（管理列表不依赖公开 page）
// 说明：管理分页不做关键词全文（ES 索引只含 ACTIVE 且审核通过才建，NEW/REJECTED 无法被 ES 命中；业务表 LIKE 被红线 4.6 禁止）——
//       管理列表定位=“按状态/审核态/来源/类型/时间结构化过滤 + 行内操作”，与 03 §3 / N1 契约一致。

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 外部公告管理分页请求（管理端审核/运营列表）。
 *
 * <p>与公开 page（SOCIAL+ACTIVE 硬过滤）不同：本请求覆盖 NEW/ACTIVE/INACTIVE/ARCHIVED ×
 * UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN 全维度，供管理端审核页「列表 + 审核通过/驳回/下线 + AI 抽取 + 正文结构化」使用。
 */
@Schema(description = "管理后台 - 外部公告管理分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeExternalManagePageReqVO extends PageParam {

    @Schema(description = "展示状态：NEW/ACTIVE/INACTIVE/ARCHIVED（空=全部）")
    private String status;

    @Schema(description = "审核状态：UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN（空=全部）")
    private String auditStatus;

    @Schema(description = "来源站 code")
    private String sourceCode;

    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;

    @Schema(description = "公告类型（外站原文/权威码均可）")
    private String noticeTypeRaw;

    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN（管理端按 AI 抽取结果筛选）")
    private String industryFlag;

    @Schema(description = "关联项目编号（跟踪维度）")
    private String projectCode;

    @Schema(description = "开始发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime beginTime;

    @Schema(description = "结束发布时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

}
