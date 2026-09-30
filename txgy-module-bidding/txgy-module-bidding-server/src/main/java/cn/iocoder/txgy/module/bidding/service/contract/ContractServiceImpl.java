package cn.iocoder.txgy.module.bidding.service.contract;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.contract.ContractMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractCompareResultEnum;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractRetentionWarnEnum;
import cn.iocoder.txgy.module.bidding.enums.contract.ContractStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 工程合同实现。
 *
 * <p>业务目标：登记中标后订立的书面合同，登记时校验履约保证金 ≤ 合同价 10%（条例第57条）
 * 与签约 ≤ 中标通知书发出 30 日（招标投标法第46条）；缺失时间/金额字段时容错跳过对应校验。
 * <p>归档锁（2026-09-28）：ARCHIVED 后只读锁定，禁止 save 修改（电子办法技术规范 存档≥15 年，
 * 说明书 §11.3 第 5 条）；archive 落归档时间 + 保存到期（归档 + 15 年自动计算），
 * retentionWarn 按到期自动计算（临期 ≤90 天 / 已到期）。
 */
@Service
@Slf4j
public class ContractServiceImpl implements ContractService {

    /** 条例第57条：履约保证金不超过合同价的 10%。 */
    private static final BigDecimal PERFORMANCE_BOND_RATE_LIMIT = new BigDecimal("0.10");
    /** 招标投标法第46条：中标通知书发出之日起 30 日内签约。 */
    private static final long SIGN_PERIOD_DAYS_LIMIT = 30L;
    /** 电子办法技术规范：存档备查 ≥15 年。 */
    private static final long RETENTION_YEARS = 15L;
    /** 保存期限临期提醒阈值：距到期 ≤90 天 → EXPIRING_SOON。 */
    private static final long EXPIRING_SOON_DAYS = 90L;

    /** 中标结果公示字段快照解析（key 照录规范英文名，见 NoticePayloadUtils）。 */
    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

    @Resource
    private ContractMapper contractMapper;

    @Resource
    private NoticeMapper noticeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ContractSaveReqVO reqVO) {
        // 履约保证金 ≤ 合同价 10%（条例第57条）；缺字段容错跳过
        if (reqVO.getPerformanceBond() != null && reqVO.getContractPrice() != null
                && reqVO.getPerformanceBond().compareTo(reqVO.getContractPrice().multiply(PERFORMANCE_BOND_RATE_LIMIT)) > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_PERFORMANCE_BOND_EXCEEDS_LIMIT,
                    reqVO.getPerformanceBond().toPlainString(), reqVO.getContractPrice().toPlainString());
        }
        // 签约 ≤ 中标通知书发出 30 日（招标投标法第46条）；缺时间字段容错跳过
        if (reqVO.getWinNoticeTime() != null && reqVO.getSignTime() != null
                && ChronoUnit.DAYS.between(reqVO.getWinNoticeTime(), reqVO.getSignTime()) > SIGN_PERIOD_DAYS_LIMIT) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_SIGN_PERIOD_EXCEEDED);
        }
        ContractDO contract = new ContractDO();
        contract.setTenderProjectCode(reqVO.getTenderProjectCode());
        contract.setBidSectionCode(reqVO.getBidSectionCode());
        contract.setContractCode(reqVO.getContractCode());
        contract.setContractName(reqVO.getContractName());
        contract.setContractPrice(reqVO.getContractPrice());
        contract.setPerformanceBond(reqVO.getPerformanceBond());
        contract.setWinNoticeTime(reqVO.getWinNoticeTime());
        contract.setSignTime(reqVO.getSignTime());
        contract.setFileId(reqVO.getFileId());
        contract.setStatus(reqVO.getStatus() != null ? reqVO.getStatus() : ContractStatusEnum.SIGNED.name());
        contract.setRemark(reqVO.getRemark());
        // 归档锁：新登记不允许直接写 ARCHIVED（归档走 archive 接口落归档时间/保存到期）
        if (ContractStatusEnum.ARCHIVED.name().equals(contract.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_ARCHIVE_STATUS_INVALID, contract.getStatus());
        }
        // 合同一致性比对（说明书 §11.3 第 2 条）：合同价 vs 中标结果公示金额，不一致标红提示（不拦截）
        contract.setCompareResult(computeCompareResult(contract.getTenderProjectCode(), contract.getBidSectionCode(),
                contract.getContractPrice()));
        contractMapper.insert(contract);
        return contract.getId();
    }

    @Override
    public ContractDO get(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_NOT_EXISTS);
        }
        refreshRetentionWarn(contract);
        return contract;
    }

    @Override
    public PageResult<ContractDO> page(ContractPageReqVO reqVO) {
        PageResult<ContractDO> page = contractMapper.selectPage(reqVO);
        // 惰性刷新保存期限提醒标记（仅归档行有 retention_until，逐行自动计算）
        if (page != null && page.getList() != null) {
            page.getList().forEach(this::refreshRetentionWarn);
        }
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_NOT_EXISTS);
        }
        ContractStatusEnum status = ContractStatusEnum.of(contract.getStatus());
        if (status == ContractStatusEnum.ARCHIVED) {
            // 幂等：已归档直接返回（保存期限已落库）
            return;
        }
        if (status != ContractStatusEnum.SIGNED) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CONTRACT_ARCHIVE_STATUS_INVALID, contract.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        ContractDO update = new ContractDO();
        update.setId(id);
        update.setStatus(ContractStatusEnum.ARCHIVED.name());
        update.setArchiveTime(now);
        // 保存期限自动计算：归档 + 15 年（电子办法技术规范 存档≥15 年）
        update.setRetentionUntil(now.plusYears(RETENTION_YEARS));
        update.setRetentionWarn(ContractRetentionWarnEnum.NORMAL.name());
        contractMapper.updateById(update);
        log.info("[archive] 合同归档完成 id={} archiveTime={} retentionUntil={}（存档≥{}年）",
                id, now, update.getRetentionUntil(), RETENTION_YEARS);
    }

    /**
     * 合同一致性比对（说明书 §11.3 第 2 条/§11.5）：在已发布的中标结果公示（WIN_RESULT_PUBLICITY）中
     * 按 TENDER_PROJECT_CODE（+标段可选）定位本项目，取公示金额 BID_AMOUNT 与合同价比对：
     * 一致→MATCH；不一致→MISMATCH（前端标红，招标人确认）；无公示/缺金额/金额非数字→NO_BASELINE。
     * 标红提示非硬拦截：比对失败一律容错为 NO_BASELINE，不影响登记。
     */
    private String computeCompareResult(String tenderProjectCode, String bidSectionCode, BigDecimal contractPrice) {
        if (tenderProjectCode == null || contractPrice == null) {
            return ContractCompareResultEnum.NO_BASELINE.name();
        }
        try {
            java.util.List<NoticeDO> winNotices = noticeMapper.selectList(
                    new LambdaQueryWrapper<NoticeDO>()
                            .eq(NoticeDO::getNoticeType, NoticeTypeEnum.WIN_RESULT_PUBLICITY.name())
                            .eq(NoticeDO::getStatus, NoticeStatusEnum.PUBLISHED.name())
                            .orderByDesc(NoticeDO::getPublishTime));
            for (NoticeDO notice : winNotices) {
                java.util.Map<String, Object> payload = parsePayload(notice.getFieldsJson());
                if (payload == null) {
                    continue;
                }
                if (!tenderProjectCode.equals(payload.get("TENDER_PROJECT_CODE"))) {
                    continue;
                }
                // 标段口径：公示标段与合同标段不一致则跳过（空标段=项目级合同不强制）
                Object section = payload.get("BID_SECTION_CODE");
                if (bidSectionCode != null && section != null && !bidSectionCode.equals(section.toString())) {
                    continue;
                }
                BigDecimal winPrice = toBigDecimal(payload.get("BID_AMOUNT"));
                if (winPrice == null) {
                    return ContractCompareResultEnum.NO_BASELINE.name();
                }
                return contractPrice.compareTo(winPrice) == 0
                        ? ContractCompareResultEnum.MATCH.name()
                        : ContractCompareResultEnum.MISMATCH.name();
            }
        } catch (Exception e) {
            log.warn("[compare] 合同一致性比对异常，容错为 NO_BASELINE：{}", e.getMessage());
        }
        return ContractCompareResultEnum.NO_BASELINE.name();
    }

    /** 解析公告权威字段快照（JSON，key=规范英文名）；解析失败返回 null（容错跳过）。 */
    private java.util.Map<String, Object> parsePayload(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return null;
        }
        try {
            return PAYLOAD_MAPPER.readValue(fieldsJson, new TypeReference<java.util.LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 中标金额转 BigDecimal；缺值/非数字返回 null（容错为无基准）。 */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void refreshRetentionWarn(ContractDO contract) {
        // 仅归档行参与保存期限提醒计算；缺到期时间容错跳过
        if (contract == null || !ContractStatusEnum.ARCHIVED.name().equals(contract.getStatus())
                || contract.getRetentionUntil() == null) {
            return;
        }
        String warn = ContractRetentionWarnEnum.NORMAL.name();
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), contract.getRetentionUntil());
        if (daysLeft < 0) {
            warn = ContractRetentionWarnEnum.EXPIRED.name();
        } else if (daysLeft <= EXPIRING_SOON_DAYS) {
            warn = ContractRetentionWarnEnum.EXPIRING_SOON.name();
        }
        if (!warn.equals(contract.getRetentionWarn())) {
            ContractDO update = new ContractDO();
            update.setId(contract.getId());
            update.setRetentionWarn(warn);
            contractMapper.updateById(update);
            contract.setRetentionWarn(warn);
        }
    }
}
