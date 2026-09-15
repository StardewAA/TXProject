package cn.iocoder.txgy.module.cms.controller.admin.Policy.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 政策资讯分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsPolicyPageReqVO extends PageParam {

    @Schema(description = "政策标题")
    private String title;
    @Schema(description = "发布部门")
    private String publishDept;
    @Schema(description = "来源网站")
    private String sourceName;
    @Schema(description = "层级：1国家 2省 3市 4区县")
    private Integer policyLevel;
    @Schema(description = "主题分类")
    private String category;
    @Schema(description = "内容类型：法律法规/金融政策")
    private String contentType;
    @Schema(description = "效力状态：现行有效/已修订/已废止/即将失效")
    private String effectiveStatus;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "发布状态")
    private Integer publishStatus;
}
