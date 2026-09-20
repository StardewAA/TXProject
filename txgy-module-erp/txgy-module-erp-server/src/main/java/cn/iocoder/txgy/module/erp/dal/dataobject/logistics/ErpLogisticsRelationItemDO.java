package cn.iocoder.txgy.module.erp.dal.dataobject.logistics;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("erp_logistics_relation_item")
@KeySequence("erp_logistics_relation_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpLogisticsRelationItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long relationId;
    private Integer lineNo;
    private String sourceItemNo;
    private Long productId;
    private String materialCode;
    private String itemNameSnapshot;
    private String specificationSnapshot;
    private String unitNameSnapshot;
    private BigDecimal quantity;

}
