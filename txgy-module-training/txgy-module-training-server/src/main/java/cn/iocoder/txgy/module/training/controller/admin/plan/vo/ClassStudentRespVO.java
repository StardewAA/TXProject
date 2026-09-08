package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 培训班学员 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassStudentRespVO extends ClassStudentDO {
}
