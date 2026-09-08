package cn.iocoder.txgy.module.mes.service.pollution;

import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;

/**
 * MES 环保污染管控服务（P2「真落地管控」心脏）
 *
 * 污染判定的"写透"与"门禁/清理"统一在这里，供判定复核、仓储(领用/出库)与台账侧单向调用。
 * 只依赖 Mapper（判定/批次/台账），不依赖其它 Service，避免 bean 环。
 *
 * 语义约定（台账行 = 权威，批次戳 = 投影缓存）：
 *  - 台账行由"复核=有污染(POLLUTED)"按 source_check_id 1:1 登记，是某批污染管控的权威事实。
 *  - 批次污染戳 pollution_* 只是"该批是否仍有未闭环(STORED/PROCESSING)台账行"的投影：
 *    有未闭环行 → POLLUTED（去向/标记/来源取最早开放行）；全部闭环/从未登记 → 四列全空(NULL)。
 *  - "复核=无污染(CLEAN)" → 关闭该批历史未闭环台账行为 CLEARED(已解除) 后整体重投影清戳；
 *    台账人工处置到终态(已回用/已排放/已处置) → 该行闭环，按剩余开放行重投影（不会误清同批其它开放行）。
 *  - 门禁只认批次戳：仅 POLLUTED 拦领用；POLLUTED 或 marked 拦出库；NULL/无戳均放行。
 *
 * @author OPENLAB BS
 */
public interface MesPollutionControlService {

    /** 环节：采购入库 */
    String STAGE_PURCHASE_INBOUND = "PURCHASE_INBOUND";
    /** 环节：生产领用 */
    String STAGE_MATERIAL_ISSUE = "MATERIAL_ISSUE";
    /** 环节：中间废弃物 */
    String STAGE_WASTE_INTERMEDIATE = "WASTE_INTERMEDIATE";
    /** 环节：成品 */
    String STAGE_FINISHED_PRODUCT = "FINISHED_PRODUCT";

    /** 人工复核结果：无污染 */
    String REVIEW_CLEAN = "CLEAN";
    /** 人工复核结果：有污染 */
    String REVIEW_POLLUTED = "POLLUTED";

    /**
     * 复核落定后的写透效果（A1+A2，须在事务内调用，抛错即回滚复核）：
     *  1) 校验：判"有污染"必须填去向(去向/库位)；非中间废弃物环节还必须关联真实批次（缺失/查不到 → 抛错）；
     *  2) A2 台账登记：复核=有污染 → 写 mes_pollution_ledger(STORED)，同一 source_check_id 已登记则不重复；
     *  3) A1 批次戳投影：无污染 → 先关闭该批未闭环台账行为 CLEARED(已解除)，再按剩余开放行重刷批次戳。
     * 中间废弃物环节无批次主数据：只登记台账、不投影批次戳。
     *
     * @param reviewed 已复核的完整判定记录（含 reviewResult/location/marked/recordNo 等落库后回查的整行）
     */
    void applyReviewEffect(MesSetPollutionCheckDO reviewed);

    /**
     * A3 生产领用门禁：批次戳 POLLUTED（即该批仍有未闭环"有污染"台账行）→ 抛 MES_POLLUTION_ISSUE_BLOCKED 拒绝领用。
     *
     * @param batchNo 批次号（null/空/批次不存在 视为放行）
     */
    void assertIssueAllowed(String batchNo);

    /**
     * A3 成品(销售)出库门禁：批次戳 POLLUTED 或 marked → 抛 MES_POLLUTION_OUTBOUND_BLOCKED 禁止出库。
     *
     * @param batchNo 批次号（null/空/批次不存在 视为放行）
     */
    void assertOutboundAllowed(String batchNo);

    /**
     * 按"未闭环台账行"重投影批次戳（A1/A2 心脏）：
     * 该批仍有未闭环(STORED/PROCESSING)台账行 → 戳 POLLUTED（去向/标记/来源取最早开放行）；
     * 全部闭环/从未登记 → 四列清空(NULL)，批次恢复可正常流转。
     * 台账处置到终态、"无污染"复核后均调用本方法重刷，同批存在多条开放台账时不会被单行闭环误清。
     *
     * @param batchNo 批次号（空/无主数据 视为无需投影，安全跳过）
     */
    void refreshBatchStamp(String batchNo);

    /**
     * A4 级联清理：删除某单据(stage+bizNo)下仍"待复核"(reviewResult IS NULL)的判定记录。
     *
     * @return 清理条数
     */
    int purgePendingByBizNo(String stage, String bizNo);

    /**
     * A4 级联清理(行删除)：删除某单据某行(stage+bizNo+itemCode，可带 batchNo)下仍"待复核"的判定记录。
     *
     * @return 清理条数
     */
    int purgePendingByBizNoItem(String stage, String bizNo, String itemCode, String batchNo);

}
