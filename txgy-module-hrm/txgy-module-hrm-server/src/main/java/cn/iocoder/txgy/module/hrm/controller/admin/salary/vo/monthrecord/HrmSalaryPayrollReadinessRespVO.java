package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - HRM 薪资核算准备 Response VO")
@Data
public class HrmSalaryPayrollReadinessRespVO {

    @Schema(description = "工资表编号")
    private Long monthRecordId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "年份")
    private Integer year;

    @Schema(description = "月份")
    private Integer month;

    @Schema(description = "开始日期")
    private LocalDateTime startTime;

    @Schema(description = "结束日期")
    private LocalDateTime endTime;

    @Schema(description = "社保年月")
    private String socialSecurityYearMonth;

    @Schema(description = "计薪员工人数")
    private Long payrollEmployeeCount;

    @Schema(description = "已定薪员工人数")
    private Long salaryEmployeeCount;

    @Schema(description = "未定薪员工人数")
    private Long noSalaryEmployeeCount;

    @Schema(description = "未分配薪资组员工人数")
    private Long noSalaryGroupEmployeeCount;

    @Schema(description = "异动员工人数")
    private Long changeEmployeeCount;

    @Schema(description = "变更类型数量映射")
    private Map<Integer, Long> changeTypeCountMap;

    @Schema(description = "社保数据缺失员工人数（当月无社保月记录，个人社保/公积金会按 0 核算）")
    private Long insuranceMissingEmployeeCount;

    @Schema(description = "当月无考勤数据员工人数（出勤天数为 0 且无迟到/缺勤记录，考勤扣款会按 0 核算）")
    private Long attendanceMissingEmployeeCount;

    @Schema(description = "启用但未配置单位的计薪项数量（影响录入时自动带出单位）")
    private Long salaryOptionUnitMissingCount;

    @Schema(description = "未定薪员工列表")
    private List<Employee> noSalaryEmployees;
    @Schema(description = "未分配薪资组员工列表")
    private List<Employee> noSalaryGroupEmployees;
    @Schema(description = "社保数据缺失员工列表")
    private List<Employee> insuranceMissingEmployees;
    @Schema(description = "当月无考勤数据员工列表")
    private List<Employee> attendanceMissingEmployees;
    @Schema(description = "启用但未配置单位的计薪项名称")
    private List<String> salaryOptionUnitMissingNames;

    @Schema(description = "管理后台 - HRM 薪资核算准备员工 Response VO")
    @Data
    public static class Employee {

        @Schema(description = "员工编号")
        private Long employeeId;

        @Schema(description = "员工姓名")
        private String employeeName;

        @Schema(description = "工号")
        private String jobNumber;

        @Schema(description = "部门编号")
        private Long deptId;

        @Schema(description = "部门名称")
        private String deptName;

        @Schema(description = "职位名称")
        private String postName;

        @Schema(description = "入职状态")
        private Integer entryStatus;

        @Schema(description = "员工状态")
        private Integer status;

        @Schema(description = "入职时间")
        private LocalDateTime entryTime;

    }

}
