package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 培训班分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassPageReqVO extends PageParam {

    @Schema(description = "培训班名称（模糊）", example = "管道工")
    private String name;

    @Schema(description = "培训班编码（模糊）", example = "TB1")
    private String code;

    @Schema(description = "培训类型：1岗前 2复工 3技能 4质检 5安全 6特种 7学徒 8复合", example = "1")
    private Integer type;

    @Schema(description = "状态：0草稿 1进行中 2已完成 3已取消", example = "0")
    private Integer status;

    @Schema(description = "关联工程项目编号", example = "PROJ-2026-001")
    private String projectId;

}
