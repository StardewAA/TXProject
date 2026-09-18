package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolIssueStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 工具领用单 DO
 */
@TableName("mes_tm_tool_issue")
@KeySequence("mes_tm_tool_issue_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolIssueDO extends BaseDO {

    @TableId
    private Long id;
    /** 领用单编码 */
    private String code;
    /** 领用单名称 */
    private String name;
    /** 工位ID */
    private Long workstationId;
    /** 领用日期 */
    private LocalDateTime issueDate;
    /** 状态 */
    private Integer status;
    /** 备注 */
    private String remark;
}
