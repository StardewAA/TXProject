package cn.iocoder.txgy.module.hrm.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 合同签署记录 Response VO")
@Data
public class HrmContractSignRecordRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "合同编号", example = "8")
    private Long contractId;

    @Schema(description = "员工编号", example = "129")
    private Long employeeId;

    @Schema(description = "员工姓名", example = "王建国")
    private String employeeName;

    @Schema(description = "员工工号", example = "QC000009")
    private String jobNumber;

    @Schema(description = "合同编号（冗余）", example = "HT-OFF-8b86fd5002434913")
    private String contractNo;

    @Schema(description = "签署用工主体编号")
    private Long signEmployerId;

    @Schema(description = "签署用工主体名称")
    private String signEmployerName;

    @Schema(description = "签署人")
    private String signPerson;

    @Schema(description = "签署方式：1 线下签署，2 电子签章")
    private Integer signWay;

    @Schema(description = "签署时间")
    private LocalDateTime signTime;

    @Schema(description = "签署件附件地址列表")
    private List<String> fileUrls;

    @Schema(description = "状态：0 待签，1 已签，2 已作废")
    private Integer status;

    @Schema(description = "催办次数")
    private Integer urgeCount;

    @Schema(description = "最后催办时间")
    private LocalDateTime lastUrgeTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
