package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘职位类型状态 Request VO")
@Data
public class HrmRecruitPostTypeStatusReqVO {

    @Schema(description = "职位类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "职位类型编号不能为空")
    private Long id;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;
}
