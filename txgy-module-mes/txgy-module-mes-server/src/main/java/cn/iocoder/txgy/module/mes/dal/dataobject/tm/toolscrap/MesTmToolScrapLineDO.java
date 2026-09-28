package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MES 工具报废单行 DO
 */
@TableName("mes_tm_tool_scrap_line")
@KeySequence("mes_tm_tool_scrap_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolScrapLineDO extends BaseDO {

    @TableId
    private Long id;
    /** 报废单ID */
    private Long scrapId;
    /** 工具ID */
    private Long toolId;
    /** 工具编码 */
    private String toolCode;
    /** 工具名称 */
    private String toolName;
    /** 报废数量 */
    private Integer quantity;
    /** 报废原因 */
    private String scrapReason;
    /** 备注 */
    private String remark;
}
