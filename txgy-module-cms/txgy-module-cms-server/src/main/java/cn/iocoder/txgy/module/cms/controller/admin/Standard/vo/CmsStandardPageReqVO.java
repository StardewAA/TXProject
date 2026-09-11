package cn.iocoder.txgy.module.cms.controller.admin.Standard.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 行业标准规范库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsStandardPageReqVO extends PageParam {

    @Schema(description = "标准名称")
    private String standardName;
    @Schema(description = "标准编号，如GB 50264-2013")
    private String standardNo;
    @Schema(description = "关键词")
    private String keywords;
    @Schema(description = "类型：1国标 2行标 3地方标 4团标 5企业标")
    private Integer standardType;
    @Schema(description = "状态：1现行 2即将实施 3废止 4被替代")
    private Integer status;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "发布状态")
    private Integer publishStatus;
}
