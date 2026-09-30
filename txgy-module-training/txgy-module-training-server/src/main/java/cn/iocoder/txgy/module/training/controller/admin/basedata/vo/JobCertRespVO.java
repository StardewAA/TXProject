package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobCertDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 工种资格证 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobCertRespVO extends JobCertDO {
}
