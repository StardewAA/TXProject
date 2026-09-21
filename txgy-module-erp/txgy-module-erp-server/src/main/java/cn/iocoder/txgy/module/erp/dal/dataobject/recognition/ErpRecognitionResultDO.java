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

/**
 * ERP 图片识别结果。原始响应与规范化结果分开保存，便于审计与重试。
 */
@TableName("erp_recognition_result")
@KeySequence("erp_recognition_result_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpRecognitionResultDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String rawResponse;
    private String normalizedJson;
    private String validationJson;
    private Integer resultVersion;

}
