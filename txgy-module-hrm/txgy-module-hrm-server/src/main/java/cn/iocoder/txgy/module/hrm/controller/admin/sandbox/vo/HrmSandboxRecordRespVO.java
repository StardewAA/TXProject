package cn.iocoder.txgy.module.hrm.controller.admin.sandbox.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - HRM 沙箱数据登记 Response VO（I-025）
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 沙箱数据登记 Response VO")
@Data
public class HrmSandboxRecordRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "业务类型：post / leave")
    private String bizType;

    @Schema(description = "业务主键")
    private Long bizId;

    @Schema(description = "业务名称")
    private String bizName;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "登记时间")
    private LocalDateTime createTime;

}
