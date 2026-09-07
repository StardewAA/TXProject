package cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 安全环保检测-污染/危废暂存台账 DO
 *
 * 人工复核判"有污染(POLLUTED)"的判定记录自动登记入台账（A2），
 * 状态：暂存(STORED)/处置中(PROCESSING)/已回用(REUSED)/已排放(DISCHARGED)/已处置(DISPOSED)/已解除(CLEARED)。
 * 终态(已回用/已排放/已处置) = 人工处置闭环；已解除(CLEARED) = 系统在"批次后续复核为无污染"时自动关闭的历史误判行。
 * 台账行是该批次的唯一管控权威；批次污染戳(pollution_*)只是"该批是否仍有未闭环台账行"的投影缓存。
 *
 * 与 mes_set_pollution_check 一对一（uk source_check_id）：一条判定记录至多一条台账。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pollution_ledger")
@KeySequence("mes_pollution_ledger_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesPollutionLedgerDO extends BaseDO {

    /** 台账状态：暂存 */
    public static final String STATUS_STORED = "STORED";
    /** 台账状态：处置中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 台账状态：已回用 */
    public static final String STATUS_REUSED = "REUSED";
    /** 台账状态：已排放 */
    public static final String STATUS_DISCHARGED = "DISCHARGED";
    /** 台账状态：已处置 */
    public static final String STATUS_DISPOSED = "DISPOSED";
    /** 台账状态：已解除（批次后续复核为无污染时，系统自动关闭历史"有污染"台账行） */
    public static final String STATUS_CLEARED = "CLEARED";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 来源判定记录ID
     *
     * 关联 {@link MesSetPollutionCheckDO#getId()}
     */
    private Long sourceCheckId;
    /**
     * 来源判定记录编号（PC-...）
     */
    private String sourceRecordNo;
    /**
     * 环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)
     */
    private String stage;
    /**
     * 关联单号
     */
    private String bizNo;
    /**
     * 批次号
     */
    private String batchNo;
    /**
     * 物料/产品编码
     */
    private String itemCode;
    /**
     * 物料/产品名称
     */
    private String itemName;
    /**
     * 规格
     */
    private String itemSpec;
    /**
     * 处置方式
     */
    private String disposition;
    /**
     * 最终存储方法
     */
    private String storageMethod;
    /**
     * 去向/库位
     */
    private String location;
    /**
     * 是否标记
     */
    private Boolean marked;
    /**
     * 台账状态：STORED(暂存)/PROCESSING(处置中)/REUSED(已回用)/DISCHARGED(已排放)/DISPOSED(已处置)/CLEARED(已解除)
     */
    private String status;
    /**
     * 最近流转人
     */
    private String statusBy;
    /**
     * 最近流转时间
     */
    private LocalDateTime statusTime;
    /**
     * 备注
     */
    private String remark;

}
