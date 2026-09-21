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

import java.time.LocalDateTime;

/**
 * ERP 图片识别导入记录。
 */
@TableName("erp_recognition_import_record")
@KeySequence("erp_recognition_import_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpRecognitionImportRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String bizType;
    private Long targetId;
    private String targetNo;
    private String importStatus;
    private String idempotencyKey;
    private String confirmedJson;
    private String importError;
    private String confirmedBy;
    private LocalDateTime confirmedAt;

}
