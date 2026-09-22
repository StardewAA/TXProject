package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工资银行代发批次 Response VO
 *
 * <p>只做"数据准备"：把当月工资条的实发金额与员工工资卡信息组装成代发批次，
 * 具体银行的文件格式由 {@code BankPaymentFileGenerator} 适配层实现（当前默认通用 CSV）。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 工资银行代发批次 Response VO")
@Data
public class HrmSalaryPaymentBatchRespVO {

    @Schema(description = "年份", example = "2026")
    private Integer year;
    @Schema(description = "月份", example = "8")
    private Integer month;
    @Schema(description = "发薪人数", example = "20")
    private Integer employeeCount;
    @Schema(description = "代发总金额", example = "168300.00")
    private BigDecimal totalAmount;
    @Schema(description = "缺工资卡的人数（需先补卡才能代发）", example = "2")
    private Integer missingCardCount;
    @Schema(description = "代发明细")
    private List<PaymentItem> items;

    @Schema(description = "代发明细行")
    @Data
    public static class PaymentItem {
        @Schema(description = "员工编号", example = "131")
        private Long employeeId;
        @Schema(description = "员工姓名", example = "李保温")
        private String employeeName;
        @Schema(description = "工号", example = "QC000011")
        private String jobNumber;
        @Schema(description = "开户银行", example = "中国建设银行")
        private String bankName;
        @Schema(description = "开户支行", example = "西安高新支行")
        private String bankBranchName;
        @Schema(description = "银行卡号", example = "6217000010000000000")
        private String bankCardNumber;
        @Schema(description = "实发金额", example = "9860.00")
        private BigDecimal amount;
        @Schema(description = "是否可代发（有工资卡）", example = "true")
        private Boolean payable;
        @Schema(description = "不可代发原因", example = "未登记工资卡")
        private String unpayableReason;
    }

}
