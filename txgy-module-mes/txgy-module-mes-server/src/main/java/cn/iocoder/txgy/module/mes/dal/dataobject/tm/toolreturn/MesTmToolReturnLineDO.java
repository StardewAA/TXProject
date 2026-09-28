package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MES 工具归还单行 DO
 */
@TableName("mes_tm_tool_return_line")
@KeySequence("mes_tm_tool_return_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolReturnLineDO extends BaseDO {

    @TableId
    private Long id;
    /** 归还单ID */
    private Long returnId;
    /** 工具ID */
    private Long toolId;
    /** 工具编码 */
    private String toolCode;
    /** 工具名称 */
    private String toolName;
    /** 归还数量 */
    private Integer quantity;
    /** 备注 */
    private String remark;
}
