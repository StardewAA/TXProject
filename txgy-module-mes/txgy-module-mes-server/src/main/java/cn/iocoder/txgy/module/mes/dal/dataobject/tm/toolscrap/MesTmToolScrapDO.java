package cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 工具报废单 DO
 */
@TableName("mes_tm_tool_scrap")
@KeySequence("mes_tm_tool_scrap_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesTmToolScrapDO extends BaseDO {

    @TableId
    private Long id;
    /** 报废单编码 */
    private String code;
    /** 报废单名称 */
    private String name;
    /** 报废日期 */
    private LocalDateTime scrapDate;
    /** 状态 */
    private Integer status;
    /** 报废原因 */
    private String scrapReason;
    /** 备注 */
    private String remark;
}
