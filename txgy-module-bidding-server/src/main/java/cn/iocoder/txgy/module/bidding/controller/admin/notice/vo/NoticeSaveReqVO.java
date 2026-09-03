package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 公告/公示新建/修改入参。
 *
 * <p>payload 字段规则：key 必须是对应权威数据集 VO（NoticeTypeEnum.datasetClass）的组件名
 * （照录规范英文名称，含拼写瑕疵），由 Service 用 NoticePayloadUtils 规整为字段全集——
 * "该有的字段一个不能少"，未知字段剔除。content 为正文（版本化，变更追加新版本）。
 */
@Schema(description = "管理后台 - 公告/公示新建/修改入参")
@Data
public class NoticeSaveReqVO {

    @Schema(description = "公告 ID；null=新建，非 null=修改", example = "1")
    private Long id;

    @Schema(description = "公告内部编号；新建时为空则系统生成")
    private String noticeCode;

    @Schema(description = "公告/公示类型 code", example = "TENDER_NOTICE")
    @NotBlank(message = "公告类型不能为空")
    private String noticeType;

    @Schema(description = "公告/公示名称（标题）", example = "某项目施工招标公告")
    @NotBlank(message = "公告名称不能为空")
    private String title;

    @Schema(description = "关联招标项目编号（可为空）")
    private String projectCode;

    @Schema(description = "招标项目名称快照")
    private String projectName;

    @Schema(description = "发布单位名称（名称快照列）")
    private String publisherName;

    @Schema(description = "公告/公示发布时间；发布时未填则取当前时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime publishTime;

    @Schema(description = "1=企业内部公告（仅本租户可见）", example = "false")
    private Boolean internalFlag;

    @Schema(description = "公开级别：SOCIAL/GOVERNMENT", example = "SOCIAL")
    private String publicLevel;

    @Schema(description = "权威数据集字段（key=规范英文名称，缺失自动补 null）")
    private Map<String, Object> payload;

    @Schema(description = "正文全文（文本形态；发布前必填）")
    private String content;

    @Schema(description = "版式文件（PDF）file_id（可选）")
    private Long pdfFileId;

}
