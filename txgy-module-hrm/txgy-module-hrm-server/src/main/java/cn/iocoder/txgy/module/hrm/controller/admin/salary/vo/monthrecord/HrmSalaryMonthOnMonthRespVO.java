package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台 - HRM 工资表「环比上月」对比 Response VO（I-023）
 *
 * <p>用于工资表详情页的环比面板：汇总口径（应发/实发/个税/社保/公积金/人数）逐项对比上月，
 * 同时给出「薪资项级别」与「员工级别」的增减明细，便于 HR 快速定位本月变化来自哪里。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 工资表环比上月 Response VO")
@Data
@Accessors(chain = true)
public class HrmSalaryMonthOnMonthRespVO {

    @Schema(description = "本月工资表编号")
    private Long monthRecordId;

    @Schema(description = "本月工资表标题")
    private String title;

    @Schema(description = "本月年份")
    private Integer year;

    @Schema(description = "本月月份")
    private Integer month;

    @Schema(description = "是否存在上月工资表")
    private Boolean hasPrevious;

    @Schema(description = "上月工资表编号")
    private Long previousMonthRecordId;

    @Schema(description = "上月工资表标题")
    private String previousTitle;

    @Schema(description = "上年份")
    private Integer previousYear;

    @Schema(description = "上月月份")
    private Integer previousMonth;

    @Schema(description = "本月汇总")
    private Totals current;

    @Schema(description = "上月汇总")
    private Totals previous;

    @Schema(description = "薪资项级别环比（按变动绝对值倒序）")
    private List<OptionRow> optionRows;

    @Schema(description = "员工级别环比（本月或上月有工资记录的人）")
    private List<EmployeeRow> employeeRows;

    @Schema(description = "汇总口径")
    @Data
    @Accessors(chain = true)
    public static class Totals {

        @Schema(description = "发薪人数")
        private Long employeeCount;

        @Schema(description = "应发工资合计")
        private BigDecimal expectedPaySalary;

        @Schema(description = "实发工资合计")
        private BigDecimal realPaySalary;

        @Schema(description = "个人所得税合计")
        private BigDecimal personalTax;

        @Schema(description = "个人社保合计")
        private BigDecimal personalInsuranceAmount;

        @Schema(description = "个人公积金合计")
        private BigDecimal personalProvidentFundAmount;

    }

    @Schema(description = "薪资项环比")
    @Data
    @Accessors(chain = true)
    public static class OptionRow {

        @Schema(description = "薪资项编码")
        private Integer code;

        @Schema(description = "薪资项名称")
        private String name;

        @Schema(description = "本月值")
        private BigDecimal currentValue;

        @Schema(description = "上月值")
        private BigDecimal previousValue;

        @Schema(description = "本月 - 上月")
        private BigDecimal delta;

    }

    @Schema(description = "员工环比")
    @Data
    @Accessors(chain = true)
    public static class EmployeeRow {

        @Schema(description = "员工编号")
        private Long employeeId;

        @Schema(description = "员工姓名")
        private String employeeName;

        @Schema(description = "工号")
        private String jobNumber;

        @Schema(description = "部门名称")
        private String deptName;

        @Schema(description = "本月应发")
        private BigDecimal currentExpectedPaySalary;

        @Schema(description = "本月实发")
        private BigDecimal currentRealPaySalary;

        @Schema(description = "上月应发")
        private BigDecimal previousExpectedPaySalary;

        @Schema(description = "上月实发")
        private BigDecimal previousRealPaySalary;

        @Schema(description = "增减类型：NEW=本月新增、LEFT=本月无记录（上月有）、CHANGED=金额变动、SAME=金额一致")
        private String changeType;

    }

}
