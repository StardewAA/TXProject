package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_supplier_credit_status")
@KeySequence("erp_supplier_credit_status_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSupplierCreditStatusDO extends BaseDO {

    @TableId
    private Long id;
    private Long supplierId;
    private String authStatus;
    private String qualificationStatus;
    private String riskLevel;
    private String purchaseLimitStatus;
    private String freezeReason;
    private Long snapshotVersion;
    private LocalDateTime lastSyncTime;
}
