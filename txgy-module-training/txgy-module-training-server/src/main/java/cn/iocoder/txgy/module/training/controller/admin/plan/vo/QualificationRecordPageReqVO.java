package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 上岗资格核查历史记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationRecordPageReqVO extends PageParam {

    @Schema(description = "姓名（模糊）", example = "张三")
    private String userName;

    @Schema(description = "手机号（模糊）", example = "138")
    private String mobile;

}
