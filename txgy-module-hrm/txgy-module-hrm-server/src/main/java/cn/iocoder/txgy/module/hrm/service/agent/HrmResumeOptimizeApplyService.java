package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentOptimizeItemDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentOptimizeItemMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeFactGuard;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_CONTENT_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_DECISION_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_ORIGINAL_NOT_FOUND;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_OPTIMIZE_TASK_MISMATCH;

/**
 * 简历优化逐项确认（A03）。
 *
 * <p>关键约定：
 * <ul>
 *   <li>只读取源版本内容做替换，**绝不修改源版本**，确认结果固化成一个新版本；</li>
 *   <li>以「建议项」为最小确认单位，逐项记录原值、建议值、最终值与采纳状态；</li>
 *   <li>拒绝项不产生任何改动；接受项再次经过事实护栏；手工调整视为用户本人输入。</li>
 * </ul></p>
 */
@Service
@Validated
public class HrmResumeOptimizeApplyService {

    private static final String DECISION_ACCEPT = "ACCEPT";
    private static final String DECISION_REJECT = "REJECT";
    private static final String DECISION_ADJUST = "ADJUST";

    @Resource
    private HrmPersonalResumeVersionService versionService;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private HrmRecruitAgentOptimizeItemMapper optimizeItemMapper;

    /**
     * 应用逐项确认结果，生成个人简历新版本。
     *
     * <p>版本创建与采纳记录写入放在同一事务内：要么两者都成功，
     * 要么都不留痕，避免出现「版本生成了但采纳情况查不到」。</p>
     *
     * @param userId 登录用户编号
     * @param reqVO  确认请求
     * @return 新版本编号
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applyDecisions(Long userId, HrmPortalResumeOptimizeApplyReqVO reqVO) {
        // 1. 源版本归属校验
        HrmPersonalResumeVersionDO sourceVersion = versionService
                .getVersion(userId, reqVO.getSourceVersionId());

        // 2. 任务校验：必须是本人的优化任务，且确实基于该版本
        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(
                () -> taskMapper.selectByIdAndUserId(reqVO.getTaskId(), userId));
        if (task == null
                || !HrmRecruitAgentSceneEnum.RESUME_OPTIMIZE.getScene().equals(task.getScene())
                || !reqVO.getSourceVersionId().equals(task.getResumeVersionId())) {
            throw exception(AGENT_OPTIMIZE_TASK_MISMATCH);
        }

        // 3. 逐项应用；拒绝项不改动内容，但采纳状态照常留痕
        String content = sourceVersion.getContent();
        if (StrUtil.isBlank(content)) {
            throw exception(AGENT_OPTIMIZE_CONTENT_INVALID);
        }
        List<String> appliedSummary = new ArrayList<>();
        List<HrmRecruitAgentOptimizeItemDO> items = new ArrayList<>();
        int seq = 0;
        for (HrmPortalResumeOptimizeApplyReqVO.Decision decision : reqVO.getDecisions()) {
            String normalized = normalizeDecision(decision);
            items.add(new HrmRecruitAgentOptimizeItemDO()
                    .setTaskId(task.getId())
                    .setUserId(userId)
                    .setSeq(seq++)
                    .setModule(decision.getModule())
                    .setFieldPath(decision.getFieldPath())
                    .setOriginalValue(decision.getOriginalValue())
                    .setSuggestedValue(decision.getSuggestedValue())
                    .setFinalValue(decision.getFinalValue())
                    .setDecision(normalized));
            if (DECISION_REJECT.equals(normalized)) {
                continue;
            }
            if (!content.contains(decision.getOriginalValue())) {
                throw exception(AGENT_OPTIMIZE_ORIGINAL_NOT_FOUND);
            }
            if (DECISION_ACCEPT.equals(normalized)) {
                // 接受项再走一次事实护栏，避免上游校验被绕过
                if (HrmResumeFactGuard.introducesNewFact(
                        decision.getOriginalValue(), decision.getFinalValue())) {
                    throw exception(AGENT_OPTIMIZE_DECISION_INVALID);
                }
            }
            // 手工调整视为用户本人输入，不做事实护栏，但仍需保证能定位到原值
            content = content.replace(decision.getOriginalValue(), decision.getFinalValue());
            appliedSummary.add(decision.getFieldPath());
        }

        // 4. 替换后必须仍是合法 JSON，避免把简历结构改坏
        try {
            JSONObject parsed = JSONUtil.parseObj(content);
            if (parsed.isEmpty()) {
                throw exception(AGENT_OPTIMIZE_CONTENT_INVALID);
            }
        } catch (RuntimeException ex) {
            throw exception(AGENT_OPTIMIZE_CONTENT_INVALID);
        }

        // 5. 固化为新版本：父版本指向被优化的版本，来源职位沿用任务上的职位
        Long newVersionId = versionService.createVersion(userId, new HrmPortalResumeVersionCreateReqVO()
                .setSourceResumeId(sourceVersion.getSourceResumeId())
                .setParentVersionId(sourceVersion.getId())
                .setSourcePostId(task.getPostId())
                .setCreateType(HrmPersonalResumeVersionDO.CREATE_TYPE_AGENT_OPTIMIZED)
                .setChangeSummary(buildChangeSummary(reqVO.getChangeSummary(), appliedSummary))
                .setContent(content)
                .setIdempotencyKey(reqVO.getIdempotencyKey()));

        // 6. 采纳情况留痕：同一任务只写一次，重复提交（幂等命中同一版本）不产生重复记录
        if (!optimizeItemMapper.existsByTaskId(task.getId())) {
            for (HrmRecruitAgentOptimizeItemDO item : items) {
                item.setResultVersionId(newVersionId);
                optimizeItemMapper.insert(item);
            }
        }
        return newVersionId;
    }

    /** 校验采纳状态与最终值的一致性，返回归一化后的状态 */
    private String normalizeDecision(HrmPortalResumeOptimizeApplyReqVO.Decision decision) {
        String decisionValue = StrUtil.trimToEmpty(decision.getDecision()).toUpperCase();
        switch (decisionValue) {
            case DECISION_ACCEPT -> {
                // 接受：最终值必须等于建议值，否则记录状态会失真
                if (!StrUtil.equals(decision.getFinalValue(), decision.getSuggestedValue())) {
                    throw exception(AGENT_OPTIMIZE_DECISION_INVALID);
                }
            }
            case DECISION_REJECT -> {
                // 拒绝：最终值必须等于原值，表示保持原样
                if (!StrUtil.equals(decision.getFinalValue(), decision.getOriginalValue())) {
                    throw exception(AGENT_OPTIMIZE_DECISION_INVALID);
                }
            }
            case DECISION_ADJUST -> {
                // 手工调整：最终值不得与原值相同，否则等于没改
                if (StrUtil.equals(decision.getFinalValue(), decision.getOriginalValue())) {
                    throw exception(AGENT_OPTIMIZE_DECISION_INVALID);
                }
            }
            default -> throw exception(AGENT_OPTIMIZE_DECISION_INVALID);
        }
        return decisionValue;
    }

    private String buildChangeSummary(String userSummary, List<String> appliedFields) {
        if (StrUtil.isNotBlank(userSummary)) {
            return userSummary;
        }
        if (appliedFields.isEmpty()) {
            return "确认优化建议：全部拒绝，内容与源版本一致";
        }
        return StrUtil.maxLength("确认优化建议，改动字段：" + String.join("、", appliedFields), 500);
    }
}
