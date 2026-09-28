package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.basedata.LearningMapDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 学习地图 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningMapRespVO extends LearningMapDO {
}
