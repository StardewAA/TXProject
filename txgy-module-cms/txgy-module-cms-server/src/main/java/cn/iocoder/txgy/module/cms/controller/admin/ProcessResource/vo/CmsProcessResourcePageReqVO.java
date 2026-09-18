package cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 工艺工法与案例库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CmsProcessResourcePageReqVO extends PageParam {

    @Schema(description = "资源名称")
    private String title;
    @Schema(description = "作者")
    private String author;
    @Schema(description = "类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案")
    private Integer resourceType;
    @Schema(description = "难度：1入门 2初级 3中级 4高级 5专家")
    private Integer difficulty;
    @Schema(description = "是否免费：0付费 1免费")
    private Integer isFree;
    @Schema(description = "审核状态")
    private Integer auditStatus;
    @Schema(description = "发布状态")
    private Integer publishStatus;
}
