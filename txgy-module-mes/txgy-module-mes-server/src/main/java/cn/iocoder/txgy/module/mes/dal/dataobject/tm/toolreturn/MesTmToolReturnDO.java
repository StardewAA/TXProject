package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 工具归还单 DO
 */
@TableName("mes_tm_tool_return")
@KeySequence("mes_tm_tool_return_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolReturnDO extends BaseDO {

    @TableId
    private Long id;
    /** 归还单编码 */
    private String code;
    /** 归还单名称 */
    private String name;
    /** 工位ID */
    private Long workstationId;
    /** 归还日期 */
    private LocalDateTime returnDate;
    /** 状态 */
    private Integer status;
    /** 备注 */
    private String remark;
}
