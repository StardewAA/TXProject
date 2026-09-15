package cn.iocoder.txgy.module.cms.controller.admin.Standard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 行业标准规范库创建/修改 Request VO")
@Data
public class CmsStandardSaveReqVO {

    @Schema(description = "主键（更新时必传）")
    private Long standardId;
    @NotNull(message = "标准编号，如GB 50264-2013不能为空")
    @Schema(description = "标准编号，如GB 50264-2013")
    private String standardNo;
    @NotNull(message = "标准名称不能为空")
    @Schema(description = "标准名称")
    private String standardName;
    @NotNull(message = "类型：1国标 2行标 3地方标 4团标 5企业标不能为空")
    @Schema(description = "类型：1国标 2行标 3地方标 4团标 5企业标")
    private Integer standardType;
    @Schema(description = "行标行业代号")
    private String industryCode;
    @NotNull(message = "发布机构不能为空")
    @Schema(description = "发布机构")
    private String publishOrg;
    @NotNull(message = "发布日期不能为空")
    @Schema(description = "发布日期")
    private LocalDate publishDate;
    @NotNull(message = "实施日期不能为空")
    @Schema(description = "实施日期")
    private LocalDate implementDate;
    @Schema(description = "状态：1现行 2即将实施 3废止 4被替代")
    private Integer status;
    @Schema(description = "废止日期")
    private LocalDate abolishDate;
    @Schema(description = "替代标准ID")
    private Long replaceStandardId;
    @Schema(description = "适用领域JSON数组")
    private List<String> industryField;
    @Schema(description = "防护对象JSON数组")
    private List<String> protectObject;
    @Schema(description = "施工场景JSON数组")
    private List<String> scene;
    @Schema(description = "标准摘要")
    private String summary;
    @Schema(description = "关键词")
    private String keywords;
    @Schema(description = "全文附件ID")
    private Long fileId;
    @Schema(description = "封面图URL")
    private String coverImage;
    @Schema(description = "浏览量")
    private Integer viewCount;
    @Schema(description = "下载量")
    private Integer downloadCount;
    @Schema(description = "订阅数")
    private Integer subscribeCount;
    @Schema(description = "是否置顶")
    private Integer isTop;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "发布状态")
    private Integer publishStatus;
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;
}
