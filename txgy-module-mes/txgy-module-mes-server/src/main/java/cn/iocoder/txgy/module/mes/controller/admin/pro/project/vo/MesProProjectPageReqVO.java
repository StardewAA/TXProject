package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - MES 项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MesProProjectPageReqVO extends PageParam {

    @Schema(description = "项目编码", example = "PRJ20260902001")
    private String code;

    @Schema(description = "项目名称", example = "某防腐保温工程")
    private String name;

    @Schema(description = "项目状态", example = "0")
    private Integer status;

    @Schema(description = "项目来源类型", example = "1")
    private Integer sourceType;

}
