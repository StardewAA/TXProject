package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.plan.RemindRecordDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 督促记录 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RemindRecordRespVO extends RemindRecordDO {
}
