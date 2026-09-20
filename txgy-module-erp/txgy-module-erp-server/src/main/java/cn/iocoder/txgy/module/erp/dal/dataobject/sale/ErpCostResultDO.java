package cn.iocoder.txgy.module.erp.dal.dataobject.sale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 造价结果 DO。 */
@TableName("erp_cost_result")
@KeySequence("erp_cost_result_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpCostResultDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    private String name;
    private String projectCode;
    private Integer costVersion;
    private Integer status;
    private String processInstanceId;
    /**
     * 反审批原因
     */
    private String reverseApproveReason;
    private Integer businessStatus;
    private Integer sourceType;
    private String currency;
    private BigDecimal totalPrice;
    private Integer mappedCount;
    private Integer itemCount;
    private LocalDateTime receivedAt;
    private Integer convertedType;
    private Long convertedId;
    private String fileUrl;
    private String remark;
}
