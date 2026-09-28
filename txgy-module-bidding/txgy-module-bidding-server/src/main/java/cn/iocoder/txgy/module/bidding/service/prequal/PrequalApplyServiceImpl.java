package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalReviewReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalApplyDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.prequal.PrequalApplyMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.prequal.PrequalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowTransitionEventEnum;
import cn.iocoder.txgy.module.bidding.enums.prequal.PrequalApplyStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.prequal.PrequalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTransitionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资格预审申请实现。
 *
 * <p>关键控制点（条例第19条）：全部申请审查完毕后统计通过数，通过者<3家时
 * 自动触发招标项目终止（TENDER_PROJECT → TERMINATED）并重新发起；终止流转失败不阻断审查结果。
 */
@Service
@Slf4j
public class PrequalApplyServiceImpl implements PrequalApplyService {

    @Resource
    private PrequalApplyMapper prequalApplyMapper;
    @Resource
    private PrequalMapper prequalMapper;
    @Resource
    private FlowTransitionService flowTransitionService;
    @Resource
    private PrequalCommitteeService prequalCommitteeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(PrequalApplyReqVO reqVO) {
        PrequalDO prequal = requirePrequal(reqVO.getPrequalId());
        // 状态校验：仅 PUBLISHED/APPLYING 可接受申请
        String status = prequal.getStatus();
        if (!PrequalStatusEnum.PUBLISHED.name().equals(status)
                && !PrequalStatusEnum.APPLYING.name().equals(status)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_STATUS_INVALID, status);
        }
        // 截止校验：申请截止前可提交
        if (prequal.getApplyDeadline() != null && LocalDateTime.now().isAfter(prequal.getApplyDeadline())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_APPLY_DEADLINE_EXCEEDED);
        }
        // 幂等：同一主体对同一预审只提交一次
        Long subjectId = reqVO.getSubjectId();
        if (subjectId != null) {
            PrequalApplyDO exist = prequalApplyMapper.selectByPrequalAndSubject(reqVO.getPrequalId(), subjectId);
            if (exist != null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_APPLY_ALREADY_EXISTS);
            }
        }
        PrequalApplyDO apply = new PrequalApplyDO();
        apply.setPrequalId(reqVO.getPrequalId());
        apply.setPrequalCode(prequal.getPrequalCode());
        apply.setTenderProjectCode(prequal.getTenderProjectCode());
        apply.setBidSectionCode(prequal.getBidSectionCode());
        apply.setSubjectId(subjectId);
        apply.setUserId(SecurityFrameworkUtils.getLoginUserId());
        apply.setFileId(reqVO.getFileId());
        apply.setApplyTime(LocalDateTime.now());
        apply.setStatus(PrequalApplyStatusEnum.SUBMITTED.name());
        apply.setRemark(reqVO.getRemark());
        prequalApplyMapper.insert(apply);
        // 首次申请时推进预审状态 PUBLISHED→APPLYING
        if (PrequalStatusEnum.PUBLISHED.name().equals(status)) {
            prequal.setStatus(PrequalStatusEnum.APPLYING.name());
            prequalMapper.updateById(prequal);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(PrequalReviewReqVO reqVO) {
        PrequalApplyDO apply = prequalApplyMapper.selectById(reqVO.getApplyId());
        if (apply == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_APPLY_NOT_EXISTS, reqVO.getApplyId());
        }
        if (!PrequalApplyStatusEnum.SUBMITTED.name().equals(apply.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_APPLY_STATUS_INVALID, apply.getStatus());
        }
        // 条例第18条强制校验：国有资金项目未组建资格审查委员会禁止审查（在审查前置状态 REVIEWING 前拦截）
        PrequalDO prequalBefore = requirePrequal(apply.getPrequalId());
        prequalCommitteeService.validateStateOwnedCommittee(prequalBefore);
        apply.setStatus(reqVO.getPassed() ? PrequalApplyStatusEnum.PASSED.name() : PrequalApplyStatusEnum.FAILED.name());
        apply.setReviewResult(apply.getStatus());
        apply.setReviewReason(reqVO.getReviewReason());
        apply.setReviewTime(LocalDateTime.now());
        prequalApplyMapper.updateById(apply);

        // 预审进入审查中（APPLYING→REVIEWING）；全部申请审查完毕后 → RESULTED，并统计通过数
        PrequalDO prequal = requirePrequal(apply.getPrequalId());
        if (!PrequalStatusEnum.RESULTED.name().equals(prequal.getStatus())) {
            prequal.setStatus(PrequalStatusEnum.REVIEWING.name());
            prequalMapper.updateById(prequal);
        }
        // 条例第19条：通过者<3家应重新招标（告警，不阻断审查，由招标人决策）
        long pending = prequalApplyMapper.selectListByPrequal(apply.getPrequalId()).stream()
                .filter(a -> PrequalApplyStatusEnum.SUBMITTED.name().equals(a.getStatus())).count();
        if (pending == 0) {
            prequal.setStatus(PrequalStatusEnum.RESULTED.name());
            prequalMapper.updateById(prequal);
            long passed = prequalApplyMapper.countPassed(apply.getPrequalId());
            if (passed < 3) {
                terminateForRerun(prequal, passed);
            }
        }
    }

    /**
     * 条例第19条：通过资格预审的申请人 <3 家 → 自动触发重新招标（招标项目 → TERMINATED）。
     *
     * <p>走「事件驱动状态机」入口（FlowTransitionService.transition），由状态机统一校验 + 留痕 +
     * 联动（终止后 checkAndCompleteProject 自动完结项目）。TERMINATE 为非时间轴事件，不受
     * manual-advance-enabled 开关约束；流转失败（如招标项目已终态）不阻断审查结果，仅告警。
     */
    private void terminateForRerun(PrequalDO prequal, long passed) {
        if (prequal.getTenderProjectCode() == null) {
            log.warn("[资格预审] 预审 {} 通过者 {} 家 < 3 家，但未关联招标项目，无法自动终止", prequal.getPrequalCode(), passed);
            return;
        }
        try {
            FlowTransitionReqVO req = new FlowTransitionReqVO();
            req.setBizType(FlowBizTypeEnum.TENDER_PROJECT.name());
            req.setBizCode(prequal.getTenderProjectCode());
            req.setEvent(FlowTransitionEventEnum.TERMINATE.name());
            req.setRemark(String.format("资格预审 %s 通过者 %d 家 < 3 家，自动触发重新招标（条例第19条）",
                    prequal.getPrequalCode(), passed));
            flowTransitionService.transition(req);
        } catch (Exception e) {
            log.warn("[资格预审] 预审 {} 通过者 {} 家 < 3 家，自动终止招标项目 {} 失败（不阻断审查结果）：{}",
                    prequal.getPrequalCode(), passed, prequal.getTenderProjectCode(), e.getMessage());
        }
    }

    @Override
    public List<PrequalApplyDO> listByPrequal(Long prequalId) {
        return prequalApplyMapper.selectListByPrequal(prequalId);
    }

    @Override
    public PageResult<PrequalApplyDO> page(PrequalApplyPageReqVO reqVO) {
        return prequalApplyMapper.selectPage(reqVO);
    }

    private PrequalDO requirePrequal(Long id) {
        PrequalDO prequal = prequalMapper.selectById(id);
        if (prequal == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_NOT_EXISTS, id);
        }
        return prequal;
    }
}
