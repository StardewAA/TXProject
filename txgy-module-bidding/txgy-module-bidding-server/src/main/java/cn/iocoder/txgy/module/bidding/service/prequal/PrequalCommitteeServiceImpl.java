package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalCommitteeDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.TenderProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.prequal.PrequalCommitteeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.prequal.PrequalMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.TenderProjectMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.prequal.PrequalCommitteeMemberCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.prequal.PrequalStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 资格审查委员会实现。
 *
 * <p>强制组建判定（条例第18条）：资格预审所属招标项目（tender_project.project_code）
 * 对应的项目主档资金来源（project.fund_source）落在国有资金字典值集
 * {01 财政性资金 / 02 国有企事业单位资金 / 03 国有控股主导} 内 ⇒ 国有资金占控股或主导 ⇒
 * 审查（review）前必须已组建资格审查委员会。
 * 组成校验（条例第37条）：成员 5 人以上单数，技术/经济专家不少于成员总数 2/3。
 */
@Service
@Slf4j
public class PrequalCommitteeServiceImpl implements PrequalCommitteeService {

    /** 国有资金占控股或主导地位的字典值（bidding_dict_data.fund_source，01/02/03）。 */
    private static final Set<String> STATE_OWNED_FUND_SOURCES = Set.of("01", "02", "03");

    /** 委员会最少成员数（条例第37条：5人以上单数）。 */
    private static final long MIN_MEMBER_COUNT = 5L;

    @Resource
    private PrequalCommitteeMapper prequalCommitteeMapper;
    @Resource
    private PrequalMapper prequalMapper;
    @Resource
    private TenderProjectMapper tenderProjectMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(PrequalCommitteeSaveReqVO reqVO) {
        PrequalDO prequal = requirePrequal(reqVO.getPrequalId());
        List<PrequalCommitteeSaveReqVO.MemberItem> members = reqVO.getMembers();
        // 组成校验（条例第37条）：成员数 5 人以上单数，专家≥2/3
        validateComposition(members);
        // 整单覆盖：先删后插
        prequalCommitteeMapper.delete(PrequalCommitteeDO::getPrequalId, prequal.getId());
        int sort = 1;
        for (PrequalCommitteeSaveReqVO.MemberItem item : members) {
            PrequalCommitteeDO member = new PrequalCommitteeDO();
            member.setPrequalId(prequal.getId());
            member.setMemberName(item.getMemberName());
            member.setMemberCategory(item.getMemberCategory());
            member.setMemberRole(item.getMemberRole());
            member.setMemberOrg(item.getMemberOrg());
            member.setMemberTitle(item.getMemberTitle());
            member.setSort(item.getSort() != null ? item.getSort() : sort);
            member.setRemark(item.getRemark());
            prequalCommitteeMapper.insert(member);
            sort++;
        }
        // 资格预审状态推进：DRAFT/PUBLISHED 阶段组建委员会均允许；进入审查（REVIEWING）前置校验在 review 入口
        log.info("资格审查委员会已组建：prequalId={}, 成员数={}", prequal.getId(), members.size());
    }

    @Override
    public List<PrequalCommitteeDO> listByPrequal(Long prequalId) {
        return prequalCommitteeMapper.selectListByPrequal(prequalId);
    }

    @Override
    public void validateStateOwnedCommittee(PrequalDO prequal) {
        if (prequal == null || !isStateOwned(prequal)) {
            return;
        }
        List<PrequalCommitteeDO> committee = prequalCommitteeMapper.selectListByPrequal(prequal.getId());
        if (committee.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_COMMITTEE_REQUIRED, prequal.getPrequalCode());
        }
    }

    /** 项目主档资金来源是否国有资金占控股或主导（条例第18条）。 */
    private boolean isStateOwned(PrequalDO prequal) {
        TenderProjectDO tenderProject = tenderProjectMapper.selectOne(
                TenderProjectDO::getTenderProjectCode, prequal.getTenderProjectCode());
        if (tenderProject == null || tenderProject.getProjectCode() == null) {
            return false;
        }
        ProjectDO project = projectMapper.selectOne(ProjectDO::getProjectCode, tenderProject.getProjectCode());
        if (project == null || project.getFundSource() == null) {
            return false;
        }
        return STATE_OWNED_FUND_SOURCES.contains(project.getFundSource());
    }

    /** 组成校验：成员数 ≥5 且单数，技术/经济专家 ≥2/3（条例第37条）。 */
    private void validateComposition(List<PrequalCommitteeSaveReqVO.MemberItem> members) {
        long total = members.size();
        if (total < MIN_MEMBER_COUNT || total % 2 == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_COMMITTEE_INVALID, total);
        }
        long expertCount = members.stream()
                .filter(m -> PrequalCommitteeMemberCategoryEnum.TECH_EXPERT.name().equals(m.getMemberCategory())
                        || PrequalCommitteeMemberCategoryEnum.ECON_EXPERT.name().equals(m.getMemberCategory()))
                .count();
        if (!PrequalCommitteeMemberCategoryEnum.expertQuorumMet(total, expertCount)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_COMMITTEE_INVALID, total);
        }
    }

    private PrequalDO requirePrequal(Long id) {
        PrequalDO prequal = prequalMapper.selectById(id);
        if (prequal == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_NOT_EXISTS, id);
        }
        if (PrequalStatusEnum.RESULTED.name().equals(prequal.getStatus())
                || PrequalStatusEnum.CLOSED.name().equals(prequal.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_STATUS_INVALID, prequal.getStatus());
        }
        return prequal;
    }
}
