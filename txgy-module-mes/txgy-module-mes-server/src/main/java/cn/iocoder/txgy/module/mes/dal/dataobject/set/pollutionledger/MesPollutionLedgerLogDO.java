package cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 安全环保检测-污染/危废暂存台账-流转历史 DO
 *
 * 台账每步流转一行（只增不改）：登记 STORED、人工流转(PROCESSING/终态)、无污染复核自动解除(CLEARED)，
 * 记录 原状态→新状态、操作人、时间、说明；台账行上的 status_by/status_time 只是"最近一次"，完整链看本表。
 * 与 mes_pollution_ledger 一对多（ledger_id）。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pollution_ledger_log")
@KeySequence("mes_pollution_ledger_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesPollutionLedgerLogDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 台账ID，关联 {@link MesPollutionLedgerDO#getId()}
     */
    private Long ledgerId;
    /**
     * 来源判定记录编号(PC-...)
     */
    private String sourceRecordNo;
    /**
     * 原状态（空=初始登记）
     */
    private String fromStatus;
    /**
     * 新状态：STORED/PROCESSING/REUSED/DISCHARGED/DISPOSED/CLEARED
     */
    private String toStatus;
    /**
     * 操作人（自动流转填复核人）
     */
    private String operator;
    /**
     * 流转时间
     */
    private LocalDateTime opTime;
    /**
     * 流转说明/备注
     */
    private String remark;

}
