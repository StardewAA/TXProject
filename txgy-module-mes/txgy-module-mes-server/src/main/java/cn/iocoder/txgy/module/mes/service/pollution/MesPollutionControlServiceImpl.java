package cn.iocoder.txgy.module.mes.service.pollution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerLogDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.batch.MesWmBatchDO;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutioncheck.MesSetPollutionCheckMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger.MesPollutionLedgerLogMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger.MesPollutionLedgerMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.wm.batch.MesWmBatchMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.MES_POLLUTION_ISSUE_BLOCKED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.MES_POLLUTION_OUTBOUND_BLOCKED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_BATCH_NOT_FOUND;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_BATCH_REQUIRED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_LOCATION_REQUIRED;

/**
 * MES 环保污染管控服务实现
 *
 * 台账行 = 该批污染管控的唯一权威；批次污染戳(pollution_*)只是"该批是否仍有未闭环台账行"的投影缓存。
 * "复核=有污染" → 登记台账(STORED) 并投影戳批次；"复核=无污染" → 关闭该批历史未闭环台账行(CLEARED) 后清戳。
 * 台账人工处置到终态(已回用/已排放/已处置) → 该行闭环，按剩余开放行重投影（refreshBatchStamp）。
 *
 * @author OPENLAB BS
 */
@Service
public class MesPollutionControlServiceImpl implements MesPollutionControlService {

    @Resource
    private MesSetPollutionCheckMapper pollutionCheckMapper;

    @Resource
    private MesWmBatchMapper batchMapper;

    @Resource
    private MesPollutionLedgerMapper ledgerMapper;

    @Resource
    private MesPollutionLedgerLogMapper ledgerLogMapper;

    @Override
    public void applyReviewEffect(MesSetPollutionCheckDO reviewed) {
        if (reviewed == null || reviewed.getReviewResult() == null) {
            return;
        }
        boolean polluted = REVIEW_POLLUTED.equals(reviewed.getReviewResult());
        boolean waste = STAGE_WASTE_INTERMEDIATE.equals(reviewed.getStage());
        String batchNo = reviewed.getBatchNo();
        // 1) 校验：中间废弃物无批次主数据，允许空批次；其余环节"有污染"必须落真实批次，否则抛错（同事务 → 复核整体回滚）
        MesWmBatchDO batch = StrUtil.isBlank(batchNo) ? null : batchMapper.selectByCode(batchNo);
        if (polluted) {
            if (StrUtil.isBlank(reviewed.getLocation())) {
                throw exception(SET_POLLUTION_CHECK_LOCATION_REQUIRED);
            }
            if (!waste && batch == null) {
                if (StrUtil.isBlank(batchNo)) {
                    throw exception(SET_POLLUTION_CHECK_BATCH_REQUIRED);
                }
                throw exception(SET_POLLUTION_CHECK_BATCH_NOT_FOUND, batchNo);
            }
        }
        // 2) A2 台账登记：仅"有污染"登记，同一判定记录(source_check_id)不重复
        if (polluted) {
            MesPollutionLedgerDO exist = ledgerMapper.selectBySourceCheckId(reviewed.getId());
            if (exist == null) {
                MesPollutionLedgerDO ledger = MesPollutionLedgerDO.builder()
                        .sourceCheckId(reviewed.getId())
                        .sourceRecordNo(reviewed.getRecordNo())
                        .stage(reviewed.getStage())
                        .bizNo(reviewed.getBizNo())
                        .batchNo(batchNo)
                        .itemCode(reviewed.getItemCode())
                        .itemName(reviewed.getItemName())
                        .itemSpec(reviewed.getItemSpec())
                        .disposition(reviewed.getDisposition())
                        .storageMethod(reviewed.getStorageMethod())
                        .location(reviewed.getLocation())
                        .marked(Boolean.TRUE.equals(reviewed.getMarked()))
                        .status(MesPollutionLedgerDO.STATUS_STORED)
                        .statusBy(reviewed.getReviewBy())
                        .statusTime(reviewed.getReviewTime())
                        .remark(reviewed.getRemark())
                        .build();
                ledgerMapper.insert(ledger);
                // 台账流转历史留痕：登记 STORED
                ledgerLogMapper.insert(MesPollutionLedgerLogDO.builder()
                        .ledgerId(ledger.getId())
                        .sourceRecordNo(ledger.getSourceRecordNo())
                        .toStatus(MesPollutionLedgerDO.STATUS_STORED)
                        .operator(ledger.getStatusBy())
                        .opTime(ledger.getStatusTime())
                        .remark("复核判为有污染，自动登记暂存台账")
                        .build());
            }
        } else if (batch != null) {
            // 3) "无污染"复核 = 解除该批此前误判的未闭环台账行（历史人工处置闭环的保留留痕），随后整体重投影清戳
            closeOpenLedgerByBatch(batchNo, reviewed);
        }
        // 4) A1 批次戳 = 台账投影重刷（仅能落地真实批次的环节；中间废弃物无主数据 → 只登记台账不投影）
        if (!waste && batch != null) {
            refreshBatchStamp(batchNo);
        }
    }

    @Override
    public void assertIssueAllowed(String batchNo) {
        if (StrUtil.isBlank(batchNo)) {
            return;
        }
        MesWmBatchDO batch = batchMapper.selectByCode(batchNo);
        if (batch != null && REVIEW_POLLUTED.equals(batch.getPollutionStatus())) {
            throw exception(MES_POLLUTION_ISSUE_BLOCKED, batchNo, safeLocation(batch.getPollutionLocation()));
        }
    }

    @Override
    public void assertOutboundAllowed(String batchNo) {
        if (StrUtil.isBlank(batchNo)) {
            return;
        }
        MesWmBatchDO batch = batchMapper.selectByCode(batchNo);
        if (batch != null) {
            boolean blocked = REVIEW_POLLUTED.equals(batch.getPollutionStatus())
                    || Boolean.TRUE.equals(batch.getPollutionMarked());
            if (blocked) {
                throw exception(MES_POLLUTION_OUTBOUND_BLOCKED, batchNo, safeLocation(batch.getPollutionLocation()));
            }
        }
    }

    @Override
    public void refreshBatchStamp(String batchNo) {
        if (StrUtil.isBlank(batchNo)) {
            return;
        }
        MesWmBatchDO batch = batchMapper.selectByCode(batchNo);
        if (batch == null) {
            return;
        }
        List<MesPollutionLedgerDO> open = ledgerMapper.selectOpenByBatchNo(batchNo);
        if (open.isEmpty()) {
            // 该批已无未闭环"有污染"台账行 → 清戳，恢复可正常流转
            patchBatchStamp(batch.getId(), null, null, null, null);
        } else {
            // 仍开放 → 投影最早一条开放行的去向/标记/来源判定
            MesPollutionLedgerDO first = open.get(0);
            patchBatchStamp(batch.getId(),
                    REVIEW_POLLUTED,
                    first.getLocation(),
                    Boolean.TRUE.equals(first.getMarked()),
                    first.getSourceRecordNo());
        }
    }

    @Override
    public int purgePendingByBizNo(String stage, String bizNo) {
        if (StrUtil.isBlank(bizNo)) {
            return 0;
        }
        return pollutionCheckMapper.delete(new LambdaQueryWrapperX<MesSetPollutionCheckDO>()
                .eq(MesSetPollutionCheckDO::getStage, stage)
                .eq(MesSetPollutionCheckDO::getBizNo, bizNo)
                .isNull(MesSetPollutionCheckDO::getReviewResult));
    }

    @Override
    public int purgePendingByBizNoItem(String stage, String bizNo, String itemCode, String batchNo) {
        if (StrUtil.isBlank(bizNo)) {
            return 0;
        }
        // 逐行 eq：链式 eq() 返回基础类型，随后无法再调 LambdaQueryWrapperX.eqIfPresent
        LambdaQueryWrapperX<MesSetPollutionCheckDO> query = new LambdaQueryWrapperX<>();
        query.eq(MesSetPollutionCheckDO::getStage, stage);
        query.eq(MesSetPollutionCheckDO::getBizNo, bizNo);
        query.isNull(MesSetPollutionCheckDO::getReviewResult);
        if (StrUtil.isNotBlank(itemCode)) {
            query.eq(MesSetPollutionCheckDO::getItemCode, itemCode);
        }
        if (StrUtil.isNotBlank(batchNo)) {
            query.eq(MesSetPollutionCheckDO::getBatchNo, batchNo);
        }
        return pollutionCheckMapper.delete(query);
    }

    // ==================== 私有方法 ====================

    /**
     * "无污染"复核把该批所有未闭环(暂存/处置中)台账行置为 CLEARED(已解除)，留痕解除人/时间并追加备注；
     * 历史已人工处置到终态的行保留不动（处置记录本身不可逆）。
     */
    private void closeOpenLedgerByBatch(String batchNo, MesSetPollutionCheckDO reviewed) {
        List<MesPollutionLedgerDO> open = ledgerMapper.selectOpenByBatchNo(batchNo);
        if (open.isEmpty()) {
            return;
        }
        String reason = StrUtil.format("批次复核无污染，自动解除(源判定: {})", reviewed.getRecordNo());
        for (MesPollutionLedgerDO row : open) {
            String remark = StrUtil.blankToDefault(row.getRemark(), "");
            remark = remark.isEmpty() ? reason : remark + "；" + reason;
            ledgerMapper.update(null, new LambdaUpdateWrapper<MesPollutionLedgerDO>()
                    .eq(MesPollutionLedgerDO::getId, row.getId())
                    .set(MesPollutionLedgerDO::getStatus, MesPollutionLedgerDO.STATUS_CLEARED)
                    .set(MesPollutionLedgerDO::getStatusBy, reviewed.getReviewBy())
                    .set(MesPollutionLedgerDO::getStatusTime, reviewed.getReviewTime())
                    .set(MesPollutionLedgerDO::getRemark, remark));
            // 台账流转历史留痕：无污染复核自动解除 → CLEARED
            ledgerLogMapper.insert(MesPollutionLedgerLogDO.builder()
                    .ledgerId(row.getId())
                    .sourceRecordNo(row.getSourceRecordNo())
                    .fromStatus(row.getStatus())
                    .toStatus(MesPollutionLedgerDO.STATUS_CLEARED)
                    .operator(reviewed.getReviewBy())
                    .opTime(reviewed.getReviewTime())
                    .remark(reason)
                    .build());
        }
    }

    private String safeLocation(String location) {
        return StrUtil.blankToDefault(location, "-");
    }

    /**
     * updateById 默认忽略 null 字段，清戳/写 null 需用 update wrapper 显式 set NULL。
     */
    private void patchBatchStamp(Long batchId, String status, String location, Boolean marked, String srcRecord) {
        batchMapper.update(null, new LambdaUpdateWrapper<MesWmBatchDO>()
                .eq(MesWmBatchDO::getId, batchId)
                .set(MesWmBatchDO::getPollutionStatus, status)
                .set(MesWmBatchDO::getPollutionLocation, location)
                .set(MesWmBatchDO::getPollutionMarked, marked)
                .set(MesWmBatchDO::getPollutionSrcRecord, srcRecord));
    }

}
