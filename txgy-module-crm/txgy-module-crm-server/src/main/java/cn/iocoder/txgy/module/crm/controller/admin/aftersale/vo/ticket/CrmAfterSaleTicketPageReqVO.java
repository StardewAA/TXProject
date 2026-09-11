package cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 售后问题工单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmAfterSaleTicketPageReqVO extends PageParam {

    @Schema(description = "工单编号", example = "AT2026090710200001")
    private String no;

    @Schema(description = "问题标题")
    private String title;

    @Schema(description = "客户编号", example = "12")
    private Long customerId;

    @Schema(description = "关联质保档案编号", example = "1")
    private Long warrantyId;

    @Schema(description = "工单来源：1=回访发现 2=客户报障", example = "1")
    private Integer source;

    @Schema(description = "优先级：1=高 2=中 3=低", example = "2")
    private Integer priority;

    @Schema(description = "工单状态：1=待处理 2=处理中 3=已完成 4=已关闭", example = "1")
    private Integer status;

    @Schema(description = "处理人用户编号", example = "1")
    private Long assigneeUserId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
