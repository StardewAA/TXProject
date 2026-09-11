package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 售后回访记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleVisitRecordPageReqVO extends PageParam {

    @Schema(description = "关联回访任务编号", example = "1")
    private Long taskId;

    @Schema(description = "质保档案编号", example = "1")
    private Long warrantyId;

    @Schema(description = "客户编号", example = "12")
    private Long customerId;

    @Schema(description = "满意度：5=非常满意 4=满意 3=一般 2=不满意 1=非常不满意", example = "5")
    private Integer satisfactionLevel;

    @Schema(description = "回访时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] visitTime;

}
