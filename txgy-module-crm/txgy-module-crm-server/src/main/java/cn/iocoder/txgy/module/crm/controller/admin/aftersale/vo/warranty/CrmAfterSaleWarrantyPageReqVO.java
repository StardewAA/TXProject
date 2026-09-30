package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 售后质保档案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleWarrantyPageReqVO extends PageParam {

    /**
     * 到期类型 - 即将到期（待办「质保即将到期」使用）
     *
     * <p>实时查询口径：质保状态 = 质保中，且质保到期日 ≤ 今天 + 售后设置「质保到期提前提醒天数」
     */
    public static final Integer EXPIRY_TYPE_ABOUT_TO_EXPIRE = 1;

    @Schema(description = "质保编号", example = "WB20260907001")
    private String no;

    @Schema(description = "工程项目名称", example = "某储罐区防腐项目")
    private String projectName;

    @Schema(description = "来源合同编号", example = "15")
    private Long contractId;

    @Schema(description = "客户编号", example = "12")
    private Long customerId;

    @Schema(description = "质保状态：1=质保中 2=即将到期 3=已到期终止", example = "1")
    private Integer status;

    @Schema(description = "业务负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "售后责任人用户编号", example = "1")
    private Long serviceUserId;

    @Schema(description = "质保到期日期区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] warrantyEndDate;

    @Schema(description = "到期类型：1=即将到期（质保中，且质保到期日在提前提醒天数内）；为空表示全部", example = "1")
    private Integer expiryType;

}
