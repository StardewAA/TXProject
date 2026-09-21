package cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - MES 工具领用单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MesTmToolIssuePageReqVO extends PageParam {

    @Schema(description = "领用单编码")
    private String code;

    @Schema(description = "领用单名称")
    private String name;

    @Schema(description = "工位ID")
    private Long workstationId;

    @Schema(description = "状态")
    private Integer status;
}
