package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - ERP 图片识别任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpRecognitionTaskPageReqVO extends PageParam {

    private String bizType;
    private String status;
    private String keyword;

}
