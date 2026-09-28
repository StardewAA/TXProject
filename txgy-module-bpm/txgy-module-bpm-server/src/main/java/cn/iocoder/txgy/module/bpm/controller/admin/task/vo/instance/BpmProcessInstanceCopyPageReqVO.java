package cn.iocoder.txgy.module.bpm.controller.admin.task.vo.instance;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 流程实例抄送的分页 Request VO")
@Data
public class BpmProcessInstanceCopyPageReqVO extends PageParam {

    @Schema(description = "流程名称", example = "OPENLAB BS")
    private String processInstanceName;

    @Schema(description = "流程定义标识集合，逗号分隔", example = "wms_receipt_approval,wms_shipment_approval")
    private String processDefinitionKeys;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
