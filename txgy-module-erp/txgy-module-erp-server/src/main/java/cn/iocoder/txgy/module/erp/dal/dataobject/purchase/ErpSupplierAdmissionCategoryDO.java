package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("erp_supplier_admission_category")
@KeySequence("erp_supplier_admission_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSupplierAdmissionCategoryDO extends BaseDO {

    @TableId
    private Long id;
    private Long supplierId;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private String remark;
}
