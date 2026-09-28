package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** ERP 采购合同通用条款模板。 */
@TableName("erp_purchase_contract_template")
@KeySequence("erp_purchase_contract_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ErpPurchaseContractTemplateDO extends BaseDO {
    @TableId
    private Long id;
    private String name;
    private String version;
    private String content;
    private Boolean defaultFlag;
    private Integer status;
    private String remark;
}
