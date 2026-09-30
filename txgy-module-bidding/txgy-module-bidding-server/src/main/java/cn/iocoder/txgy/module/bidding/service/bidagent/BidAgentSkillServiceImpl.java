package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentSkillVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentProfileSkillDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentProfileSkillMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentSkillMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_SKILL_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_SKILL_NOT_EXISTS;

@Service
@Slf4j
public class BidAgentSkillServiceImpl implements BidAgentSkillService {

    @Resource
    private BidAgentSkillMapper skillMapper;
    @Resource
    private BidAgentProfileSkillMapper profileSkillMapper;
    /** 自注入代理：listByProfile 的自动挂载经代理调用 setProfileSkills，避免 self-invocation 使 @Transactional 失效 */
    @Resource
    @Lazy
    private BidAgentSkillService self;

    @Override
    public PageResult<BidAgentSkillVO.RespVO> page(BidAgentSkillVO.PageReqVO reqVO) {
        // 平台预置技能（tenant_id=0）与租户自建技能合并展示：租户同 code 优先，避免"管理页看不到预置技能"
        java.util.Map<String, BidAgentSkillDO> merged = new java.util.LinkedHashMap<>();
        for (BidAgentSkillDO item : cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(0L,
                skillMapper::selectListOrdered)) {
            merged.put(item.getCode(), item);
        }
        Long tenantId = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId != 0L) {
            for (BidAgentSkillDO item : skillMapper.selectListOrdered()) {
                merged.put(item.getCode(), item);
            }
        }
        List<BidAgentSkillDO> all = new ArrayList<>(merged.values());
        all.removeIf(item -> StrUtil.isNotBlank(reqVO.getName()) && !StrUtil.containsIgnoreCase(item.getName(), reqVO.getName()));
        all.removeIf(item -> StrUtil.isNotBlank(reqVO.getCode()) && !StrUtil.containsIgnoreCase(item.getCode(), reqVO.getCode()));
        all.removeIf(item -> reqVO.getStatus() != null && !reqVO.getStatus().equals(item.getStatus()));
        all.sort((a, b) -> Integer.compare(a.getSort() == null ? 0 : a.getSort(), b.getSort() == null ? 0 : b.getSort()));
        // long 运算防溢出：pageNo 传极大值时 int 乘法会溢出为负数导致 subList 抛异常；pageSize 设上限防全表拉取
        long pageNo = reqVO.getPageNo() == null || reqVO.getPageNo() <= 0 ? 1 : reqVO.getPageNo();
        long pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() <= 0 ? 10 : Math.min(reqVO.getPageSize(), 200);
        int from = (int) Math.min((pageNo - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        List<BidAgentSkillVO.RespVO> list = new ArrayList<>();
        for (BidAgentSkillDO item : all.subList(from, to)) {
            list.add(BeanUtils.toBean(item, BidAgentSkillVO.RespVO.class));
        }
        return new PageResult<>(list, (long) all.size());
    }

    @Override
    public BidAgentSkillVO.RespVO get(Long id) {
        BidAgentSkillDO item = skillMapper.selectById(id);
        if (item == null) {
            throw exception(BID_AGENT_SKILL_NOT_EXISTS);
        }
        return BeanUtils.toBean(item, BidAgentSkillVO.RespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidAgentSkillVO.SaveReqVO reqVO) {
        BidAgentSkillDO exist = skillMapper.selectByCode(reqVO.getCode());
        if (exist != null && !exist.getId().equals(reqVO.getId())) {
            throw exception(BID_AGENT_SKILL_CODE_DUPLICATE, reqVO.getCode());
        }
        BidAgentSkillDO item = reqVO.getId() == null ? new BidAgentSkillDO() : skillMapper.selectById(reqVO.getId());
        if (item == null) {
            throw exception(BID_AGENT_SKILL_NOT_EXISTS);
        }
        item.setCode(reqVO.getCode());
        item.setName(reqVO.getName());
        item.setDescription(reqVO.getDescription());
        item.setPromptFragment(reqVO.getPromptFragment());
        item.setKnowledgeRoles(reqVO.getKnowledgeRoles());
        item.setToolWhitelist(reqVO.getToolWhitelist());
        item.setApplicableTaskTypes(reqVO.getApplicableTaskTypes());
        item.setAutoAttach(reqVO.getAutoAttach() == null ? 1 : reqVO.getAutoAttach());
        item.setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        item.setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        item.setRemark(reqVO.getRemark());
        if (reqVO.getId() == null) {
            // 重建同编码技能前，物理清理已逻辑删除的历史行，避免撞唯一键 (tenant_id, code, deleted)
            skillMapper.purgeDeletedByCode(reqVO.getCode());
            skillMapper.insert(item);
        } else {
            skillMapper.updateById(item);
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BidAgentSkillDO item = skillMapper.selectById(id);
        if (item == null) {
            throw exception(BID_AGENT_SKILL_NOT_EXISTS);
        }
        skillMapper.deleteById(id);
    }

    @Override
    public List<BidAgentSkillDO> listAutoAttachByTaskType(String taskType) {
        return skillMapper.selectEnabledAutoAttachList().stream()
                .filter(skill -> applicable(skill, taskType))
                .collect(Collectors.toList());
    }

    @Override
    public List<BidAgentSkillDO> listByProfile(Long profileId, String taskType, boolean autoAttachIfEmpty) {
        List<BidAgentProfileSkillDO> relations = profileSkillMapper.selectListByProfileId(profileId);
        if (relations.isEmpty() && autoAttachIfEmpty) {
            // "自动添加相关技能"：首次打开（或新建智能体）时按 taskType 自动挂载推荐技能。
            // 经自注入代理调用，保证 setProfileSkills 上的 @Transactional 生效（self-invocation 会绕过代理）
            List<BidAgentSkillDO> recommended = listAutoAttachByTaskType(taskType);
            if (!recommended.isEmpty()) {
                self.setProfileSkills(profileId, recommended.stream().map(BidAgentSkillDO::getId).collect(Collectors.toList()));
                relations = profileSkillMapper.selectListByProfileId(profileId);
            }
        }
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = relations.stream().map(BidAgentProfileSkillDO::getSkillId).collect(Collectors.toSet());
        List<BidAgentSkillDO> skills = skillMapper.selectListByIds(ids);
        // 一并读取平台预置技能（租户关系可能指向 tenant_id=0 的技能）
        if (skills.size() < ids.size()) {
            for (Long id : ids) {
                if (skills.stream().noneMatch(skill -> skill.getId().equals(id))) {
                    BidAgentSkillDO platformSkill = cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(0L,
                            () -> skillMapper.selectById(id));
                    if (platformSkill != null) {
                        skills.add(platformSkill);
                    }
                }
            }
        }
        skills.removeIf(skill -> skill.getStatus() != null && skill.getStatus() == 1);
        skills.sort((a, b) -> Integer.compare(
                a.getSort() == null ? 0 : a.getSort(), b.getSort() == null ? 0 : b.getSort()));
        return skills;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProfileSkills(Long profileId, List<Long> skillIds) {
        profileSkillMapper.deleteByProfileId(profileId);
        if (skillIds == null) {
            return;
        }
        for (Long skillId : skillIds) {
            if (skillId == null) {
                continue;
            }
            BidAgentProfileSkillDO relation = new BidAgentProfileSkillDO();
            relation.setProfileId(profileId);
            relation.setSkillId(skillId);
            profileSkillMapper.insert(relation);
        }
    }

    private boolean applicable(BidAgentSkillDO skill, String taskType) {
        if (StrUtil.isBlank(skill.getApplicableTaskTypes())) {
            return true;
        }
        if (StrUtil.isBlank(taskType)) {
            return false;
        }
        for (String item : skill.getApplicableTaskTypes().split(",")) {
            if (taskType.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }
}
