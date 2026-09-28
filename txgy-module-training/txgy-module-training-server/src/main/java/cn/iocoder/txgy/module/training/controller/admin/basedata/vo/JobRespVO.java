package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 岗位工种 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRespVO extends JobDO {

    @Schema(description = "最高成长等级名称（关联成长等级字典翻译）", example = "高级技师")
    private String maxLevelName;

    @Schema(description = "资格证数量", example = "3")
    private Integer certCount;

    @Schema(description = "学习地图课程数", example = "37")
    private Integer mapCourseCount;

}
