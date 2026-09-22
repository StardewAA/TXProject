package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_supplier_qualification")
@KeySequence("erp_supplier_qualification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSupplierQualificationDO extends BaseDO {

    @TableId
    private Long id;
    private Long supplierId;
    private String qualificationName;
    private String qualificationNo;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private Boolean requiredFlag;
    private String remark;
}
