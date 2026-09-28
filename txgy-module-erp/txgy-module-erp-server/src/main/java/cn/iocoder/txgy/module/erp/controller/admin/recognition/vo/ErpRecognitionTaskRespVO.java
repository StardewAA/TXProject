package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP 图片识别任务 Response VO")
@Data
public class ErpRecognitionTaskRespVO {

    private Long id;
    private String bizType;
    private String status;
    private String sourceFileUrl;
    private String documentType;
    private String providerType;
    private BigDecimal finalConfidence;
    private String errorMessage;
    private Integer resultVersion;
    private Long importedTargetId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String reviewer;
    private LocalDateTime createTime;
    private ErpRecognitionDocument document;
    private ErpRecognitionValidation validation;

}
