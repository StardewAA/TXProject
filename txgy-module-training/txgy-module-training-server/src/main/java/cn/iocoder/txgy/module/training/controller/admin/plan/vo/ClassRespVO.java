package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 培训班 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassRespVO extends ClassDO {

    @Schema(description = "绑定课程 ID 列表", example = "[1,2]")
    private List<Long> courseIds;

}
