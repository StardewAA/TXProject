package cn.iocoder.txgy.module.erp.dal.dataobject.recognition;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 图片识别任务。
 */
@TableName("erp_recognition_task")
@KeySequence("erp_recognition_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpRecognitionTaskDO extends TenantBaseDO {

    @TableId
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

}
