package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "员工端 - 个人简历版本 Response VO")
@Data
public class HrmPortalResumeVersionRespVO {

    @Schema(description = "版本编号")
    private Long id;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "父版本编号")
    private Long parentVersionId;

    @Schema(description = "来源个人简历编号")
    private Long sourceResumeId;

    @Schema(description = "来源目标职位编号")
    private Long sourcePostId;

    @Schema(description = "创建方式：1 手动固化，2 确认优化建议")
    private Integer createType;

    @Schema(description = "修改摘要")
    private String changeSummary;

    @Schema(description = "内容结构版本")
    private String schemaVersion;

    @Schema(description = "状态：0 有效，1 已失效")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 结构化简历内容。
     *
     * <p>列表场景不返回，仅在按编号查询单个版本时返回，避免列表响应体过大。</p>
     */
    @Schema(description = "结构化简历内容，仅单个查询返回")
    private String content;
}
