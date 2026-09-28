package cn.iocoder.txgy.module.erp.dal.dataobject.finance;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/** ERP 结算账户资金流水。 */
@TableName("erp_account_record")
@KeySequence("erp_account_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ErpAccountRecordDO extends BaseDO {

    @TableId
    private Long id;
    private Long accountId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private BigDecimal changeAmount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String remark;

}
