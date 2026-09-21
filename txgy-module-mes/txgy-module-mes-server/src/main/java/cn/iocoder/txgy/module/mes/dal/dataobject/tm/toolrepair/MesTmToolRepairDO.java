package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 工具维修单 DO
 */
@TableName("mes_tm_tool_repair")
@KeySequence("mes_tm_tool_repair_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolRepairDO extends BaseDO {

    @TableId
    private Long id;
    /** 维修单编码 */
    private String code;
    /** 维修单名称 */
    private String name;
    /** 维修日期 */
    private LocalDateTime repairDate;
    /** 完成日期 */
    private LocalDateTime finishDate;
    /** 状态 */
    private Integer status;
    /** 维修结果 */
    private String repairResult;
    /** 备注 */
    private String remark;
}
