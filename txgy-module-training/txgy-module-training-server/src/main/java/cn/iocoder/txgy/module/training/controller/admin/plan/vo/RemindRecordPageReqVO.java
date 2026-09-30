package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 督促记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RemindRecordPageReqVO extends PageParam {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "发送状态：0成功 1失败", example = "0")
    private Integer status;

    @Schema(description = "学员姓名（模糊）", example = "张")
    private String userName;

}
