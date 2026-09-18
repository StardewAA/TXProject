package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MES 工具领用单行 DO
 */
@TableName("mes_tm_tool_issue_line")
@KeySequence("mes_tm_tool_issue_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolIssueLineDO extends BaseDO {

    @TableId
    private Long id;
    /** 领用单ID */
    private Long issueId;
    /** 工具ID */
    private Long toolId;
    /** 工具编码 */
    private String toolCode;
    /** 工具名称 */
    private String toolName;
    /** 领用数量 */
    private Integer quantity;
    /** 备注 */
    private String remark;
}
