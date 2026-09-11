package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.course.GrowthLevelDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 成长等级 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class GrowthLevelRespVO extends GrowthLevelDO {
}
