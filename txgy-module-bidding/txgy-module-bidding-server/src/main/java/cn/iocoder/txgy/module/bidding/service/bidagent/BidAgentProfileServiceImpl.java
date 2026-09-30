package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2、§4.5
// [业务目标] 后台可改提示词/模型/温度/默认知识库角色；editable=0 的锁机制保留（仍禁止覆盖）
// [为什么] 五类智能体是**平台默认定义**（bidding_bid_agent 里 tenant_id=0 的 5 行），单个租户可以"覆盖"其中
//          后台可编辑的那些（2026-09-28 起五类均可编辑）；若读取时不合并平台默认，新租户看到的是空列表
//          （2026-09-16 实测踩到）。
//          因此：读 = 本租户优先 + 平台默认兜底；写 = 本租户行存在则更新，否则按平台默认复制一份到本租户再改。

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_PROFILE_NOT_EXISTS;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_PROFILE_READONLY;

@Service
public class BidAgentProfileServiceImpl implements BidAgentProfileService {

    /** 平台默认定义的租户编号：五类智能体的基线配置都挂在该租户下，作为各租户的兜底 */
    private static final long PLATFORM_TENANT_ID = 0L;

    @Resource
    private BidAgentProfileMapper profileMapper;

    @Override
    public List<BidAgentProfileVO.RespVO> list() {
        // 先放平台默认（tenant_id=0），再用本租户的同 task_type 覆盖，保证新租户也能看到完整五类智能体
        Map<String, BidAgentProfileDO> merged = new LinkedHashMap<>();
        for (BidAgentProfileDO profile : TenantUtils.execute(PLATFORM_TENANT_ID, profileMapper::selectListOrdered)) {
            merged.put(profile.getTaskType(), profile);
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
            for (BidAgentProfileDO profile : profileMapper.selectListOrdered()) {
                merged.put(profile.getTaskType(), profile);
            }
        }
        List<BidAgentProfileDO> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing(BidAgentProfileDO::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(BidAgentProfileDO::getTaskType, Comparator.nullsLast(String::compareTo)));
        List<BidAgentProfileVO.RespVO> result = new ArrayList<>(list.size());
        for (BidAgentProfileDO profile : list) {
            result.add(toRespVO(profile));
        }
        return result;
    }

    @Override
    public BidAgentProfileVO.RespVO get(Long id) {
        BidAgentProfileDO profile = profileMapper.selectById(id);
        if (profile == null) {
            throw exception(BID_AGENT_PROFILE_NOT_EXISTS);
        }
        return toRespVO(profile);
    }

    @Override
    public BidAgentProfileVO.RespVO getByTaskType(String taskType) {
        BidAgentProfileDO profile = StrUtil.isBlank(taskType) ? null : profileMapper.selectByTaskType(taskType);
        if (profile == null && StrUtil.isNotBlank(taskType)) {
            profile = TenantUtils.execute(PLATFORM_TENANT_ID, () -> profileMapper.selectByTaskType(taskType));
        }
        return profile == null ? null : toRespVO(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidAgentProfileVO.SaveReqVO reqVO) {
        // 1. 先找本租户可见的行（按 id，再按 taskType）
        BidAgentProfileDO profile = reqVO.getId() != null ? profileMapper.selectById(reqVO.getId()) : null;
        if (profile == null && StrUtil.isNotBlank(reqVO.getTaskType())) {
            profile = profileMapper.selectByTaskType(reqVO.getTaskType());
        }
        // 2. 本租户没有：基于平台默认复制一份到本租户（保留平台默认的"是否可编辑"语义），或按自定义新增
        if (profile == null) {
            BidAgentProfileDO platform = StrUtil.isBlank(reqVO.getTaskType()) ? null
                    : TenantUtils.execute(PLATFORM_TENANT_ID, () -> profileMapper.selectByTaskType(reqVO.getTaskType()));
            if (platform == null && reqVO.getId() != null) {
                // 传了 id 却既不在本租户、又对不上平台默认的 taskType → 真的是脏数据
                throw exception(BID_AGENT_PROFILE_NOT_EXISTS);
            }
            if (platform != null && platform.getEditable() != null && platform.getEditable() == 0) {
                throw exception(BID_AGENT_PROFILE_READONLY, platform.getName());
            }
            profile = new BidAgentProfileDO();
            profile.setTaskType(platform == null ? reqVO.getTaskType() : platform.getTaskType());
            profile.setEditable(platform == null ? 1 : platform.getEditable());
            applyFields(profile, reqVO);
            profileMapper.insert(profile);
            return profile.getId();
        }
        // 3. 本租户已有：只读项禁止覆盖
        if (profile.getEditable() != null && profile.getEditable() == 0) {
            throw exception(BID_AGENT_PROFILE_READONLY, profile.getName());
        }
        applyFields(profile, reqVO);
        profileMapper.updateById(profile);
        return profile.getId();
    }

    private void applyFields(BidAgentProfileDO profile, BidAgentProfileVO.SaveReqVO reqVO) {
        if (StrUtil.isNotBlank(reqVO.getName())) {
            profile.setName(reqVO.getName());
        }
        profile.setDescription(reqVO.getDescription());
        profile.setSystemPrompt(reqVO.getSystemPrompt());
        profile.setModel(reqVO.getModel());
        profile.setTemperature(reqVO.getTemperature());
        profile.setKnowledgeRoles(reqVO.getKnowledgeRoles());
        profile.setMaxTokens(reqVO.getMaxTokens());
        profile.setToolWhitelist(reqVO.getToolWhitelist());
        if (reqVO.getSkillAutoAttach() != null) {
            profile.setSkillAutoAttach(reqVO.getSkillAutoAttach());
        }
        profile.setSort(reqVO.getSort());
        profile.setStatus(reqVO.getStatus());
        profile.setRemark(reqVO.getRemark());
    }

    private BidAgentProfileVO.RespVO toRespVO(BidAgentProfileDO profile) {
        BidAgentProfileVO.RespVO vo = BeanUtils.toBean(profile, BidAgentProfileVO.RespVO.class);
        vo.setKnowledgeRoleList(StrUtil.isBlank(profile.getKnowledgeRoles())
                ? List.of() : List.of(profile.getKnowledgeRoles().split(",")));
        return vo;
    }
}
