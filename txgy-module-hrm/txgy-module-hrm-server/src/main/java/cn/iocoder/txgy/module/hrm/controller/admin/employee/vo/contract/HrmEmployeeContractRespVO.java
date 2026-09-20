package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - HRM 员工合同 Response VO")
@Data
public class HrmEmployeeContractRespVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long employeeId;

    @Schema(description = "合同编号", example = "HT-2026-001")
    private String no;

    @Schema(description = "合同模板编号", example = "1")
    private Long templateId;

    @Schema(description = "来源 Offer 编号", example = "2")
    private Long sourceOfferId;

    @Schema(description = "工作内容")
    private String workContent;

    @Schema(description = "工作地点")
    private String workLocation;

    @Schema(description = "工作任务")
    private String workTask;

    @Schema(description = "计薪方式：1 月薪，2 日薪，3 计件", example = "1")
    private Integer payMode;

    @Schema(description = "计薪金额/单价", example = "8000.00")
    private java.math.BigDecimal payAmount;

    @Schema(description = "计薪单位", example = "元/月")
    private String payUnit;

    @Schema(description = "每月发薪日", example = "15")
    private Integer payDay;

    @Schema(description = "商业保险说明")
    private String commercialInsurance;

    @Schema(description = "考勤方式")
    private String attendanceMode;

    @Schema(description = "合同类型", example = "1")
    private Integer type;

    @Schema(description = "合同开始日期")
    private LocalDateTime startTime;

    @Schema(description = "合同结束日期")
    private LocalDateTime endTime;

    @Schema(description = "期限", example = "36")
    private Integer term;

    @Schema(description = "合同状态", example = "1")
    private Integer status;

    @Schema(description = "签约公司")
    private String signCompany;

    @Schema(description = "合同签订日期")
    private LocalDateTime signTime;

    @Schema(description = "签署状态：0 待签，1 已签，2 已作废；无签署记录时为空", example = "1")
    private Integer signStatus;

    @Schema(description = "签署状态名称", example = "已签")
    private String signStatusName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否到期提醒", example = "true")
    private Boolean expireRemind;

    @Schema(description = "附件地址数组")
    private List<String> fileUrls;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
