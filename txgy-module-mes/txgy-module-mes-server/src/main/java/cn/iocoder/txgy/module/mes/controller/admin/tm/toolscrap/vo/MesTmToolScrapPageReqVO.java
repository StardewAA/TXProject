package cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - MES 工具报废单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MesTmToolScrapPageReqVO extends PageParam {

    @Schema(description = "报废单编码")
    private String code;

    @Schema(description = "报废单名称")
    private String name;

    @Schema(description = "状态")
    private Integer status;
}
