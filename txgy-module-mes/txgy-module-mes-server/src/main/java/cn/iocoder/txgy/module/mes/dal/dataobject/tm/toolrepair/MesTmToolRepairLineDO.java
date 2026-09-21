package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MES 工具维修单行 DO
 */
@TableName("mes_tm_tool_repair_line")
@KeySequence("mes_tm_tool_repair_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolRepairLineDO extends BaseDO {

    @TableId
    private Long id;
    /** 维修单ID */
    private Long repairId;
    /** 工具ID */
    private Long toolId;
    /** 工具编码 */
    private String toolCode;
    /** 工具名称 */
    private String toolName;
    /** 维修数量 */
    private Integer quantity;
    /** 维修结果 */
    private String repairResult;
    /** 备注 */
    private String remark;
}
