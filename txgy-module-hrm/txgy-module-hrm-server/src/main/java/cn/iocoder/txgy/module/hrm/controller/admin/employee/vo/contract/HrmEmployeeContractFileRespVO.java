package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 员工合同文件（生成稿/签署件） Response VO")
@Data
public class HrmEmployeeContractFileRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "合同编号")
    private Long contractId;
    @Schema(description = "员工编号")
    private Long employeeId;
    @Schema(description = "文件类型：1 系统生成稿，2 签署件")
    private Integer fileType;
    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "文件名")
    private String fileName;
    @Schema(description = "文件地址")
    private String fileUrl;
    @Schema(description = "模板编号")
    private Long templateId;
    @Schema(description = "签署记录编号")
    private Long signRecordId;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
