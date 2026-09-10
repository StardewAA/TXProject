package cn.iocoder.txgy.module.wms.dal.dataobject.integration;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("wms_integration_request")
@KeySequence("wms_integration_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WmsIntegrationRequestDO extends BaseDO {

    @TableId
    private Long id;
    private String requestId;
    private String operationType;
    private String payloadDigest;
    private String aggregateType;
    private String aggregateNo;
    private String processingStatus;
    private Long processedEventVersion;
    private Integer resultCode;
    private String responsePayload;
    private String failureReason;
}
