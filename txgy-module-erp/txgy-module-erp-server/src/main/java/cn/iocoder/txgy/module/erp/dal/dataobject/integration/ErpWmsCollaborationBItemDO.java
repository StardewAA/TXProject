package cn.iocoder.txgy.module.erp.dal.dataobject.integration;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("erp_wms_collaboration_b_item")
@KeySequence("erp_wms_collaboration_b_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpWmsCollaborationBItemDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long collaborationId;
    private Integer lineNo;
    private String materialCode;
    private String skuCode;
    private String materialNameSnapshot;
    private String specificationSnapshot;
    private String unitCode;
    private String unitNameSnapshot;
    private BigDecimal shortageQuantity;
}
