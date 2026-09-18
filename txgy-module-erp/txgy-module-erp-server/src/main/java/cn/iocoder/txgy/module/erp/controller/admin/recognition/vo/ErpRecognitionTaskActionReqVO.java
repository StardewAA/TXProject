package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ErpRecognitionTaskActionReqVO {

    @NotNull(message = "任务编号不能为空")
    private Long taskId;
    @NotNull(message = "识别结果版本不能为空")
    private Integer resultVersion;

}
