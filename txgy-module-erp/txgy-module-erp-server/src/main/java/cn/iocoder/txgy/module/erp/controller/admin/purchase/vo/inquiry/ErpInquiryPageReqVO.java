package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpInquiryPageReqVO extends PageParam {
    private String no;
    private String title;
    private Integer status;
    private Integer businessStatus;
    private Integer awardStatus;
    private Integer inquiryMode;
    /** 仅查询已进入定标阶段的询价单 */
    private Boolean awardOnly;
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] deadline;
}
