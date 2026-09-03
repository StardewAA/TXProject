package cn.iocoder.txgy.module.mes.service.set.pollutionledger;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerStatusReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerLogDO;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger.MesPollutionLedgerLogMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger.MesPollutionLedgerMapper;
import cn.iocoder.txgy.module.mes.service.pollution.MesPollutionControlService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_LEDGER_NOT_EXISTS;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_LEDGER_STATUS_INVALID;

/**
 * MES 安全环保检测-污染/危废暂存台账 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesPollutionLedgerServiceImpl implements MesPollutionLedgerService {

    /**
     * 台账可流转目标状态（暂存 STORED 为自动登记态，不可人工流转回）
     */
    private static final Set<String> FLOW_TARGETS = new HashSet<>(Arrays.asList(
            MesPollutionLedgerDO.STATUS_PROCESSING,
            MesPollutionLedgerDO.STATUS_REUSED,
            MesPollutionLedgerDO.STATUS_DISCHARGED,
            MesPollutionLedgerDO.STATUS_DISPOSED));

    /**
     * 已闭环状态：人工处置终态(已回用/已排放/已处置) + 无污染自动解除(CLEARED)。闭环行禁止再流转(状态机不可逆)。
     */
    private static final Set<String> CLOSED_STATUSES = new HashSet<>(Arrays.asList(
            MesPollutionLedgerDO.STATUS_REUSED,
            MesPollutionLedgerDO.STATUS_DISCHARGED,
            MesPollutionLedgerDO.STATUS_DISPOSED,
            MesPollutionLedgerDO.STATUS_CLEARED));

    @Resource
    private MesPollutionLedgerMapper ledgerMapper;

    @Resource
    private MesPollutionLedgerLogMapper ledgerLogMapper;

    @Resource
    private MesPollutionControlService pollutionControlService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLedgerStatus(MesPollutionLedgerStatusReqVO reqVO) {
        MesPollutionLedgerDO ledger = validateLedgerExists(reqVO.getId());
        String status = reqVO.getStatus();
        if (!FLOW_TARGETS.contains(status)) {
            throw exception(SET_POLLUTION_LEDGER_STATUS_INVALID);
        }
        // 状态机：开放行(暂存/处置中)才能流转；已闭环(处置终态/已解除)禁止 reopen
        if (CLOSED_STATUSES.contains(ledger.getStatus())) {
            throw exception(SET_POLLUTION_LEDGER_STATUS_INVALID);
        }
        // 1. 流转
        String fromStatus = ledger.getStatus();
        ledger.setStatus(status);
        ledger.setStatusBy(getCurrentUserName());
        ledger.setStatusTime(LocalDateTime.now());
        if (StrUtil.isNotBlank(reqVO.getRemark())) {
            ledger.setRemark(reqVO.getRemark());
        }
        ledgerMapper.updateById(ledger);
        // 1.1 流转历史留痕（只增不改，完整处置链在 ledger_log）
        ledgerLogMapper.insert(MesPollutionLedgerLogDO.builder()
                .ledgerId(ledger.getId())
                .sourceRecordNo(ledger.getSourceRecordNo())
                .fromStatus(fromStatus)
                .toStatus(status)
                .operator(ledger.getStatusBy())
                .opTime(ledger.getStatusTime())
                .remark(reqVO.getRemark())
                .build());
        // 2. 终态(处置完成) → 该行闭环，重投影批次戳：同批若仍有其它未闭环台账行则保持拦截，全部闭环才清戳
        boolean terminal = MesPollutionLedgerDO.STATUS_REUSED.equals(status)
                || MesPollutionLedgerDO.STATUS_DISCHARGED.equals(status)
                || MesPollutionLedgerDO.STATUS_DISPOSED.equals(status);
        if (terminal) {
            pollutionControlService.refreshBatchStamp(ledger.getBatchNo());
        }
    }

    @Override
    public List<MesPollutionLedgerLogDO> getLedgerLogList(Long ledgerId) {
        return ledgerLogMapper.selectByLedgerId(ledgerId);
    }

    @Override
    public MesPollutionLedgerDO getLedger(Long id) {
        return ledgerMapper.selectById(id);
    }

    @Override
    public PageResult<MesPollutionLedgerDO> getLedgerPage(MesPollutionLedgerPageReqVO pageReqVO) {
        return ledgerMapper.selectPage(pageReqVO);
    }

    // ==================== 私有方法 ====================

    private MesPollutionLedgerDO validateLedgerExists(Long id) {
        MesPollutionLedgerDO ledger = ledgerMapper.selectById(id);
        if (ledger == null) {
            throw exception(SET_POLLUTION_LEDGER_NOT_EXISTS);
        }
        return ledger;
    }

    /**
     * 当前登录人昵称，取不到则回退为登录用户 id
     */
    private String getCurrentUserName() {
        String nickname = getLoginUserNickname();
        if (nickname == null || nickname.isEmpty()) {
            return String.valueOf(getLoginUserId());
        }
        return nickname;
    }

}
