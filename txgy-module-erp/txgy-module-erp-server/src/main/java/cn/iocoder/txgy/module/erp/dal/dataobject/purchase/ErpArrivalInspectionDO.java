package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** ERP 到货检验单 DO。 */
@TableName("erp_arrival_inspection")
@KeySequence("erp_arrival_inspection_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpArrivalInspectionDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long purchaseInId;
    private Long supplierId;
    /**
     * 检验时间
     */
    private LocalDateTime inspectTime;
    private Long inspectorId;
    /**
     * 检验方式：1 全检，2 抽检
     */
    private Integer inspectType;
    /**
     * 检验结论：1 合格，2 让步接收，3 退货
     */
    private Integer result;
    private String conclusion;
    private String origin;
    private Integer status;
    private String fileUrl;
    private String remark;
}
