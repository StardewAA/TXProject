package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.blacklist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 员工黑名单 Response VO")
@Data
public class HrmEmployeeBlacklistRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;
    @Schema(description = "员工编号", example = "1024")
    private Long employeeId;
    @Schema(description = "员工工号", example = "QC000012")
    private String jobNumber;
    @Schema(description = "姓名", example = "李四")
    private String name;
    @Schema(description = "手机号", example = "13800000000")
    private String mobile;
    @Schema(description = "身份证号")
    private String idCardNo;
    @Schema(description = "加入原因")
    private String reason;
    @Schema(description = "来源：1 HRM 手工登记，2 现场上报，3 其他")
    private Integer source;
    @Schema(description = "来源名称")
    private String sourceName;
    @Schema(description = "加入时间")
    private LocalDateTime joinTime;
    @Schema(description = "解除时间")
    private LocalDateTime releaseTime;
    @Schema(description = "是否永久")
    private Boolean permanentFlag;
    @Schema(description = "状态：1 生效，2 已解除")
    private Integer status;
    @Schema(description = "状态名称")
    private String statusName;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
