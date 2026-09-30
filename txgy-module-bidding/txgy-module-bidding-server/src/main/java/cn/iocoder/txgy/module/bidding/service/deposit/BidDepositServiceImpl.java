package cn.iocoder.txgy.module.bidding.service.deposit;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRefundReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.deposit.BidDepositDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.BidSectionDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.contract.ContractMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.deposit.BidDepositMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.BidSectionMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 投标保证金实现。
 *
 * <p>业务目标：登记投标保证金，登记时校验金额 ≤ 估算价 2%（条例第26条），
 * 超额即拒绝登记（非仅提示，避免事后追缴困难）；估算价缺失/非数值时容错跳过（外部采集标段可能缺价）。
 */
@Service
@Slf4j
public class BidDepositServiceImpl implements BidDepositService {

    /** 条例第26条：投标保证金不超过估算价的 2%。 */
    private static final BigDecimal DEPOSIT_RATE_LIMIT = new BigDecimal("0.02");
    /** 条例第58条：合同签订后 5 日内退还投标保证金。 */
    private static final long REFUND_PERIOD_DAYS_LIMIT = 5L;

    @Resource
    private BidDepositMapper bidDepositMapper;
    @Resource
    private BidSectionMapper bidSectionMapper;
    @Resource
    private ContractMapper contractMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidDepositSaveReqVO reqVO) {
        // 定位标段拿估算价（登记保证金必须以标段为粒度）
        BidSectionDO section = bidSectionMapper.selectOne(
                BidSectionDO::getBidSectionCode, reqVO.getBidSectionCode());
        if (section == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_DEPOSIT_SECTION_NOT_FOUND, reqVO.getBidSectionCode());
        }
        // 2% 限额校验（条例第26条）：金额 > 估算价 × 2% 即拒绝
        BigDecimal estimate = parsePrice(section.getContractReckonPrice());
        if (estimate != null && reqVO.getAmount() != null
                && reqVO.getAmount().compareTo(estimate.multiply(DEPOSIT_RATE_LIMIT)) > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_DEPOSIT_EXCEEDS_LIMIT,
                    reqVO.getAmount().toPlainString(), section.getContractReckonPrice());
        }
        // 幂等：同一标段同一主体只登记一次（重复登记返回既有记录）
        BidDepositDO exist = bidDepositMapper.selectBySectionAndSubject(reqVO.getBidSectionCode(), reqVO.getSubjectId());
        if (exist != null) {
            return exist.getId();
        }
        BidDepositDO deposit = new BidDepositDO();
        deposit.setBidSectionCode(reqVO.getBidSectionCode());
        deposit.setTenderProjectCode(section.getTenderProjectCode());
        deposit.setSubjectId(reqVO.getSubjectId());
        deposit.setAmount(reqVO.getAmount());
        deposit.setCurrencyCode(reqVO.getCurrencyCode());
        deposit.setPayType(reqVO.getPayType());
        deposit.setStatus(reqVO.getStatus());
        deposit.setFileId(reqVO.getFileId());
        deposit.setDepositTime(LocalDateTime.now());
        deposit.setRemark(reqVO.getRemark());
        bidDepositMapper.insert(deposit);
        return deposit.getId();
    }

    @Override
    public BidDepositDO get(Long id) {
        BidDepositDO deposit = bidDepositMapper.selectById(id);
        if (deposit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_DEPOSIT_NOT_EXISTS, id);
        }
        fillRefundOverdue(deposit);
        return deposit;
    }

    @Override
    public PageResult<BidDepositDO> page(BidDepositPageReqVO reqVO) {
        PageResult<BidDepositDO> page = bidDepositMapper.selectPage(reqVO);
        page.getList().forEach(this::fillRefundOverdue);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long refund(BidDepositRefundReqVO reqVO) {
        BidDepositDO deposit = bidDepositMapper.selectById(reqVO.getDepositId());
        if (deposit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_DEPOSIT_NOT_EXISTS, reqVO.getDepositId());
        }
        if (deposit.getRefundTime() != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_DEPOSIT_ALREADY_REFUNDED, reqVO.getDepositId());
        }
        // 退还 5 日时限告警（条例第58条）：合同签订后超 5 日未退还 → warn（不拦截，超期退还仍允许）
        ContractDO contract = contractMapper.selectLatestByProject(deposit.getTenderProjectCode());
        if (contract != null && contract.getSignTime() != null) {
            long overdueDays = ChronoUnit.DAYS.between(contract.getSignTime(), LocalDateTime.now());
            if (overdueDays > REFUND_PERIOD_DAYS_LIMIT) {
                log.warn("[deposit][refund] 投标保证金超期退还（条例第58条：合同签订后5日内退还）：depositId={}，合同签订={}，已超{}日",
                        deposit.getId(), contract.getSignTime(), overdueDays);
            }
        }
        deposit.setRefundTime(LocalDateTime.now());
        deposit.setRefundStatus(reqVO.getRefundStatus() != null ? reqVO.getRefundStatus() : "REFUNDED");
        bidDepositMapper.updateById(deposit);
        return deposit.getId();
    }

    /** 计算「是否超期未退还」（条例第58条）：未退还且合同签订后超 5 日 → true。 */
    private void fillRefundOverdue(BidDepositDO deposit) {
        if (deposit.getRefundTime() != null) {
            deposit.setRefundOverdue(false);
            return;
        }
        ContractDO contract = contractMapper.selectLatestByProject(deposit.getTenderProjectCode());
        deposit.setRefundOverdue(contract != null && contract.getSignTime() != null
                && ChronoUnit.DAYS.between(contract.getSignTime(), LocalDateTime.now()) > REFUND_PERIOD_DAYS_LIMIT);
    }

    /** 估算价字符串 → BigDecimal；缺失/非数值返回 null（调用方容错跳过 2% 校验）。 */
    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            log.warn("[deposit] 估算价非数值（跳过2%校验）：{}", text);
            return null;
        }
    }
}
